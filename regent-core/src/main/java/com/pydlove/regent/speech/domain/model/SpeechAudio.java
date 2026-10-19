package com.pydlove.regent.speech.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;
import java.util.Objects;

/** 一段待转写的录音。文件名只用于落临时文件，不参与命令行拼接。 */
public record SpeechAudio(String filename, String contentType, byte[] content) {

    public SpeechAudio {
        if (filename == null || filename.isBlank()) {
            throw new DomainException("录音文件名不能为空");
        }
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            throw new DomainException("只接受音频文件");
        }
        Objects.requireNonNull(content, "录音内容不能为空");
        if (content.length == 0) {
            throw new DomainException("没有收到录音内容");
        }
    }
}
