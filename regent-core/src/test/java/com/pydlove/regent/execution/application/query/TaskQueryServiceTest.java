package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.DailyCompletionCount;
import com.pydlove.regent.execution.domain.repository.DailyTokenUsage;
import com.pydlove.regent.execution.domain.repository.HourlyTokenUsage;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工作台趋势读侧测试。
 *
 * <p>守住两件事：日期必须逐日连续（前端拿到就能画，不用判空档），以及切天用的是调用方时区
 * —— 这两点一旦破了，柱状图会静默画歪，界面上看不出错。
 */
class TaskQueryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    @DisplayName("趋势按天补齐，没有数据的日子是 0")
    void fillsMissingDaysWithZero() {
        LocalDate today = LocalDate.now(ZONE);
        Recording repository = new Recording(List.of(new DailyCompletionCount(today.minusDays(2), 3L, 1L)));
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        List<DailyCompletionCount> trend = service.completionTrend(null, 5);

        assertEquals(5, trend.size());
        assertEquals(today.minusDays(4), trend.get(0).day());
        assertEquals(today, trend.get(4).day());
        assertEquals(new DailyCompletionCount(today.minusDays(2), 3L, 1L), trend.get(2));
        assertEquals(0L, trend.get(0).done());
        assertEquals(0L, trend.get(4).failed());
    }

    @Test
    @DisplayName("趋势窗口含今天，且按给定时区切天")
    void queriesWindowInGivenZone() {
        Recording repository = new Recording(List.of());
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        service.completionTrend(null, 7);

        assertEquals(LocalDate.now(ZONE).minusDays(6), repository.from);
        assertEquals(LocalDate.now(ZONE), repository.to);
        assertEquals(ZoneOffset.ofHours(8), repository.offset);
    }

    @Test
    @DisplayName("天数越界直接拒绝，不去查库")
    void rejectsOutOfRangeDays() {
        Recording repository = new Recording(List.of());
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        assertThrows(IllegalArgumentException.class, () -> service.completionTrend(null, 0));
        assertThrows(IllegalArgumentException.class, () -> service.completionTrend(null, 31));
        assertEquals(null, repository.from);
    }

    @Test
    @DisplayName("Token 汇总直接采用查询仓储的全平台口径")
    void returnsTokenUsageFromRepository() {
        TokenUsage usage = TokenUsage.of(1200, 300, 500);
        Recording repository = new Recording(List.of());
        repository.usage = usage;
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        assertEquals(usage, service.tokenUsage(null));
    }

    @Test
    @DisplayName("Token 明细按范围补齐自然日")
    void fillsMissingTokenDays() {
        LocalDate today = LocalDate.now(ZONE);
        Recording repository = new Recording(List.of());
        repository.dailyTokenUsages = List.of(new DailyTokenUsage(
                today.minusDays(1), TokenUsage.of(100, 40, 20), 3));
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        List<DailyTokenUsage> points = service.tokenUsageByDay(null, TokenUsageRange.LAST_7_DAYS);

        assertEquals(7, points.size());
        assertEquals(today.minusDays(6), points.get(0).day());
        assertEquals(today, points.get(6).day());
        assertEquals(3, points.get(5).runCount());
        assertEquals(TokenUsage.of(100, 40, 20), points.get(5).usage());
    }

    @Test
    @DisplayName("近 24 小时使用精确时间窗口，全部范围不做补零")
    void handlesRollingAndAllRanges() {
        Recording repository = new Recording(List.of());
        LocalDate oldDay = LocalDate.now(ZONE).minusDays(40);
        repository.dailyTokenUsages = List.of(
                new DailyTokenUsage(oldDay, TokenUsage.of(1, 2, 3), 1));
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        service.tokenUsageByDay(null, TokenUsageRange.LAST_24_HOURS);
        assertEquals(true, repository.tokenFrom.isAfter(Instant.now().minusSeconds(86_405)));
        assertEquals(2, service.tokenUsageByDay(null, TokenUsageRange.LAST_24_HOURS).size());

        List<DailyTokenUsage> all = service.tokenUsageByDay(null, TokenUsageRange.ALL);
        assertEquals(1, all.size());
        assertEquals(oldDay, all.get(0).day());
    }

    @Test
    @DisplayName("当天 Token 趋势按整点补齐到当前小时")
    void fillsHourlyTokenTrend() {
        LocalDateTime currentHour = LocalDateTime.now(ZONE)
                .withMinute(0).withSecond(0).withNano(0);
        Recording repository = new Recording(List.of());
        repository.hourlyTokenUsages = List.of(new HourlyTokenUsage(
                currentHour, TokenUsage.of(10, 20, 5), 2));
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        List<HourlyTokenUsage> points = service.tokenUsageByHour(null, TokenUsageRange.TODAY);

        assertEquals(currentHour.toLocalDate().atStartOfDay(), points.get(0).bucketStart());
        assertEquals(currentHour, points.get(points.size() - 1).bucketStart());
        assertEquals(2, points.get(points.size() - 1).runCount());
        assertThrows(IllegalArgumentException.class,
                () -> service.tokenUsageByHour(null, TokenUsageRange.ALL));
    }

    @Test
    @DisplayName("项目筛选原样下推到统计仓储")
    void forwardsProjectScopeToRepository() {
        Recording repository = new Recording(List.of());
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);
        ProjectId statsProject = ProjectId.of(7L);
        ProjectId tokenProject = ProjectId.of(8L);
        ProjectId trendProject = ProjectId.of(9L);

        service.statusCounts(statsProject);
        assertEquals(statsProject, repository.projectId);

        service.tokenUsage(tokenProject);
        assertEquals(tokenProject, repository.projectId);

        service.completionTrend(trendProject, 3);
        assertEquals(trendProject, repository.projectId);
    }

    /** 只实现趋势与 token 查询，其它方法一律当测试写错处理。 */
    private static final class Recording implements TaskQueryRepository {

        private final List<DailyCompletionCount> rows;
        private LocalDate from;
        private LocalDate to;
        private ZoneOffset offset;
        private ProjectId projectId;
        private TokenUsage usage = TokenUsage.ZERO;
        private List<DailyTokenUsage> dailyTokenUsages = List.of();
        private List<HourlyTokenUsage> hourlyTokenUsages = List.of();
        private Instant tokenFrom;
        private Instant tokenTo;

        private Recording(List<DailyCompletionCount> rows) {
            this.rows = rows;
        }

        @Override
        public TaskListItemPage findPage(TaskQuery query) {
            throw new UnsupportedOperationException("本测试不涉及列表查询");
        }

        @Override
        public Map<TaskStatus, Long> countGroupByStatus(ProjectId projectId) {
            this.projectId = projectId;
            return Map.of();
        }

        @Override
        public TokenUsage summarizeTokens(ProjectId projectId) {
            this.projectId = projectId;
            return usage;
        }

        @Override
        public List<DailyTokenUsage> summarizeTokensPerDay(ProjectId projectId,
                                                           Instant from,
                                                           Instant to,
                                                           ZoneOffset offset) {
            this.projectId = projectId;
            this.tokenFrom = from;
            this.tokenTo = to;
            this.offset = offset;
            return dailyTokenUsages;
        }

        @Override
        public List<HourlyTokenUsage> summarizeTokensPerHour(ProjectId projectId,
                                                              Instant from,
                                                              Instant to,
                                                              ZoneOffset offset) {
            this.projectId = projectId;
            this.tokenFrom = from;
            this.tokenTo = to;
            this.offset = offset;
            return hourlyTokenUsages;
        }

        @Override
        public List<DailyCompletionCount> countCompletionPerDay(ProjectId projectId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                ZoneOffset offset) {
            this.projectId = projectId;
            this.from = from;
            this.to = to;
            this.offset = offset;
            return rows;
        }
    }
}
