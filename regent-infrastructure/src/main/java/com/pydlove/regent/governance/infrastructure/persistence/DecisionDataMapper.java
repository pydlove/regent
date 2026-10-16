package com.pydlove.regent.governance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.DecisionSnapshot;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.governance.domain.model.ResolvedBy;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * 决策单的领域对象 ↔ 数据对象转换。
 *
 * <p>这里唯一有点分量的是 {@code options}：MySQL 里是 JSON 列，领域层里是
 * {@code List<String>}。转换必须失败关闭 —— 读到一段坏 JSON 时抛异常，而不是当成空列表，
 * 否则「选项丢了」会表现为「审批弹框里一个选项都没有」，没人会想到去查库里的脏数据。
 */
public final class DecisionDataMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private DecisionDataMapper() {
    }

    public static DecisionData toData(Decision decision) {
        DecisionData data = new DecisionData();
        data.setId(decision.getId() == null ? null : decision.getId().value());
        data.setTaskId(decision.getTaskId().value());
        data.setProjectId(decision.getProjectId().value());
        data.setAiLevel(decision.getAiLevel().name());
        data.setEffectiveLevel(decision.getEffectiveLevel().name());
        data.setMatchedRuleId(decision.getMatchedRuleId().orElse(null));
        data.setStatus(decision.getStatus().name());
        data.setQuestion(decision.getQuestion());
        data.setOptions(toJson(decision.getOptions()));
        data.setRecommendation(decision.getRecommendation().orElse(null));
        data.setReversible(decision.isReversible());
        data.setBlastRadius(decision.getBlastRadius().orElse(null));
        data.setResolvedBy(decision.getResolvedBy().map(Enum::name).orElse(null));
        data.setResolvedChoice(decision.getResolvedChoice().orElse(null));
        data.setResolutionNote(decision.getResolutionNote().orElse(null));
        data.setRaisedAt(toDb(decision.getRaisedAt()));
        data.setResolvedAt(toDb(decision.getResolvedAt().orElse(null)));
        data.setCreatedAt(toDb(decision.getCreatedAt()));
        data.setUpdatedAt(toDb(decision.getUpdatedAt()));
        return data;
    }

    public static Decision toDomain(DecisionData data) {
        return Decision.restore(new DecisionSnapshot(
                DecisionId.of(data.getId()),
                TaskId.of(data.getTaskId()),
                ProjectId.of(data.getProjectId()),
                DecisionLevel.parse(data.getAiLevel()),
                DecisionLevel.parse(data.getEffectiveLevel()),
                data.getMatchedRuleId(),
                DecisionStatus.parse(data.getStatus()),
                data.getQuestion(),
                fromJson(data.getOptions()),
                data.getRecommendation(),
                Boolean.TRUE.equals(data.getReversible()),
                data.getBlastRadius(),
                Optional.ofNullable(data.getResolvedBy()).map(ResolvedBy::parse).orElse(null),
                data.getResolvedChoice(),
                data.getResolutionNote(),
                fromDb(data.getRaisedAt()),
                fromDb(data.getResolvedAt()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt())));
    }

    private static String toJson(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new DomainException("决策选项无法序列化", e);
        }
    }

    private static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, OPTIONS_TYPE);
        } catch (JsonProcessingException e) {
            throw new DomainException("decision.options 不是合法的 JSON 数组：" + json, e);
        }
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
