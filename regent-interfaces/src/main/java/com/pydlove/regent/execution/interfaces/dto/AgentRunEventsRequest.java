package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.AgentRunEvent;

import java.time.Instant;
import java.util.List;

/** 设备代理批量上报的一次执行事件。 */
public record AgentRunEventsRequest(String executor, String deviceId, List<Event> events) {

    public AgentRunEventsRequest(String executor, List<Event> events) {
        this(executor, null, events);
    }

    public List<AgentRunEvent> toEvents() {
        return events == null ? List.of() : events.stream()
                .filter(java.util.Objects::nonNull)
                .map(event -> new AgentRunEvent(event.type(), event.payload(), event.occurredAt()))
                .toList();
    }

    public record Event(String type, String payload, Instant occurredAt) {
    }
}
