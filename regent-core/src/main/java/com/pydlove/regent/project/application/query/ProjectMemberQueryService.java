package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationMember;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectMemberQueryService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectMembershipGuard membershipGuard;
    private final UserRepository userRepository;

    public ProjectMemberQueryService(ProjectMemberRepository memberRepository,
                                     ProjectProfileRepository projectProfileRepository,
                                     OrganizationMemberRepository organizationMemberRepository,
                                     ProjectMembershipGuard membershipGuard,
                                     UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.membershipGuard = membershipGuard;
        this.userRepository = userRepository;
    }

    public List<ProjectMemberView> list(ProjectId projectId, UserId actorId) {
        membershipGuard.requireReadable(projectId, actorId);
        return memberRepository.findByProject(projectId).stream()
                .map(member -> {
                    var user = userRepository.findById(member.userId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "成员账号不存在：" + member.userId().value()));
                    return new ProjectMemberView(
                            member,
                            user.getUsername().value(),
                            user.getDisplayName().value(),
                            user.getEmail() == null ? null : user.getEmail().value(),
                            user.getAvatar() == null
                                    ? null : user.getAvatar().updatedAt().toEpochMilli());
                })
                .toList();
    }

    public List<ProjectMemberCandidateView> candidates(ProjectId projectId, UserId actorId) {
        var project = membershipGuard.requireManage(projectId, actorId);
        if (project.organizationId() == null) return List.of();
        Map<UserId, List<OrganizationRole>> organizationRoles = new LinkedHashMap<>();
        for (OrganizationMember member : organizationMemberRepository.findByOrganization(
                project.organizationId())) {
            organizationRoles.computeIfAbsent(member.userId(), ignored -> new ArrayList<>())
                    .add(member.role());
        }
        return organizationRoles.entrySet().stream()
                .map(entry -> {
                    var user = userRepository.findById(entry.getKey())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "成员账号不存在：" + entry.getKey().value()));
                    List<ProjectMemberRole> projectRoles = memberRepository
                            .findRoles(projectId, entry.getKey());
                    return new ProjectMemberCandidateView(
                            user, List.copyOf(entry.getValue()), projectRoles);
                })
                .sorted(Comparator.comparing(candidate -> candidate.user().getDisplayName().value()))
                .toList();
    }
}
