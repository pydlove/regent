package com.pydlove.regent.relay;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public final class RelayMain {
    private RelayMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            printUsage();
            System.exit(2);
        }
        switch (args[0]) {
            case "server" -> runServer();
            case "connector" -> runConnector();
            default -> {
                printUsage();
                System.exit(2);
            }
        }
    }

    private static void runServer() throws Exception {
        RelayServerConfig config = new RelayServerConfig(
            env("REGENT_RELAY_BIND", "127.0.0.1"),
            intEnv("REGENT_RELAY_PORT", 8788),
            requiredEnv("REGENT_RELAY_SHARED_SECRET"),
            Duration.ofSeconds(intEnv("REGENT_RELAY_POLL_SECONDS", 25)),
            Duration.ofSeconds(intEnv("REGENT_RELAY_REQUEST_TIMEOUT_SECONDS", 40)),
            Duration.ofSeconds(intEnv("REGENT_RELAY_OFFLINE_SECONDS", 45)),
            intEnv("REGENT_RELAY_QUEUE_CAPACITY", 64),
            intEnv("REGENT_RELAY_MAX_BODY_BYTES", 32 * 1024 * 1024)
        );
        RelayHttpServer server = new RelayHttpServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "regent-relay-shutdown"));
        server.start();
        System.out.printf("[relay] 已监听 %s:%d%n", config.bindAddress(), server.port());
        new CountDownLatch(1).await();
    }

    private static void runConnector() throws Exception {
        RelayConnectorConfig config = new RelayConnectorConfig(
            URI.create(requiredEnv("REGENT_RELAY_SERVER_URL")),
            URI.create(env("REGENT_RELAY_LOCAL_URL", "http://127.0.0.1:8787")),
            requiredEnv("REGENT_RELAY_SHARED_SECRET"),
            intEnv("REGENT_RELAY_CONNECTIONS", 4),
            Duration.ofSeconds(intEnv("REGENT_RELAY_POLL_SECONDS", 25)),
            Duration.ofSeconds(intEnv("REGENT_RELAY_REQUEST_TIMEOUT_SECONDS", 40))
        );
        RelayConnector connector = new RelayConnector(config);
        Runtime.getRuntime().addShutdownHook(new Thread(connector::close, "regent-relay-connector-shutdown"));
        connector.start();
        System.out.printf("[relay-connector] 已连接 %s，出口 %s%n",
            config.serverBase(), config.localBase());
        new CountDownLatch(1).await();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少环境变量 " + name);
        }
        return value.trim();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " 必须是整数", exception);
        }
    }

    private static void printUsage() {
        System.err.println("用法：java -jar regent-relay.jar server|connector");
    }
}
