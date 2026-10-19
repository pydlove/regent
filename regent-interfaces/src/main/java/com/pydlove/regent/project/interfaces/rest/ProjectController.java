package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.ProjectSummaryGenerationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.application.query.DirectoryListingService;
import com.pydlove.regent.project.application.query.ProjectGitBranchQueryService;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.interfaces.dto.DirectoryListingResponse;
import com.pydlove.regent.project.interfaces.dto.ProjectCreateRequest;
import com.pydlove.regent.project.interfaces.dto.ProjectGitBranchResponse;
import com.pydlove.regent.project.interfaces.dto.ProjectResponse;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目接口。
 *
 * <p>读侧直接给全量列表、不分页：项目是这个平台里数量最少的东西（个位数），
 * 分页是照猫画虎。写侧只有注册 —— 项目是任务的前提，没有它录不进任务。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectRegistrationService projectRegistrationService;
    private final DirectoryListingService directoryListingService;
    private final ProjectGitBranchQueryService projectGitBranchQueryService;
    private final ProjectSummaryGenerationService projectSummaryGenerationService;

    public ProjectController(ProjectProfileRepository projectProfileRepository,
                             ProjectRegistrationService projectRegistrationService,
                             DirectoryListingService directoryListingService,
                             ProjectGitBranchQueryService projectGitBranchQueryService,
                             ProjectSummaryGenerationService projectSummaryGenerationService) {
        this.projectProfileRepository = projectProfileRepository;
        this.projectRegistrationService = projectRegistrationService;
        this.directoryListingService = directoryListingService;
        this.projectGitBranchQueryService = projectGitBranchQueryService;
        this.projectSummaryGenerationService = projectSummaryGenerationService;
    }

    /**
     * 列子目录，给注册表单的路径字段当选择器用。
     *
     * <p>挂在 {@code /api/projects} 下不是因为它属于项目聚合，而是因为它只服务于项目注册 ——
     * 全平台只有这一个地方要人填本机路径。不传 {@code path} 就从用户主目录开始。
     *
     * <p>它是唯一能读本机目录树的接口，成立的前提是服务只绑本机（{@code server.address}）。
     * 哪天后端要挪到服务器上多人共用，这个接口得先有鉴权与白名单，别直接搬过去。
     */
    @GetMapping("/directories")
    public DirectoryListingResponse directories(@RequestParam(required = false) String path) {
        return DirectoryListingResponse.from(directoryListingService.list(path));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectProfileRepository.findAll().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{id}/git-branch")
    public ProjectGitBranchResponse gitBranch(@PathVariable long id) {
        return new ProjectGitBranchResponse(
                projectGitBranchQueryService.currentBranch(ProjectId.of(id)).orElse(null));
    }

    /**
     * 注册项目。
     *
     * <p>返回读模型而不是拿在手里的聚合：注册之后「库里现在是什么」才是前端该看的，
     * 顺便把「主键是数据库生成的」这个实现细节挡在接口之外 —— 拿聚合直接序列化的话，
     * insert 回填前后返回的 JSON 会不一样。
     */
    @PostMapping
    public ProjectResponse create(@RequestBody ProjectCreateRequest request) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                request.code(),
                request.name(),
                request.path(),
                // 技术栈与验证方式还没有入口，等情况出现时在请求体、画像、响应三处一起加
                null,
                null,
                DecisionLevel.parse(request.defaultDecisionLevel())));
        return ProjectResponse.from(projectProfileRepository.findById(project.getId())
                .orElseThrow(() -> new EntityNotFoundException("项目刚注册就查不到了：" + project.getId())));
    }

    /** 手动重新生成项目资料；同一项目生成中时调度器会忽略重复请求。 */
    @PostMapping("/{id}/summary/regenerate")
    public ProjectResponse regenerateSummary(@PathVariable long id) {
        ProjectId projectId = ProjectId.of(id);
        projectSummaryGenerationService.schedule(projectId);
        return ProjectResponse.from(projectProfileRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + id)));
    }
}
