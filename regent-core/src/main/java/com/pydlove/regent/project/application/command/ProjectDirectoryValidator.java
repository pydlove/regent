package com.pydlove.regent.project.application.command;

import com.pydlove.regent.shared.domain.DomainException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 项目工作区路径的应用层校验。
 *
 * <p>直接使用 {@code java.nio.file}：这是对用户输入的一次本机校验，不是需要适配的外部系统。
 */
final class ProjectDirectoryValidator {

    private ProjectDirectoryValidator() {
    }

    static void requireExistingDirectory(String path) {
        try {
            if (!Files.isDirectory(Path.of(path))) {
                throw new DomainException("项目路径不存在或不是目录：" + path);
            }
        } catch (InvalidPathException e) {
            throw new DomainException("项目路径不是合法路径：" + path);
        }
    }
}
