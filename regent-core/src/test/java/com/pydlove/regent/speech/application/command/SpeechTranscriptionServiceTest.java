package com.pydlove.regent.speech.application.command;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.speech.domain.model.SpeechAudio;
import com.pydlove.regent.speech.domain.model.SpeechLanguage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpeechTranscriptionServiceTest {

    private static final byte[] AUDIO = "audio".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("转写结果会去除首尾空白")
    void trimsTranscript() {
        SpeechTranscriptionService service = new SpeechTranscriptionService(
                (audio, language) -> "  帮我新建任务  ",
                1_024);

        assertEquals("帮我新建任务", service.transcribe(
                new SpeechAudio("speech.webm", "audio/webm", AUDIO),
                SpeechLanguage.ZH));
    }

    @Test
    @DisplayName("空转写结果按失败处理")
    void rejectsEmptyTranscript() {
        SpeechTranscriptionService service = new SpeechTranscriptionService(
                (audio, language) -> " ",
                1_024);

        assertThrows(DomainException.class, () -> service.transcribe(
                new SpeechAudio("speech.webm", "audio/webm", AUDIO),
                SpeechLanguage.ZH));
    }

    @Test
    @DisplayName("超过上限的录音不会进入转写器")
    void rejectsOversizedAudio() {
        SpeechTranscriptionService service = new SpeechTranscriptionService(
                (audio, language) -> "不应调用",
                4);

        assertThrows(DomainException.class, () -> service.transcribe(
                new SpeechAudio("speech.webm", "audio/webm", AUDIO),
                SpeechLanguage.ZH));
    }
}
