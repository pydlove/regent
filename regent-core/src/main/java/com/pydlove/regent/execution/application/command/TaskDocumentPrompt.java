package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskDocument;

import java.util.List;

/** 把任务文档路径翻译成执行器可读的资料清单。 */
final class TaskDocumentPrompt {

    private TaskDocumentPrompt() {
    }

    static String section(List<TaskDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder("\n\n---\n\n## 任务文档\n")
                .append("以下文档是本任务提供的只读参考资料。需要时请直接读取对应路径，不要修改或删除：\n");
        for (TaskDocument document : documents) {
            prompt.append("- ").append(document.filename())
                    .append("（").append(document.contentType())
                    .append("，").append(document.sizeBytes()).append(" 字节）：")
                    .append(document.storagePath()).append('\n');
        }
        return prompt.toString();
    }
}
