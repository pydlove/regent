package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.command.CodeEditorService;
import com.pydlove.regent.project.application.command.OpenProjectCodeCommand;
import com.pydlove.regent.project.domain.model.CodeEditorId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.interfaces.dto.CodeEditorResponse;
import com.pydlove.regent.project.interfaces.dto.OpenProjectCodeRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 本机代码编辑器检测与打开接口。 */
@RestController
@RequestMapping("/api/projects/{projectId}/code-editors")
public class CodeEditorController {

    private final CodeEditorService service;

    public CodeEditorController(CodeEditorService service) {
        this.service = service;
    }

    @GetMapping
    public List<CodeEditorResponse> available(@PathVariable long projectId,
                                              HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return service.available(ProjectId.of(projectId), UserId.of(user.id())).stream()
                .map(CodeEditorResponse::from)
                .toList();
    }

    @PostMapping("/{editorId}/open")
    public void open(@PathVariable long projectId,
                     @PathVariable String editorId,
                     @RequestBody OpenProjectCodeRequest body,
                     HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        service.open(new OpenProjectCodeCommand(
                ProjectId.of(projectId),
                UserId.of(user.id()),
                CodeEditorId.valueOf(editorId.strip().toUpperCase()),
                body.relativePath(),
                body.line()));
    }
}
