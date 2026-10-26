package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativePlan;
import com.pydlove.regent.delivery.domain.model.InitiativePlanItem;
import com.pydlove.regent.delivery.domain.repository.InitiativePlanRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 版本计划的 MyBatis 实现。 */
@Repository
public class MyBatisInitiativePlanRepository implements InitiativePlanRepository {

    private final InitiativePlanMapper mapper;

    public MyBatisInitiativePlanRepository(InitiativePlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<InitiativePlan> findByInitiative(InitiativeId initiativeId) {
        InitiativePlanData data = mapper.selectPlan(initiativeId.value());
        if (data == null) return Optional.empty();
        List<InitiativePlanItem> items = mapper.selectItems(initiativeId.value()).stream()
                .map(item -> new InitiativePlanItem(
                        item.getName(),
                        item.getRoleCode(),
                        item.getStartDate(),
                        item.getEndDate(),
                        item.getHeadcount()))
                .toList();
        return Optional.of(new InitiativePlan(
                initiativeId,
                data.getPlanStartDate(),
                data.getPlanEndDate(),
                data.getMilestones(),
                items,
                UserId.of(data.getUpdatedBy()),
                data.getUpdatedAt().toInstant(ZoneOffset.UTC)));
    }

    @Override
    public void save(InitiativePlan plan) {
        InitiativePlanData data = new InitiativePlanData();
        data.setInitiativeId(plan.initiativeId().value());
        data.setPlanStartDate(plan.planStartDate());
        data.setPlanEndDate(plan.planEndDate());
        data.setMilestones(plan.milestones());
        data.setUpdatedBy(plan.updatedBy().value());
        data.setUpdatedAt(db(plan.updatedAt()));
        if (mapper.upsertPlan(data) < 1) {
            throw new DomainException("版本计划保存失败");
        }
        mapper.deleteItems(plan.initiativeId().value());
        int order = 1;
        for (InitiativePlanItem item : plan.items()) {
            InitiativePlanItemData itemData = new InitiativePlanItemData();
            itemData.setInitiativeId(plan.initiativeId().value());
            itemData.setName(item.name());
            itemData.setRoleCode(item.roleCode());
            itemData.setStartDate(item.startDate());
            itemData.setEndDate(item.endDate());
            itemData.setHeadcount(item.headcount());
            itemData.setSortOrder(order++);
            itemData.setCreatedAt(db(plan.updatedAt()));
            if (mapper.insertItem(itemData) < 1) {
                throw new DomainException("版本计划工作项保存失败：" + item.name());
            }
        }
    }

    private static LocalDateTime db(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
