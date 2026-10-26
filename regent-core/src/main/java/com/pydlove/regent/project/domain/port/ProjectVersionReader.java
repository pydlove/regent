package com.pydlove.regent.project.domain.port;

import java.util.Optional;

/** 读取项目当前提交上的 Git Tag。 */
public interface ProjectVersionReader {

    Optional<String> currentTag(String projectPath);
}
