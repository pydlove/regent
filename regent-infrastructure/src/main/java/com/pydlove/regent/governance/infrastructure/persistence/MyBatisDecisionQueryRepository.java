package com.pydlove.regent.governance.infrastructure.persistence;

import com.pydlove.regent.governance.domain.repository.DecisionPage;
import com.pydlove.regent.governance.domain.repository.DecisionQuery;
import com.pydlove.regent.governance.domain.repository.DecisionQueryRepository;
import com.pydlove.regent.governance.domain.repository.DecisionView;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 决策单查询仓储的 MyBatis 实现。 */
@Repository
public class MyBatisDecisionQueryRepository implements DecisionQueryRepository {

    private final DecisionQueryMapper decisionQueryMapper;

    public MyBatisDecisionQueryRepository(DecisionQueryMapper decisionQueryMapper) {
        this.decisionQueryMapper = decisionQueryMapper;
    }

    @Override
    public DecisionPage findPage(DecisionQuery query) {
        String level = query.level() == null ? null : query.level().name();
        String scope = query.scope().name();
        String keyword = escapeLike(query.keyword());
        long total = decisionQueryMapper.countBy(level, scope, keyword);
        if (total == 0) {
            return new DecisionPage(List.of(), 0, query.page(), query.size());
        }
        List<DecisionView> items = decisionQueryMapper
                .selectPage(level, scope, keyword, query.size(), query.offset())
                .stream()
                .map(MyBatisDecisionQueryRepository::toView)
                .toList();
        return new DecisionPage(items, total, query.page(), query.size());
    }

    @Override
    public Optional<DecisionView> findById(long id) {
        return Optional.ofNullable(decisionQueryMapper.selectById(id))
                .map(MyBatisDecisionQueryRepository::toView);
    }

    private static DecisionView toView(DecisionRow row) {
        return new DecisionView(
                row.getId(),
                row.getTaskId(),
                row.getTaskTitle(),
                row.getProjectId(),
                row.getProjectName(),
                DecisionLevel.parse(row.getAiLevel()),
                DecisionLevel.parse(row.getEffectiveLevel()),
                row.getMatchedRuleName(),
                row.getStatus(),
                row.getQuestion(),
                DecisionDataMapper.parseOptions(row.getOptions()),
                row.getRecommendation(),
                Boolean.TRUE.equals(row.getReversible()),
                row.getBlastRadius(),
                row.getResolvedBy(),
                row.getResolvedChoice(),
                row.getResolutionNote(),
                fromDb(row.getRaisedAt()),
                fromDb(row.getResolvedAt()));
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
