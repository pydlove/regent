package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.DailyCompletionCount;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 看板趋势读侧测试。
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

        List<DailyCompletionCount> trend = service.completionTrend(5);

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

        service.completionTrend(7);

        assertEquals(LocalDate.now(ZONE).minusDays(6), repository.from);
        assertEquals(LocalDate.now(ZONE), repository.to);
        assertEquals(ZoneOffset.ofHours(8), repository.offset);
    }

    @Test
    @DisplayName("天数越界直接拒绝，不去查库")
    void rejectsOutOfRangeDays() {
        Recording repository = new Recording(List.of());
        TaskQueryService service = new TaskQueryService(new ExecutionFakes.Tasks(), repository, ZONE);

        assertThrows(IllegalArgumentException.class, () -> service.completionTrend(0));
        assertThrows(IllegalArgumentException.class, () -> service.completionTrend(31));
        assertEquals(null, repository.from);
    }

    /** 只实现趋势查询，其它方法一律当测试写错处理。 */
    private static final class Recording implements TaskQueryRepository {

        private final List<DailyCompletionCount> rows;
        private LocalDate from;
        private LocalDate to;
        private ZoneOffset offset;

        private Recording(List<DailyCompletionCount> rows) {
            this.rows = rows;
        }

        @Override
        public TaskListItemPage findPage(TaskQuery query) {
            throw new UnsupportedOperationException("本测试不涉及列表查询");
        }

        @Override
        public Map<TaskStatus, Long> countGroupByStatus() {
            throw new UnsupportedOperationException("本测试不涉及状态分布");
        }

        @Override
        public List<DailyCompletionCount> countCompletionPerDay(LocalDate from, LocalDate to, ZoneOffset offset) {
            this.from = from;
            this.to = to;
            this.offset = offset;
            return rows;
        }
    }
}
