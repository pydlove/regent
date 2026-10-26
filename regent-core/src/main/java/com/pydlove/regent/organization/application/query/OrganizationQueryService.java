package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.organization.domain.model.Organization;
import com.pydlove.regent.organization.domain.model.OrganizationAuditEntry;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationInvitation;
import com.pydlove.regent.organization.domain.model.OrganizationMember;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.port.OrganizationInvitationTokenCodec;
import com.pydlove.regent.organization.domain.repository.OrganizationAuditRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationInvitationRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.application.query.ProjectMemberAssignmentView;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组织管理读侧。普通成员只能在项目中使用，不进入组织管理页。 */
public class OrganizationQueryService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationAuditRepository auditRepository;
    private final OrganizationInvitationTokenCodec tokenCodec;
    private final OrganizationAccessService accessService;
    private final UserRepository userRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final Clock clock;

    public OrganizationQueryService(OrganizationRepository organizationRepository,
                                    OrganizationMemberRepository memberRepository,
                                    OrganizationInvitationRepository invitationRepository,
                                    OrganizationAuditRepository auditRepository,
                                    OrganizationInvitationTokenCodec tokenCodec,
                                    OrganizationAccessService accessService,
                                    UserRepository userRepository,
                                    ProjectProfileRepository projectProfileRepository,
                                    ProjectMemberRepository projectMemberRepository,
                                    Clock clock) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.auditRepository = auditRepository;
        this.tokenCodec = tokenCodec;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.clock = clock;
    }

    public List<OrganizationView> listMine(UserId userId) {
        Map<OrganizationId, List<OrganizationRole>> rolesByOrganization = new LinkedHashMap<>();
        for (OrganizationMember member : memberRepository.findByUser(userId)) {
            rolesByOrganization.computeIfAbsent(member.organizationId(), ignored -> new ArrayList<>())
                    .add(member.role());
        }
        if (rolesByOrganization.isEmpty()) return List.of();
        return organizationRepository.findByIds(List.copyOf(rolesByOrganization.keySet())).stream()
                .map(organization -> view(organization, rolesByOrganization.get(organization.getId())))
                .toList();
    }

    public OrganizationView get(OrganizationId id, UserId userId) {
        List<OrganizationRole> roles = accessService.requireMember(id, userId);
        return view(requireOrganization(id), roles);
    }

    public List<OrganizationMemberView> members(OrganizationId id, UserId actorId) {
        accessService.requireManagerAccess(id, actorId);
        Map<UserId, List<OrganizationRole>> grouped = new LinkedHashMap<>();
        for (OrganizationMember member : memberRepository.findByOrganization(id)) {
            grouped.computeIfAbsent(member.userId(), ignored -> new ArrayList<>()).add(member.role());
        }
        Map<UserId, List<ProjectMemberAssignmentView>> projectAssignments = new LinkedHashMap<>();
        for (ProjectProfile project : projectProfileRepository.findByOrganization(id)) {
            Map<UserId, List<com.pydlove.regent.project.domain.model.ProjectMemberRole>> rolesByUser =
                    new LinkedHashMap<>();
            for (var projectMember : projectMemberRepository.findByProject(project.id())) {
                rolesByUser.computeIfAbsent(projectMember.userId(), ignored -> new ArrayList<>())
                        .add(projectMember.role());
            }
            rolesByUser.forEach((userId, roles) ->
                    projectAssignments.computeIfAbsent(userId, ignored -> new ArrayList<>())
                            .add(new ProjectMemberAssignmentView(
                                    project.id(), project.name(), List.copyOf(roles))));
        }
        return grouped.entrySet().stream()
                .map(entry -> new OrganizationMemberView(
                        userRepository.findById(entry.getKey())
                                .orElseThrow(() -> new EntityNotFoundException(
                                        "账号不存在：" + entry.getKey().value())),
                        List.copyOf(entry.getValue()),
                        projectAssignments.getOrDefault(entry.getKey(), List.of())))
                .toList();
    }

    public List<OrganizationInvitationView> invitations(OrganizationId id, UserId actorId) {
        accessService.requireAdmin(id, actorId);
        return invitationRepository.findByOrganization(id).stream()
                .map(OrganizationInvitationView::new)
                .toList();
    }

    public List<ProjectProfile> projects(OrganizationId id, UserId actorId) {
        accessService.requireManagerAccess(id, actorId);
        return projectProfileRepository.findByOrganization(id);
    }

    public List<OrganizationAuditEntry> audit(OrganizationId id, UserId actorId, int limit) {
        accessService.requireAdmin(id, actorId);
        return auditRepository.findRecent(id, Math.max(1, Math.min(limit, 200)));
    }

    public OrganizationInvitationPreview previewInvitation(String rawToken) {
        OrganizationInvitation invitation = requireInvitation(rawToken);
        Organization organization = requireOrganization(invitation.getOrganizationId());
        boolean accountExists = userRepository.findByEmail(invitation.getEmail()).isPresent();
        return new OrganizationInvitationPreview(invitation, organization.getName(), accountExists);
    }

    public OrganizationInvitation requireInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new EntityNotFoundException("邀请不存在或已失效");
        }
        OrganizationInvitation invitation = invitationRepository
                .findByTokenHash(tokenCodec.hash(rawToken))
                .orElseThrow(() -> new EntityNotFoundException("邀请不存在或已失效"));
        invitation.requireUsable(clock.instant());
        return invitation;
    }

    private OrganizationView view(Organization organization, List<OrganizationRole> roles) {
        return new OrganizationView(
                organization,
                List.copyOf(roles),
                memberRepository.countDistinctUsers(organization.getId()),
                projectProfileRepository.countByOrganization(organization.getId()));
    }

    private Organization requireOrganization(OrganizationId id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("组织不存在：" + id.value()));
    }
}
