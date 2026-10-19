package com.pydlove.regent.project.domain.port;

import com.pydlove.regent.project.domain.model.ProjectSummaryRequest;

/**
 * 项目资料生成端口。
 *
 * <p>实现必须只读分析工作区，返回适合人阅读的 Markdown 文本；命令、进程和 Codex 出参格式
 * 都由基础设施适配器消化，不进入项目领域。
 */
public interface ProjectSummaryGenerator {

    String generate(ProjectSummaryRequest request);
}
