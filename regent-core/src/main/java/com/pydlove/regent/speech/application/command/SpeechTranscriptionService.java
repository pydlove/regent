package com.pydlove.regent.speech.application.command;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.speech.domain.model.SpeechAudio;
import com.pydlove.regent.speech.domain.model.SpeechLanguage;
import com.pydlove.regent.speech.domain.port.SpeechTranscriber;

/** 语音转写用例。 */
public class SpeechTranscriptionService {

    private final SpeechTranscriber transcriber;
    private final long maxBytes;

    public SpeechTranscriptionService(SpeechTranscriber transcriber, long maxBytes) {
        this.transcriber = transcriber;
        this.maxBytes = maxBytes;
    }

    public String transcribe(SpeechAudio audio, SpeechLanguage language) {
        if (audio.content().length > maxBytes) {
            throw new DomainException("录音过大（上限 " + maxBytes + " 字节）");
        }
        String text = transcriber.transcribe(audio, language);
        if (text == null || text.isBlank()) {
            throw new DomainException("没有识别到语音内容，请重新录制");
        }
        return text.trim();
    }
}
