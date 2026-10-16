package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.AgentReportOutcome;

/** 上报响应：平台怎么判的、接下来干什么。 */
public record AgentReportResponse(String outcome,
                                  String runStatus,
                                  String taskStatus,
                                  String instruction,
                                  Long decisionId,
                                  String decisionLevel,
                                  String note,
                                  int roundsLeft) {

    public static AgentReportResponse from(AgentReportOutcome outcome) {
        return new AgentReportResponse(outcome.outcome().name(), outcome.runStatus(), outcome.taskStatus(),
                outcome.instruction(), outcome.decisionId(), outcome.decisionLevel(), outcome.note(),
                outcome.roundsLeft());
    }
}
