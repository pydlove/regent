package com.pydlove.regent.speech.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 本机 Whisper 支持的听写语言。 */
public enum SpeechLanguage {

    ZH("zh"),
    EN("en"),
    JA("ja"),
    KO("ko");

    private final String whisperCode;

    SpeechLanguage(String whisperCode) {
        this.whisperCode = whisperCode;
    }

    public String whisperCode() {
        return whisperCode;
    }

    public static SpeechLanguage parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZH;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ZH")) return ZH;
        if (normalized.startsWith("EN")) return EN;
        if (normalized.startsWith("JA")) return JA;
        if (normalized.startsWith("KO")) return KO;
        throw new DomainException("不支持的语音语言：" + raw);
    }
}
