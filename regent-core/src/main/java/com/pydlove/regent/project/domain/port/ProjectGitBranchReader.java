package com.pydlove.regent.project.domain.port;

import java.util.Optional;

/**
 * 读取项目工作区当前 Git 分支的端口。
 *
 * <p>分支是工作区的运行时状态，不进入项目画像；没有 Git 管理或处于游离 HEAD 时返回空。
 */
public interface ProjectGitBranchReader {

    Optional<String> currentBranch(String projectPath);
}
