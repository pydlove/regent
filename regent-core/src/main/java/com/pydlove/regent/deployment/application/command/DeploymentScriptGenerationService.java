package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.application.query.GeneratedDeploymentScript;
import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentScriptGenerationRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.port.DeploymentScriptGenerator;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.deployment.domain.service.DeploymentScriptValidator;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 生成部署脚本的用例：限定工作区范围，并在返回前执行与保存相同的规范检查。 */
public class DeploymentScriptGenerationService {

    private static final int MAX_REMOTE_DIRECTORY_LENGTH = 512;

    private final ProjectRepository projectRepository;
    private final DeploymentServerRepository serverRepository;
    private final DeploymentScriptGenerator generator;
    private final ConcurrentMap<String, Thread> activeRequests = new ConcurrentHashMap<>();

    public DeploymentScriptGenerationService(ProjectRepository projectRepository,
                                             DeploymentServerRepository serverRepository,
                                             DeploymentScriptGenerator generator) {
        this.projectRepository = projectRepository;
        this.serverRepository = serverRepository;
        this.generator = generator;
    }

    public GeneratedDeploymentScript generate(GenerateDeploymentScriptCommand command) {
        return generate(command, "internal-" + UUID.randomUUID());
    }

    public GeneratedDeploymentScript generate(GenerateDeploymentScriptCommand command,
                                              String requestId) {
        String id = requireRequestId(requestId);
        Thread worker = Thread.currentThread();
        if (activeRequests.putIfAbsent(id, worker) != null) {
            throw new DomainException("部署脚本生成请求正在执行：" + id);
        }
        try {
            return doGenerate(command);
        } catch (RuntimeException e) {
            if (worker.isInterrupted()) {
                throw new DomainException("部署脚本生成已停止");
            }
            throw e;
        } finally {
            activeRequests.remove(id);
            Thread.interrupted();
        }
    }

    /** 返回 false 表示请求已经结束或不存在，不影响调用方恢复空闲状态。 */
    public boolean cancel(String requestId) {
        Thread worker = activeRequests.get(requireRequestId(requestId));
        if (worker == null) {
            return false;
        }
        worker.interrupt();
        return true;
    }

    private GeneratedDeploymentScript doGenerate(GenerateDeploymentScriptCommand command) {
        if (command.projectId() == null) {
            throw new DomainException("请选择项目");
        }
        if (command.serverId() == null) {
            throw new DomainException("请选择部署服务器");
        }

        Project project = projectRepository.findById(command.projectId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + command.projectId().value()));
        DeploymentServer server = serverRepository.findById(command.serverId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "部署服务器不存在：" + command.serverId().value()));
        if (!server.getProjectId().equals(project.getId())) {
            throw new DomainException("部署与服务器必须属于同一项目");
        }

        Path projectRoot = realDirectory(project.getPath(), "项目目录");
        Path workspace = command.workspacePath() == null || command.workspacePath().isBlank()
                ? projectRoot
                : realDirectory(command.workspacePath(), "项目文件夹");
        if (!workspace.startsWith(projectRoot)) {
            throw new DomainException("项目文件夹必须位于项目目录内：" + projectRoot);
        }

        String remoteDirectory = requireRemoteDirectory(command.remoteDirectory());
        String generated = generator.generate(new DeploymentScriptGenerationRequest(
                project.getId(),
                project.getCode(),
                project.getName(),
                projectRoot.toString(),
                project.getTechStack(),
                project.getVerifyMethod(),
                project.getSummary(),
                workspace.toString(),
                server.getName(),
                server.getHost(),
                server.getPort(),
                server.getUsername(),
                remoteDirectory));
        String scriptContent = normalize(generated);
        if (scriptContent.length() > Deployment.MAX_SCRIPT_LENGTH) {
            throw new DomainException(
                    "生成的部署脚本超长（上限 " + Deployment.MAX_SCRIPT_LENGTH + " 字符）");
        }
        DeploymentScriptValidator.requireValid(scriptContent);
        return new GeneratedDeploymentScript(
                "deploy_" + project.getCode() + ".sh",
                scriptContent);
    }

    private static String requireRequestId(String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9-]{8,80}")) {
            throw new DomainException("部署脚本生成请求 ID 不合法");
        }
        return requestId;
    }

    private static String normalize(String script) {
        if (script == null || script.isBlank()) {
            throw new DomainException("Codex 未返回部署脚本");
        }
        String normalized = script.replace("\r\n", "\n").replace('\r', '\n').strip();
        return normalized + "\n";
    }

    private static Path realDirectory(String rawPath, String field) {
        try {
            Path path = Path.of(rawPath == null ? "" : rawPath.trim()).toRealPath();
            if (!Files.isDirectory(path)) {
                throw new DomainException(field + "不是目录：" + path);
            }
            return path;
        } catch (InvalidPathException e) {
            throw new DomainException(field + "路径不合法：" + rawPath);
        } catch (IOException e) {
            throw new DomainException(field + "不存在或无法读取：" + rawPath);
        }
    }

    private static String requireRemoteDirectory(String remoteDirectory) {
        if (remoteDirectory == null || remoteDirectory.isBlank()) {
            throw new DomainException("远程目录不能为空");
        }
        String value = remoteDirectory.trim();
        if (value.length() > MAX_REMOTE_DIRECTORY_LENGTH) {
            throw new DomainException("远程目录超长（上限 512 字符）");
        }
        if (!value.equals("~") && !value.startsWith("/") && !value.startsWith("~/")) {
            throw new DomainException("远程目录必须使用 ~ 或绝对路径：" + value);
        }
        if (!value.matches("[A-Za-z0-9._~/-]+")) {
            throw new DomainException("远程目录只能包含字母、数字、点、下划线、斜杠和 ~：" + value);
        }
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                throw new DomainException("远程目录不能包含 .. 路径段：" + value);
            }
        }
        return value;
    }
}
