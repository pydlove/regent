package com.pydlove.regent.speech.interfaces.rest;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.speech.application.command.SpeechTranscriptionService;
import com.pydlove.regent.speech.domain.model.SpeechAudio;
import com.pydlove.regent.speech.domain.model.SpeechLanguage;
import com.pydlove.regent.speech.interfaces.dto.SpeechTranscriptionResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 新建任务弹框的语音转写接口。录音只在本机处理，不写数据库。 */
@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechTranscriptionService service;

    public SpeechController(SpeechTranscriptionService service) {
        this.service = service;
    }

    @PostMapping("/transcriptions")
    public SpeechTranscriptionResponse transcribe(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(defaultValue = "zh-CN") String language) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("没有收到录音内容");
        }
        try {
            SpeechLanguage parsed = SpeechLanguage.parse(language);
            String text = service.transcribe(new SpeechAudio(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()), parsed);
            return new SpeechTranscriptionResponse(text, parsed.name());
        } catch (IOException e) {
            throw new DomainException("录音读取失败：" + e.getMessage());
        }
    }
}
