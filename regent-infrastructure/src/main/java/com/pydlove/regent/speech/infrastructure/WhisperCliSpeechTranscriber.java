package com.pydlove.regent.speech.infrastructure;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.speech.domain.model.SpeechAudio;
import com.pydlove.regent.speech.domain.model.SpeechLanguage;
import com.pydlove.regent.speech.domain.port.SpeechTranscriber;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本机 Whisper CLI 转写适配器。
 *
 * <p>录音先落临时目录，再以参数数组启动进程，不经过 shell。返回文本来自 Whisper 的 txt
 * 输出文件；识别失败时把进程输出的末尾带回界面，便于判断是模型、ffmpeg 还是音频格式问题。
 */
public class WhisperCliSpeechTranscriber implements SpeechTranscriber {

    private final Path tempRoot;
    private final String command;
    private final String model;
    private final Duration timeout;
    private final String pathPrefix;

    public WhisperCliSpeechTranscriber(Path tempRoot,
                                       String command,
                                       String model,
                                       Duration timeout,
                                       String pathPrefix) {
        this.tempRoot = tempRoot;
        this.command = requireText(command, "Whisper 命令");
        this.model = requireText(model, "Whisper 模型");
        this.timeout = timeout;
        this.pathPrefix = pathPrefix == null ? "" : pathPrefix.trim();
    }

    @Override
    public String transcribe(SpeechAudio audio, SpeechLanguage language) {
        Path tempDirectory = null;
        try {
            Files.createDirectories(tempRoot);
            tempDirectory = Files.createTempDirectory(tempRoot, "speech-");
            String extension = extensionOf(audio.filename(), audio.contentType());
            Path audioFile = tempDirectory.resolve("speech" + extension);
            Files.write(audioFile, audio.content());
            Path outputDirectory = Files.createDirectories(tempDirectory.resolve("out"));

            List<String> commandLine = new ArrayList<>(List.of(
                    command,
                    audioFile.toString(),
                    "--model", model,
                    "--language", language.whisperCode(),
                    "--task", "transcribe",
                    "--output_dir", outputDirectory.toString(),
                    "--output_format", "txt",
                    "--verbose", "False",
                    "--fp16", "False"));
            if (language == SpeechLanguage.ZH) {
                commandLine.add("--initial_prompt");
                commandLine.add("以下是普通话任务指令，请使用简体中文输出。");
            }

            ProcessBuilder builder = new ProcessBuilder(commandLine);
            Map<String, String> environment = builder.environment();
            if (!pathPrefix.isBlank()) {
                String currentPath = environment.getOrDefault("PATH", "");
                environment.put("PATH", currentPath.isBlank()
                        ? pathPrefix
                        : pathPrefix + ":" + currentPath);
            }
            environment.put("PYTHONUNBUFFERED", "1");
            builder.redirectErrorStream(true);

            Process process;
            try {
                process = builder.start();
            } catch (IOException e) {
                throw new DomainException("无法启动本机 Whisper（" + command + "）：" + e.getMessage(), e);
            }

            ProcessOutput collector = new ProcessOutput(process.getInputStream());
            Thread pump = new Thread(collector, "regent-whisper-output");
            pump.setDaemon(true);
            pump.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                throw new DomainException("语音转写超时（" + timeout + "）");
            }
            pump.join(2_000);
            if (process.exitValue() != 0) {
                throw new DomainException("语音转写失败：" + tail(collector.text()));
            }

            Path resultFile = outputDirectory.resolve("speech.txt");
            if (!Files.isRegularFile(resultFile)) {
                throw new DomainException("Whisper 没有生成转写结果：" + tail(collector.text()));
            }
            return Files.readString(resultFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new DomainException("语音转写临时文件处理失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("语音转写被中断", e);
        } finally {
            deleteRecursively(tempDirectory);
        }
    }

    private static String extensionOf(String filename, String contentType) {
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("webm")) return ".webm";
        if (normalizedType.contains("ogg")) return ".ogg";
        if (normalizedType.contains("mp4") || normalizedType.contains("m4a")) return ".m4a";
        if (normalizedType.contains("mpeg") || normalizedType.contains("mp3")) return ".mp3";
        if (normalizedType.contains("wav")) return ".wav";
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? ".audio" : lower.substring(dot);
    }

    private static String tail(String output) {
        String normalized = output == null ? "" : output.strip();
        if (normalized.length() <= 1_000) {
            return normalized;
        }
        return normalized.substring(normalized.length() - 1_000);
    }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 临时文件清理失败不影响转写结果
                        }
                    });
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static final class ProcessOutput implements Runnable {

        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private ProcessOutput(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4_096];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    synchronized (output) {
                        if (output.size() < 32_768) {
                            output.write(buffer, 0, Math.min(read, 32_768 - output.size()));
                        }
                    }
                }
            } catch (IOException ignored) {
                // 进程退出时流关闭属于正常情况
            }
        }

        private String text() {
            synchronized (output) {
                return output.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
