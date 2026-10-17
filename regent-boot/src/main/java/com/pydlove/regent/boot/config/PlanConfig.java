package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.plan.application.command.PlanCommandService;
import com.pydlove.regent.plan.application.command.PlanExecutionService;
import com.pydlove.regent.plan.application.query.PlanQueryService;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.plan.domain.repository.PlanRunRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 计划上下文的显式装配。 */
@Configuration
public class PlanConfig {

    @Bean
    public PlanCommandService planCommandService(PlanRepository planRepository,
                                                 TaskRepository taskRepository) {
        return new PlanCommandService(planRepository, taskRepository);
    }

    @Bean
    public PlanExecutionService planExecutionService(PlanRepository planRepository,
                                                     PlanRunRepository planRunRepository,
                                                     TaskRepository taskRepository,
                                                     TaskRunRepository taskRunRepository,
                                                     TaskDispatchService taskDispatchService,
                                                     TransactionRunner transactionRunner) {
        return new PlanExecutionService(
                planRepository,
                planRunRepository,
                taskRepository,
                taskRunRepository,
                taskDispatchService,
                transactionRunner,
                Clock.systemUTC());
    }

    @Bean
    public PlanQueryService planQueryService(PlanRepository planRepository,
                                             PlanRunRepository planRunRepository,
                                             TaskRepository taskRepository) {
        return new PlanQueryService(planRepository, planRunRepository, taskRepository);
    }
}
