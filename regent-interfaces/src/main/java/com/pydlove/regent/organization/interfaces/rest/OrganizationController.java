package com.pydlove.regent.organization.interfaces.rest;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.organization.application.command.CreateOrganizationCommand;
import com.pydlove.regent.organization.application.command.CreateOrganizationAccountCommand;
import com.pydlove.regent.organization.application.command.InviteOrganizationMemberCommand;
import com.pydlove.regent.organization.application.command.OrganizationCommandService;
import com.pydlove.regent.organization.application.command.UpdateOrganizationCommand;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.application.query.OrganizationQueryService;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.interfaces.dto.AcceptedOrganizationInvitationResponse;
import com.pydlove.regent.organization.interfaces.dto.CreateOrganizationRequest;
import com.pydlove.regent.organization.interfaces.dto.CreateOrganizationAccountRequest;
import com.pydlove.regent.organization.interfaces.dto.CreatedOrganizationAccountResponse;
import com.pydlove.regent.organization.interfaces.dto.CreatedOrganizationInvitationResponse;
import com.pydlove.regent.organization.interfaces.dto.InviteOrganizationMemberRequest;
import com.pydlove.regent.organization.interfaces.dto.OrganizationAuditResponse;
import com.pydlove.regent.organization.interfaces.dto.OrganizationInvitationResponse;
import com.pydlove.regent.organization.interfaces.dto.OrganizationMemberResponse;
import com.pydlove.regent.organization.interfaces.dto.OrganizationMemberRoleRequest;
import com.pydlove.regent.organization.interfaces.dto.OrganizationResponse;
import com.pydlove.regent.organization.interfaces.dto.ResetOrganizationMemberPasswordResponse;
import com.pydlove.regent.organization.interfaces.dto.UpdateOrganizationRequest;
import com.pydlove.regent.organization.interfaces.excel.OrganizationMemberExcel;
import com.pydlove.regent.project.interfaces.dto.ProjectResponse;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private static final String IMPORT_TOTAL = "X-Regent-Import-Total";
    private static final String IMPORT_CREATED = "X-Regent-Import-Created";
    private static final String IMPORT_ADDED = "X-Regent-Import-Added";
    private static final String IMPORT_SKIPPED = "X-Regent-Import-Skipped";
    private static final String IMPORT_FAILED = "X-Regent-Import-Failed";
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final OrganizationCommandService commandService;
    private final OrganizationQueryService queryService;
    private final OrganizationAccessService accessService;

    public OrganizationController(OrganizationCommandService commandService,
                                  OrganizationQueryService queryService,
                                  OrganizationAccessService accessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<OrganizationResponse> list(HttpServletRequest request) {
        return queryService.listMine(currentUser(request)).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @PostMapping
    public OrganizationResponse create(@RequestBody CreateOrganizationRequest request,
                                       HttpServletRequest servletRequest) {
        UserId actorId = currentUser(servletRequest);
        var organization = commandService.create(
                new CreateOrganizationCommand(request.code(), request.name(), request.description()),
                actorId);
        return OrganizationResponse.from(queryService.get(organization.getId(), actorId));
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable long id, HttpServletRequest request) {
        return OrganizationResponse.from(
                queryService.get(OrganizationId.of(id), currentUser(request)));
    }

    @PutMapping("/{id}")
    public OrganizationResponse update(@PathVariable long id,
                                       @RequestBody UpdateOrganizationRequest request,
                                       HttpServletRequest servletRequest) {
        UserId actorId = currentUser(servletRequest);
        var organization = commandService.update(
                OrganizationId.of(id),
                new UpdateOrganizationCommand(request.name(), request.description()),
                actorId);
        return OrganizationResponse.from(queryService.get(organization.getId(), actorId));
    }

    @GetMapping("/{id}/members")
    public List<OrganizationMemberResponse> members(@PathVariable long id,
                                                    HttpServletRequest request) {
        return queryService.members(OrganizationId.of(id), currentUser(request)).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
    }

    @GetMapping("/{id}/members/export")
    public ResponseEntity<byte[]> exportMembers(@PathVariable long id,
                                                HttpServletRequest request) {
        UserId actorId = currentUser(request);
        accessService.requireAdmin(OrganizationId.of(id), actorId);
        byte[] content = OrganizationMemberExcel.export(
                queryService.members(OrganizationId.of(id), actorId));
        return excelResponse(content, "Regent-组织成员-" + id + ".xlsx");
    }

    @GetMapping("/{id}/members/import-template")
    public ResponseEntity<byte[]> memberImportTemplate(@PathVariable long id,
                                                       HttpServletRequest request) {
        accessService.requireAdmin(OrganizationId.of(id), currentUser(request));
        return excelResponse(OrganizationMemberExcel.template(),
                "Regent-组织成员导入模板.xlsx");
    }

    @PostMapping(
            value = "/{id}/members/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> importMembers(@PathVariable long id,
                                                @RequestParam("file") MultipartFile file,
                                                HttpServletRequest request) {
        UserId actorId = currentUser(request);
        OrganizationId organizationId = OrganizationId.of(id);
        accessService.requireAdmin(organizationId, actorId);
        if (file.isEmpty()) {
            throw new DomainException("请选择要导入的 Excel 文件");
        }
        if (file.getSize() > OrganizationMemberExcel.MAX_FILE_BYTES) {
            throw new DomainException("导入文件不能超过 2MB");
        }

        List<OrganizationMemberExcel.ParsedRow> parsed;
        try {
            parsed = OrganizationMemberExcel.parse(file.getInputStream());
        } catch (IOException exception) {
            throw new DomainException("Excel 文件无法读取，请重新选择");
        }

        List<OrganizationMemberExcel.ResultRow> results = new ArrayList<>(parsed.size());
        int created = 0;
        int added = 0;
        int skipped = 0;
        int failed = 0;
        for (OrganizationMemberExcel.ParsedRow row : parsed) {
            if (row.error() != null) {
                failed++;
                results.add(new OrganizationMemberExcel.ResultRow(
                        row.rowNumber(), row.email(), row.displayName(), row.roleText(),
                        "失败", "", "", row.error()));
                continue;
            }
            try {
                var imported = commandService.importMember(
                        organizationId,
                        row.email(),
                        row.displayName(),
                        row.role(),
                        actorId);
                String outcome;
                String message;
                if (imported.accountCreated()) {
                    created++;
                    outcome = "新建账号";
                    message = "首次登录后必须修改初始密码";
                } else if (imported.roleAdded()) {
                    added++;
                    outcome = "已加入组织";
                    message = "已有账号，仅新增组织角色";
                } else {
                    skipped++;
                    outcome = "已是成员";
                    message = "该账号已拥有此角色，未重复添加";
                }
                results.add(new OrganizationMemberExcel.ResultRow(
                        row.rowNumber(),
                        imported.user().getEmail() == null
                                ? row.email() : imported.user().getEmail().value(),
                        imported.user().getDisplayName().value(),
                        OrganizationMemberExcel.roleLabel(imported.role()),
                        outcome,
                        imported.user().getUsername().value(),
                        imported.initialPassword() == null ? "" : imported.initialPassword(),
                        message));
            } catch (DomainException exception) {
                failed++;
                results.add(new OrganizationMemberExcel.ResultRow(
                        row.rowNumber(), row.email(), row.displayName(), row.roleText(),
                        "失败", "", "", exception.getMessage()));
            }
        }

        String filename = "Regent-组织成员导入结果-"
                + LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(FILE_TIME)
                + ".xlsx";
        return excelResponse(
                OrganizationMemberExcel.importResult(results),
                filename,
                Map.of(
                        IMPORT_TOTAL, Integer.toString(parsed.size()),
                        IMPORT_CREATED, Integer.toString(created),
                        IMPORT_ADDED, Integer.toString(added),
                        IMPORT_SKIPPED, Integer.toString(skipped),
                        IMPORT_FAILED, Integer.toString(failed)));
    }

    @PostMapping("/{id}/members/{userId}/roles")
    public List<OrganizationMemberResponse> addRole(@PathVariable long id,
                                                    @PathVariable long userId,
                                                    @RequestBody OrganizationMemberRoleRequest request,
                                                    HttpServletRequest servletRequest) {
        commandService.addRole(OrganizationId.of(id), UserId.of(userId),
                parseRole(request.role()), currentUser(servletRequest));
        return members(id, servletRequest);
    }

    @DeleteMapping("/{id}/members/{userId}/roles/{role}")
    public List<OrganizationMemberResponse> removeRole(@PathVariable long id,
                                                       @PathVariable long userId,
                                                       @PathVariable String role,
                                                       HttpServletRequest request) {
        commandService.removeRole(OrganizationId.of(id), UserId.of(userId),
                parseRole(role), currentUser(request));
        return members(id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public List<OrganizationMemberResponse> removeMember(@PathVariable long id,
                                                         @PathVariable long userId,
                                                         HttpServletRequest request) {
        commandService.removeMember(OrganizationId.of(id), UserId.of(userId),
                currentUser(request));
        return members(id, request);
    }

    @PostMapping("/{id}/members/{userId}/reset-password")
    public ResetOrganizationMemberPasswordResponse resetMemberPassword(
            @PathVariable long id,
            @PathVariable long userId,
            HttpServletRequest request) {
        String initialPassword = commandService.resetMemberPassword(
                OrganizationId.of(id), UserId.of(userId), currentUser(request));
        return new ResetOrganizationMemberPasswordResponse(initialPassword, true);
    }

    @GetMapping("/{id}/invitations")
    public List<OrganizationInvitationResponse> invitations(@PathVariable long id,
                                                            HttpServletRequest request) {
        return queryService.invitations(OrganizationId.of(id), currentUser(request)).stream()
                .map(view -> OrganizationInvitationResponse.from(view.invitation()))
                .toList();
    }

    @PostMapping("/{id}/invitations")
    public CreatedOrganizationInvitationResponse invite(
            @PathVariable long id,
            @RequestBody InviteOrganizationMemberRequest request,
            HttpServletRequest servletRequest) {
        return CreatedOrganizationInvitationResponse.from(commandService.invite(
                OrganizationId.of(id),
                new InviteOrganizationMemberCommand(request.email(), parseRole(request.role())),
                currentUser(servletRequest)));
    }

    @PostMapping("/{id}/accounts")
    public CreatedOrganizationAccountResponse createAccount(
            @PathVariable long id,
            @RequestBody CreateOrganizationAccountRequest request,
            HttpServletRequest servletRequest) {
        OrganizationRole role = parseRole(request.role());
        return CreatedOrganizationAccountResponse.from(
                commandService.createAccount(
                        OrganizationId.of(id),
                        new CreateOrganizationAccountCommand(
                                request.email(), request.displayName(), role),
                        currentUser(servletRequest)),
                role);
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    public List<OrganizationInvitationResponse> revokeInvitation(
            @PathVariable long id,
            @PathVariable long invitationId,
            HttpServletRequest request) {
        commandService.revokeInvitation(
                OrganizationId.of(id), invitationId, currentUser(request));
        return invitations(id, request);
    }

    @GetMapping("/{id}/projects")
    public List<ProjectResponse> projects(@PathVariable long id,
                                          HttpServletRequest request) {
        return queryService.projects(OrganizationId.of(id), currentUser(request)).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{id}/audit")
    public List<OrganizationAuditResponse> audit(@PathVariable long id,
                                                 @RequestParam(defaultValue = "50") int limit,
                                                 HttpServletRequest request) {
        return queryService.audit(OrganizationId.of(id), currentUser(request), limit).stream()
                .map(OrganizationAuditResponse::from)
                .toList();
    }

    @GetMapping("/{id}/permissions")
    public PermissionsResponse permissions(@PathVariable long id,
                                           HttpServletRequest request) {
        var roles = accessService.requireMember(OrganizationId.of(id), currentUser(request));
        return new PermissionsResponse(
                roles.stream().map(OrganizationRole::name).toList(),
                roles.stream().anyMatch(OrganizationRole::canManageOrganization),
                roles.stream().anyMatch(OrganizationRole::canManageProjects));
    }

    private static OrganizationRole parseRole(String value) {
        try {
            return OrganizationRole.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的组织角色：" + value);
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] content, String filename) {
        return excelResponse(content, filename, Map.of());
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] content,
                                                        String filename,
                                                        Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(OrganizationMemberExcel.CONTENT_TYPE));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore());
        extraHeaders.forEach(headers::set);
        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    public record PermissionsResponse(
            List<String> roles,
            boolean canManageOrganization,
            boolean canManageProjects) {
    }
}
