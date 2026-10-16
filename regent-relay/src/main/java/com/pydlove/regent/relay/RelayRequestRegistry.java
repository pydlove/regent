package com.pydlove.regent.relay;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

final class RelayRequestRegistry {
    private final BlockingQueue<RelayRequest> queue;
    private final Map<String, RelayRequest> pending = new ConcurrentHashMap<>();
    private final Duration connectorOfflineAfter;
    private final Duration requestTimeout;
    private final AtomicLong lastConnectorPollAt = new AtomicLong();

    RelayRequestRegistry(int queueCapacity, Duration connectorOfflineAfter, Duration requestTimeout) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.connectorOfflineAfter = connectorOfflineAfter;
        this.requestTimeout = requestTimeout;
    }

    boolean connectorOnline() {
        long last = lastConnectorPollAt.get();
        return last > 0 && Instant.now().toEpochMilli() - last <= connectorOfflineAfter.toMillis();
    }

    int queueDepth() {
        return queue.size();
    }

    RelayRequest enqueue(
        String method,
        String pathAndQuery,
        Map<String, String> headers,
        byte[] body
    ) {
        if (!connectorOnline()) {
            throw new RelayOfflineException();
        }

        Instant now = Instant.now();
        RelayRequest request = new RelayRequest(
            UUID.randomUUID().toString(),
            method,
            pathAndQuery,
            headers,
            body,
            now,
            now.plus(requestTimeout),
            new CompletableFuture<>()
        );
        pending.put(request.id(), request);
        if (!queue.offer(request)) {
            pending.remove(request.id());
            throw new RelayQueueFullException();
        }
        return request;
    }

    RelayRequest poll(Duration wait) throws InterruptedException {
        lastConnectorPollAt.set(Instant.now().toEpochMilli());
        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return null;
            }
            RelayRequest request = queue.poll(remaining, TimeUnit.NANOSECONDS);
            if (request == null) {
                return null;
            }
            if (!pending.containsKey(request.id()) || !request.expiresAt().isAfter(Instant.now())) {
                pending.remove(request.id());
                continue;
            }
            return request;
        }
    }

    RelayResponse await(RelayRequest request, Duration timeout)
        throws InterruptedException, TimeoutException, java.util.concurrent.ExecutionException {
        try {
            return request.response().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            pending.remove(request.id());
            throw exception;
        }
    }

    boolean complete(String requestId, RelayResponse response) {
        RelayRequest request = pending.remove(requestId);
        if (request == null || request.response().isDone()) {
            return false;
        }
        return request.response().complete(response);
    }

    static final class RelayOfflineException extends RuntimeException {
    }

    static final class RelayQueueFullException extends RuntimeException {
    }
}
