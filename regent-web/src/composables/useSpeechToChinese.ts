import { onScopeDispose, ref } from 'vue'

import { ApiError, postForm } from '@/api/http'

export type SpeechLanguage = 'zh-CN' | 'en-US' | 'ja-JP' | 'ko-KR'

type TranscriptionResponse = {
  text: string
  language: string
}

type TranslatorInstance = {
  translate(text: string): Promise<string>
  destroy?(): void
}

type TranslatorApi = {
  create(options: { sourceLanguage: string; targetLanguage: string }): Promise<TranslatorInstance>
}

type AudioWindow = Window & { Translator?: TranslatorApi }

const SEGMENT_MILLIS = 2_500
const MIME_CANDIDATES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/mp4',
  'audio/ogg;codecs=opus',
]

function audioWindow(): AudioWindow | null {
  return typeof window === 'undefined' ? null : (window as AudioWindow)
}

function preferredMimeType(): string {
  if (typeof MediaRecorder === 'undefined') return ''
  return MIME_CANDIDATES.find((type) => MediaRecorder.isTypeSupported(type)) ?? ''
}

function extensionOf(mimeType: string): string {
  if (mimeType.includes('mp4')) return 'm4a'
  if (mimeType.includes('ogg')) return 'ogg'
  return 'webm'
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError') return '麦克风权限被拒绝，请在浏览器设置中允许'
    if (error.name === 'NotFoundError') return '没有检测到可用麦克风'
  }
  return error instanceof Error ? error.message : '语音转写失败，请重试'
}

/**
 * 浏览器录音 + 本机 Whisper 准实时转写。
 *
 * <p>每 2.5 秒结束一个自包含音频片段并顺序提交，前一段转写完成后立刻追加到指令框；
 * 用户停止录音时再提交最后一段。它不是逐字流式，但不需要 WebSocket 或云端识别服务，
 * 在本机 Whisper 上延迟通常只有几秒。
 */
export function useSpeechToChinese() {
  const supported =
    typeof navigator !== 'undefined' &&
    Boolean(navigator.mediaDevices?.getUserMedia) &&
    typeof MediaRecorder !== 'undefined'
  const secureContext = typeof window !== 'undefined' && window.isSecureContext

  const listening = ref(false)
  const translating = ref(false)
  const interim = ref('')
  const error = ref<string | null>(null)
  const translationFallback = ref(false)

  let mediaStream: MediaStream | null = null
  let segmentRecorder: MediaRecorder | null = null
  let segmentTimer: number | undefined
  let recordingActive = false
  let discardRecording = false
  let finishCallback: ((text: string) => void) | null = null
  let language: SpeechLanguage = 'zh-CN'
  let session = 0
  let pendingSegments = 0
  let transcriptionChain = Promise.resolve()
  const translators = new Map<string, Promise<TranslatorInstance>>()

  const releaseStream = () => {
    mediaStream?.getTracks().forEach((track) => track.stop())
    mediaStream = null
  }

  const clearSegmentTimer = () => {
    window.clearTimeout(segmentTimer)
    segmentTimer = undefined
  }

  const abort = () => {
    session += 1
    recordingActive = false
    discardRecording = true
    clearSegmentTimer()
    if (segmentRecorder && segmentRecorder.state !== 'inactive') {
      segmentRecorder.stop()
    }
    segmentRecorder = null
    finishCallback = null
    listening.value = false
    translating.value = false
    pendingSegments = 0
    interim.value = ''
    releaseStream()
  }

  const reset = () => {
    abort()
    error.value = null
    translationFallback.value = false
  }

  const translatorFor = (sourceLanguage: string): Promise<TranslatorInstance> | null => {
    const translatorApi = audioWindow()?.Translator
    if (!translatorApi) return null

    const source = sourceLanguage.split('-')[0]
    let pending = translators.get(source)
    if (!pending) {
      pending = translatorApi.create({
        sourceLanguage: source,
        targetLanguage: 'zh',
      })
      translators.set(source, pending)
    }
    return pending
  }

  const translate = async (text: string, sourceLanguage: SpeechLanguage) => {
    if (sourceLanguage === 'zh-CN') return { text, translated: true }
    const pending = translatorFor(sourceLanguage)
    if (!pending) return { text, translated: false }
    try {
      const translator = await pending
      return { text: await translator.translate(text), translated: true }
    } catch {
      translators.delete(sourceLanguage.split('-')[0])
      return { text, translated: false }
    }
  }

  const enqueueSegment = (
    blob: Blob,
    activeSession: number,
    selectedLanguage: SpeechLanguage,
    callback: (text: string) => void,
  ) => {
    pendingSegments += 1
    translating.value = true
    transcriptionChain = transcriptionChain
      .then(async () => {
        if (activeSession !== session) return
        const form = new FormData()
        form.append('file', blob, `speech.${extensionOf(blob.type)}`)
        form.append('language', selectedLanguage)
        const result = await postForm<TranscriptionResponse>('/speech/transcriptions', form)
        if (activeSession !== session) return

        const translated = await translate(result.text, selectedLanguage)
        if (activeSession !== session) return
        if (!translated.translated) translationFallback.value = true
        if (translated.text.trim()) callback(translated.text.trim())
      })
      .catch((e) => {
        if (activeSession === session) error.value = messageOf(e)
      })
      .finally(() => {
        if (activeSession !== session) return
        pendingSegments = Math.max(0, pendingSegments - 1)
        translating.value = pendingSegments > 0
      })
  }

  const startSegment = (activeSession: number) => {
    if (!recordingActive || !mediaStream || activeSession !== session) return
    const mimeType = preferredMimeType()
    const chunks: Blob[] = []
    const recorder = new MediaRecorder(mediaStream, mimeType ? { mimeType } : undefined)
    segmentRecorder = recorder

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data)
    }
    recorder.onerror = () => {
      if (activeSession === session) {
        error.value = '录音失败，请重试'
        listening.value = false
      }
    }
    recorder.onstop = () => {
      if (segmentRecorder === recorder) segmentRecorder = null
      clearSegmentTimer()

      const blob = new Blob(chunks, { type: mimeType || 'audio/webm' })
      if (!discardRecording && activeSession === session && blob.size > 0) {
        const callback = finishCallback
        if (callback) enqueueSegment(blob, activeSession, language, callback)
      }

      if (recordingActive && activeSession === session) {
        startSegment(activeSession)
      } else {
        releaseStream()
      }
    }

    recorder.start()
    segmentTimer = window.setTimeout(() => {
      if (recordingActive && activeSession === session && recorder.state === 'recording') {
        recorder.stop()
      }
    }, SEGMENT_MILLIS)
  }

  const start = async (
    selectedLanguage: SpeechLanguage,
    onFinalText: (text: string) => void,
  ) => {
    if (!supported) {
      error.value = '当前浏览器不支持录音，请使用 Chrome、Edge 或 Safari'
      return false
    }
    if (!secureContext) {
      error.value = '语音输入需要 HTTPS，或在本机 localhost 访问'
      return false
    }

    abort()
    reset()
    language = selectedLanguage
    finishCallback = onFinalText
    discardRecording = false
    recordingActive = true
    const activeSession = session

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
        },
      })
      if (activeSession !== session || !recordingActive) {
        releaseStream()
        return false
      }
      startSegment(activeSession)
      listening.value = true
      return true
    } catch (e) {
      recordingActive = false
      releaseStream()
      listening.value = false
      error.value = messageOf(e)
      return false
    }
  }

  const stop = () => {
    recordingActive = false
    listening.value = false
    clearSegmentTimer()
    if (segmentRecorder && segmentRecorder.state === 'recording') {
      segmentRecorder.stop()
    } else {
      releaseStream()
    }
  }

  onScopeDispose(() => {
    abort()
    for (const pending of translators.values()) {
      pending.then((translator) => translator.destroy?.()).catch(() => undefined)
    }
    translators.clear()
  })

  return {
    supported,
    secureContext,
    listening,
    translating,
    interim,
    error,
    translationFallback,
    start,
    stop,
    abort,
    reset,
  }
}
