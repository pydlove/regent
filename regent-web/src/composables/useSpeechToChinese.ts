import { onScopeDispose, ref } from 'vue'

export type SpeechLanguage = 'zh-CN' | 'en-US' | 'ja-JP' | 'ko-KR'

type SpeechRecognitionAlternativeLike = {
  transcript: string
}

type SpeechRecognitionResultLike = {
  readonly isFinal: boolean
  readonly length: number
  readonly [index: number]: SpeechRecognitionAlternativeLike
}

type SpeechRecognitionResultListLike = {
  readonly length: number
  readonly [index: number]: SpeechRecognitionResultLike
}

type SpeechRecognitionEventLike = Event & {
  readonly resultIndex: number
  readonly results: SpeechRecognitionResultListLike
}

type SpeechRecognitionErrorEventLike = Event & {
  readonly error: string
  readonly message?: string
}

type SpeechRecognitionLike = EventTarget & {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  onstart: ((event: Event) => void) | null
  onend: ((event: Event) => void) | null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  start(): void
  stop(): void
  abort(): void
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

type TranslatorInstance = {
  translate(text: string): Promise<string>
  destroy?(): void
}

type TranslatorApi = {
  create(options: { sourceLanguage: string; targetLanguage: string }): Promise<TranslatorInstance>
}

type SpeechWindow = Window & {
  SpeechRecognition?: SpeechRecognitionConstructor
  webkitSpeechRecognition?: SpeechRecognitionConstructor
  Translator?: TranslatorApi
}

function speechWindow(): SpeechWindow | null {
  return typeof window === 'undefined' ? null : (window as SpeechWindow)
}

function recognitionConstructor(): SpeechRecognitionConstructor | null {
  const target = speechWindow()
  return target?.SpeechRecognition ?? target?.webkitSpeechRecognition ?? null
}

function friendlyError(code: string): string {
  if (code === 'not-allowed' || code === 'service-not-allowed') {
    return '麦克风权限被拒绝，请在浏览器设置中允许'
  }
  if (code === 'audio-capture') return '没有检测到可用麦克风'
  if (code === 'no-speech') return '没有听清，请再试一次'
  if (code === 'network') return '语音识别服务网络不可用'
  return '语音识别失败，请重试'
}

/**
 * 浏览器语音输入。
 *
 * 中文直接用 Web Speech API 输出；其它语言在浏览器提供 Translator API 时翻译到中文。
 * 两套能力都只依赖浏览器，不把音频或文本上传到本平台后端。
 */
export function useSpeechToChinese() {
  const supported = recognitionConstructor() !== null
  const secureContext = typeof window !== 'undefined' && window.isSecureContext

  const listening = ref(false)
  const translating = ref(false)
  const interim = ref('')
  const error = ref<string | null>(null)
  const translationFallback = ref(false)

  let recognition: SpeechRecognitionLike | null = null
  let appendQueue = Promise.resolve()
  let session = 0
  const translators = new Map<string, Promise<TranslatorInstance>>()

  const stop = () => {
    recognition?.stop()
  }

  const abort = () => {
    session += 1
    recognition?.abort()
    recognition = null
    listening.value = false
    translating.value = false
    interim.value = ''
  }

  const reset = () => {
    abort()
    error.value = null
    translating.value = false
    translationFallback.value = false
  }

  const translatorFor = (sourceLanguage: string): Promise<TranslatorInstance> | null => {
    const translatorApi = speechWindow()?.Translator
    if (!translatorApi) return null

    const source = sourceLanguage.split('-')[0]
    let pendingTranslator = translators.get(source)
    if (!pendingTranslator) {
      pendingTranslator = translatorApi.create({
        sourceLanguage: source,
        targetLanguage: 'zh',
      })
      translators.set(source, pendingTranslator)
    }
    return pendingTranslator
  }

  const translate = async (text: string, language: SpeechLanguage) => {
    if (language === 'zh-CN') return { text, translated: true }

    const pendingTranslator = translatorFor(language)
    if (!pendingTranslator) return { text, translated: false }

    try {
      const translator = await pendingTranslator
      return { text: await translator.translate(text), translated: true }
    } catch {
      translators.delete(language.split('-')[0])
      return { text, translated: false }
    }
  }

  const start = (
    language: SpeechLanguage,
    onFinalText: (text: string) => void,
  ) => {
    const Constructor = recognitionConstructor()
    if (!Constructor) {
      error.value = '当前浏览器不支持语音输入，请使用 Chrome、Edge 或 Safari'
      return false
    }
    if (!secureContext) {
      error.value = '语音输入需要 HTTPS，或在本机 localhost 访问'
      return false
    }

    abort()
    reset()
    const activeSession = session

    const next = new Constructor()
    next.lang = language
    next.continuous = true
    next.interimResults = true
    next.maxAlternatives = 1
    next.onstart = () => {
      listening.value = true
    }
    next.onend = () => {
      listening.value = false
      interim.value = ''
    }
    next.onerror = (event) => {
      listening.value = false
      interim.value = ''
      error.value = friendlyError(event.error)
    }
    next.onresult = (event) => {
      let interimText = ''
      const finals: string[] = []
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index]
        const transcript = result[0]?.transcript ?? ''
        if (result.isFinal) finals.push(transcript)
        else interimText += transcript
      }
      interim.value = interimText

      for (const finalText of finals) {
        appendQueue = appendQueue.then(async () => {
          if (activeSession !== session) return
          translating.value = true
          try {
            const result = await translate(finalText, language)
            if (activeSession !== session) return
            if (!result.translated) translationFallback.value = true
            if (result.text.trim()) onFinalText(result.text.trim())
          } finally {
            if (activeSession === session) translating.value = false
          }
        }).catch(() => undefined)
      }
    }

    recognition = next
    try {
      next.start()
      return true
    } catch {
      recognition = null
      listening.value = false
      error.value = '语音输入未能启动，请稍后重试'
      return false
    }
  }

  onScopeDispose(() => {
    abort()
    for (const pendingTranslator of translators.values()) {
      pendingTranslator.then((translator) => translator.destroy?.()).catch(() => undefined)
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
