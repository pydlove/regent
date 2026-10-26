package com.pydlove.regent.boot.config;

import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.project.application.command.ProjectMemberCommandService;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.application.query.ProjectMemberQueryService;
import com.pydlove.regent.project.application.query.ProjectMembershipGuard;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectMemberConfig {

    @Bean
    public ProjectMemberAccessService projectMemberAccessService(
            ProjectMemberRepository memberRepository) {
        return new ProjectMemberAccessService(memberRepository);
    }

    @Bean
    public ProjectMemberQueryService projectMemberQueryService(
            ProjectMemberRepository memberRepository,
            ProjectProfileRepository projectProfileRepository,
            OrganizationMemberRepository organizationMemberRepository,
            ProjectMembershipGuard projectMembershipGuard,
            UserRepository userRepository) {
        return new ProjectMemberQueryService(
                memberRepository, projectProfileRepository, organizationMemberRepository,
                projectMembershipGuard, userRepository);
    }

    @Bean
    public ProjectMembershipGuard projectMembershipGuard(
            ProjectMemberRepository memberRepository,
            ProjectProfileRepository projectProfileRepository,
            OrganizationAccessService organizationAccessService) {
        return new ProjectMembershipGuard(
                memberRepository, projectProfileRepository, organizationAccessService);
    }

    @Bean
    public ProjectMemberCommandService projectMemberCommandService(
            ProjectMemberRepository memberRepository,
            ProjectMembershipGuard projectMembershipGuard,
            OrganizationAccessService organizationAccessService,
            UserRepository userRepository,
            TransactionRunner transactionRunner) {
        return new ProjectMemberCommandService(
                memberRepository, projectMembershipGuard, organizationAccessService,
                userRepository, transactionRunner);
    }
}
