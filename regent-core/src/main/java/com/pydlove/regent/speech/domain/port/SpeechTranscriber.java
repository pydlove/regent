package com.pydlove.regent.speech.domain.port;

import com.pydlove.regent.speech.domain.model.SpeechAudio;
import com.pydlove.regent.speech.domain.model.SpeechLanguage;

/** 语音转写端口。具体使用本机 Whisper 还是其它离线引擎由基础设施决定。 */
public interface SpeechTranscriber {

    String transcribe(SpeechAudio audio, SpeechLanguage language);
}
