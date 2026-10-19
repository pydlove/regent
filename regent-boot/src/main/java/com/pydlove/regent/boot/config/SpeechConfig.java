package com.pydlove.regent.boot.config;

import com.pydlove.regent.speech.application.command.SpeechTranscriptionService;
import com.pydlove.regent.speech.domain.port.SpeechTranscriber;
import com.pydlove.regent.speech.infrastructure.WhisperCliSpeechTranscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

/** 本机语音转写装配。 */
@Configuration
public class SpeechConfig {

    @Bean
    public SpeechTranscriber speechTranscriber(
            @Value("${regent.home}") String home,
            @Value("${regent.speech.whisper-command:whisper}") String command,
            @Value("${regent.speech.model:base}") String model,
            @Value("${regent.speech.timeout:PT2M}") Duration timeout,
            @Value("${regent.speech.path-prefix:}") String pathPrefix) {
        return new WhisperCliSpeechTranscriber(
                Path.of(home, "speech"),
                command,
                model,
                timeout,
                pathPrefix);
    }

    @Bean
    public SpeechTranscriptionService speechTranscriptionService(
            SpeechTranscriber speechTranscriber,
            @Value("${regent.speech.max-bytes:20971520}") long maxBytes) {
        return new SpeechTranscriptionService(speechTranscriber, maxBytes);
    }
}
