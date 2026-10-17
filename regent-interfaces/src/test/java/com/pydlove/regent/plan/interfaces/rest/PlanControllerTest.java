package com.pydlove.regent.plan.interfaces.rest;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plan.application.command.CreatePlanCommand;
import com.pydlove.regent.plan.application.command.PlanCommandService;
import com.pydlove.regent.plan.application.command.PlanExecutionService;
import com.pydlove.regent.plan.application.query.PlanQueryService;
import com.pydlove.regent.plan.application.query.PlanDetailView;
import com.pydlove.regent.plan.application.query.PlanRunDetailView;
import com.pydlove.regent.plan.application.query.PlanRunView;
import com.pydlove.regent.plan.application.query.PlanSummaryView;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.domain.model.PlanRunStatus;
import com.pydlove.regent.plan.domain.model.PlanStatus;
import com.pydlove.regent.plan.domain.model.PlanTaskSnapshot;
import com.pydlove.regent.plan.domain.model.PlanTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanControllerTest {

    private RecordingPlanCommand command;
    private RecordingPlanExecution execution;
    private RecordingPlanQuery query;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        command = new RecordingPlanCommand();
        execution = new RecordingPlanExecution();
        query = new RecordingPlanQuery();
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanController(command, execution, query)).build();
    }

    @Test
    @DisplayName("创建计划把任务 ID 与执行时间传给应用层")
    void 创建计划() throws Exception {
        Plan plan = Plan.schedule("发布计划", List.of(TaskId.of(7)), Instant.parse("2026-10-18T01:00:00Z"));
        plan.assignIdentity(PlanId.of(21));
        command.created = plan;
        query.summary = new PlanSummaryView(
                plan.getId(), plan.getName(), PlanStatus.SCHEDULED, 1,
                plan.getScheduledAt(), null, null, plan.getCreatedAt(), null);

        mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "发布计划",
                                  "taskIds": [7],
                                  "scheduledAt": "2026-10-18T01:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.taskCount").value(1));

        assertEquals(List.of(TaskId.of(7)), command.command.taskIds());
    }

    @Test
    @DisplayName("人工执行计划返回本轮运行摘要")
    void 人工执行计划() throws Exception {
        PlanRun run = PlanRun.start(
                PlanId.of(21), "发布计划", PlanTrigger.MANUAL,
                List.of(new PlanTaskSnapshot(TaskId.of(7), "任务 7")));
        run.assignIdentity(PlanRunId.of(31));
        execution.run = run;
        query.runDetail = new PlanRunDetailView(PlanRunView.from(run), List.of());

        mockMvc.perform(post("/api/plans/{id}/run", 21))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        assertEquals(PlanId.of(21), execution.planId);
    }

    private static final class RecordingPlanCommand extends PlanCommandService {

        private Plan created;
        private CreatePlanCommand command;

        private RecordingPlanCommand() {
            super(null, null);
        }

        @Override
        public Plan create(CreatePlanCommand command) {
            this.command = command;
            return created;
        }
    }

    private static final class RecordingPlanExecution extends PlanExecutionService {

        private PlanRun run;
        private PlanId planId;

        private RecordingPlanExecution() {
            super(null, null, null, null, null, null, Clock.fixed(
                    Instant.parse("2026-10-17T08:00:00Z"), ZoneOffset.UTC));
        }

        @Override
        public PlanRun runNow(PlanId planId) {
            this.planId = planId;
            return run;
        }
    }

    private static final class RecordingPlanQuery extends PlanQueryService {

        private PlanSummaryView summary;
        private PlanRunDetailView runDetail;

        private RecordingPlanQuery() {
            super(null, null, null);
        }

        @Override
        public PlanDetailView get(PlanId ignored) {
            return new PlanDetailView(summary, List.of(), List.of());
        }

        @Override
        public PlanRunDetailView getRun(PlanRunId runId) {
            return runDetail;
        }
    }
}
