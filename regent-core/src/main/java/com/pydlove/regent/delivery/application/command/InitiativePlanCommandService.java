package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativePlan;
import com.pydlove.regent.delivery.domain.model.InitiativePlanItem;
import com.pydlove.regent.delivery.domain.repository.InitiativePlanRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.ArrayList;
import java.util.List;

/** 项目负责人维护版本计划与工作量估算。 */
public class InitiativePlanCommandService {

    private final InitiativeRepository initiativeRepository;
    private final InitiativePlanRepository planRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final TransactionRunner transactionRunner;

    public InitiativePlanCommandService(
            InitiativeRepository initiativeRepository,
            InitiativePlanRepository planRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            TransactionRunner transactionRunner) {
        this.initiativeRepository = initiativeRepository;
        this.planRepository = planRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.memberAccessService = memberAccessService;
        this.transactionRunner = transactionRunner;
    }

    public InitiativePlan save(
            InitiativeId initiativeId,
            SaveInitiativePlanCommand command,
            UserId actorId) {
        Initiative initiative = initiativeRepository.findById(initiativeId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "立项不存在：" + initiativeId.value()));
        projectProfileRepository.findById(initiative.getProjectId(), actorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + initiative.getProjectId().value()));
        memberAccessService.requireRole(
                initiative.getProjectId(), actorId, ProjectMemberRole.OWNER);
        initiative.requirePlanEditable();

        List<InitiativePlanItem> items = new ArrayList<>();
        for (SaveInitiativePlanCommand.Item item : command.items()) {
            if (item == null) continue;
            items.add(new InitiativePlanItem(
                    item.name(),
                    item.roleCode(),
                    item.startDate(),
                    item.endDate(),
                    item.headcount()));
        }
        InitiativePlan plan = InitiativePlan.create(
                initiativeId,
                command.planStartDate(),
                command.planEndDate(),
                command.milestones(),
                items,
                actorId);
        return transactionRunner.inTransaction(() -> {
            planRepository.save(plan);
            return plan;
        });
    }
}
