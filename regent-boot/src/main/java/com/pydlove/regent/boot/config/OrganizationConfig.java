package com.pydlove.regent.boot.config;

import com.pydlove.regent.identity.application.command.UserProvisioningService;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.identity.domain.port.TemporaryPasswordGenerator;
import com.pydlove.regent.organization.application.command.OrganizationCommandService;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.application.query.OrganizationQueryService;
import com.pydlove.regent.organization.domain.port.OrganizationInvitationEmailSender;
import com.pydlove.regent.organization.domain.port.OrganizationInvitationTokenCodec;
import com.pydlove.regent.organization.domain.repository.OrganizationAuditRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationInvitationRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationRepository;
import com.pydlove.regent.organization.infrastructure.security.SecureOrganizationInvitationTokenCodec;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class OrganizationConfig {

    @Bean
    public OrganizationInvitationTokenCodec organizationInvitationTokenCodec() {
        return new SecureOrganizationInvitationTokenCodec();
    }

    @Bean
    public OrganizationAccessService organizationAccessService(
            OrganizationMemberRepository memberRepository) {
        return new OrganizationAccessService(memberRepository);
    }

    @Bean
    public OrganizationCommandService organizationCommandService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            OrganizationInvitationRepository invitationRepository,
            OrganizationAuditRepository auditRepository,
            OrganizationInvitationTokenCodec tokenCodec,
            OrganizationInvitationEmailSender invitationEmailSender,
            OrganizationAccessService accessService,
            UserRepository userRepository,
            UserProvisioningService userProvisioningService,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            TransactionRunner transactionRunner,
            @Value("${regent.organization.invitation-ttl:P7D}") Duration invitationTtl,
            @Value("${regent.organization.invitation-url:https://ichuang.top/regent}")
            String invitationUrl) {
        return new OrganizationCommandService(
                organizationRepository,
                memberRepository,
                invitationRepository,
                auditRepository,
                tokenCodec,
                invitationEmailSender,
                accessService,
                userRepository,
                userProvisioningService,
                temporaryPasswordGenerator,
                transactionRunner,
                Clock.systemUTC(),
                invitationTtl,
                invitationUrl);
    }

    @Bean
    public OrganizationQueryService organizationQueryService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            OrganizationInvitationRepository invitationRepository,
            OrganizationAuditRepository auditRepository,
            OrganizationInvitationTokenCodec tokenCodec,
            OrganizationAccessService accessService,
            UserRepository userRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberRepository projectMemberRepository) {
        return new OrganizationQueryService(
                organizationRepository,
                memberRepository,
                invitationRepository,
                auditRepository,
                tokenCodec,
                accessService,
                userRepository,
                projectProfileRepository,
                projectMemberRepository,
                Clock.systemUTC());
    }
}
