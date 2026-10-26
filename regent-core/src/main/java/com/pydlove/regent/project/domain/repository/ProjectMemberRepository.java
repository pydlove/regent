package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;

import java.util.List;

public interface ProjectMemberRepository {

    List<ProjectMember> findByProject(ProjectId projectId);

    List<ProjectMemberRole> findRoles(ProjectId projectId, UserId userId);

    boolean exists(ProjectId projectId, UserId userId, ProjectMemberRole role);

    int countDistinctUsers(ProjectId projectId);

    ProjectMember save(ProjectMember member);

    void delete(ProjectId projectId, UserId userId, ProjectMemberRole role);

    void ensure(ProjectId projectId, UserId userId, ProjectMemberRole role);
}
