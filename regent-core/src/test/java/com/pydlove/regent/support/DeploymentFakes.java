package com.pydlove.regent.support;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.port.DeploymentExecutor;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogSink;
import com.pydlove.regent.deployment.domain.port.DeploymentLogStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogWriter;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 部署上下文的内存替身。 */
public final class DeploymentFakes {

    private DeploymentFakes() {
    }

    public static final class Deployments implements DeploymentRepository {

        private final Map<Long, Deployment> store = new LinkedHashMap<>();
        private long sequence;

        public Deployment put(Deployment deployment) {
            if (deployment.getId() == null) {
                deployment.assignIdentity(DeploymentId.of(++sequence));
            }
            store.put(deployment.getId().value(), deployment);
            return deployment;
        }

        @Override
        public Optional<Deployment> findById(DeploymentId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<Deployment> findByIdForUpdate(DeploymentId id) {
            return findById(id);
        }

        @Override
        public List<Deployment> findAll(ProjectId projectId) {
            return store.values().stream()
                    .filter(deployment -> projectId == null
                            || deployment.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public boolean existsByName(ProjectId projectId, String name, DeploymentId excludingId) {
            return store.values().stream()
                    .anyMatch(deployment -> deployment.getProjectId().equals(projectId)
                            && deployment.getName().equals(name)
                            && (excludingId == null || !deployment.getId().equals(excludingId)));
        }

        @Override
        public List<DeploymentId> findIdsByServerId(DeploymentServerId serverId) {
            return store.values().stream()
                    .filter(deployment -> deployment.getServerId().equals(serverId))
                    .map(Deployment::getId)
                    .toList();
        }

        @Override
        public int countByServerId(DeploymentServerId serverId) {
            return findIdsByServerId(serverId).size();
        }

        @Override
        public void save(Deployment deployment) {
            put(deployment);
        }

        @Override
        public void delete(DeploymentId id) {
            store.remove(id.value());
        }
    }

    public static final class Runs implements DeploymentRunRepository {

        private final Map<Long, DeploymentRun> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<DeploymentRun> findById(DeploymentRunId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public List<DeploymentRun> findByDeployment(DeploymentId deploymentId, int limit) {
            return store.values().stream()
                    .filter(run -> run.getDeploymentId().equals(deploymentId))
                    .sorted((left, right) -> Long.compare(right.getId().value(), left.getId().value()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<DeploymentRun> findLatestByDeploymentIds(List<DeploymentId> deploymentIds) {
            List<DeploymentRun> latest = new ArrayList<>();
            for (DeploymentId deploymentId : deploymentIds) {
                store.values().stream()
                        .filter(run -> run.getDeploymentId().equals(deploymentId))
                        .max((left, right) -> Long.compare(left.getId().value(), right.getId().value()))
                        .ifPresent(latest::add);
            }
            return latest;
        }

        @Override
        public List<DeploymentRun> findActive() {
            return store.values().stream()
                    .filter(run -> run.getStatus().isActive())
                    .toList();
        }

        @Override
        public boolean existsActiveByDeployment(DeploymentId deploymentId) {
            return store.values().stream()
                    .anyMatch(run -> run.getDeploymentId().equals(deploymentId)
                            && run.getStatus().isActive());
        }

        @Override
        public void save(DeploymentRun run) {
            if (run.getId() == null) {
                run.assignIdentity(DeploymentRunId.of(++sequence));
            }
            store.put(run.getId().value(), run);
        }

        public List<DeploymentRun> all() {
            return List.copyOf(store.values());
        }
    }

    public static final class Servers implements DeploymentServerRepository {

        private final Map<Long, DeploymentServer> store = new LinkedHashMap<>();
        private long sequence;

        public DeploymentServer put(DeploymentServer server) {
            if (server.getId() == null) {
                server.assignIdentity(DeploymentServerId.of(++sequence));
            }
            store.put(server.getId().value(), server);
            return server;
        }

        @Override
        public Optional<DeploymentServer> findById(DeploymentServerId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<DeploymentServer> findByIdForUpdate(DeploymentServerId id) {
            return findById(id);
        }

        @Override
        public List<DeploymentServer> findAll(ProjectId projectId) {
            return store.values().stream()
                    .filter(server -> projectId == null || server.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public boolean existsByName(ProjectId projectId,
                                    String name,
                                    DeploymentServerId excludingId) {
            return store.values().stream()
                    .anyMatch(server -> server.getProjectId().equals(projectId)
                            && server.getName().equals(name)
                            && (excludingId == null || !server.getId().equals(excludingId)));
        }

        @Override
        public void save(DeploymentServer server) {
            put(server);
        }

        @Override
        public void delete(DeploymentServerId id) {
            store.remove(id.value());
        }
    }

    public static final class Credentials implements DeploymentServerCredentialStore {

        private final Map<Long, String> store = new LinkedHashMap<>();

        @Override
        public void save(DeploymentServerId serverId, String password) {
            store.put(serverId.value(), password);
        }

        @Override
        public boolean exists(DeploymentServerId serverId) {
            return store.containsKey(serverId.value());
        }

        @Override
        public Optional<String> find(DeploymentServerId serverId) {
            return Optional.ofNullable(store.get(serverId.value()));
        }

        @Override
        public void delete(DeploymentServerId serverId) {
            store.remove(serverId.value());
        }

        public String password(DeploymentServerId serverId) {
            return store.get(serverId.value());
        }
    }

    public static final class Logs implements DeploymentLogStore {

        private final Map<Long, StringBuilder> store = new LinkedHashMap<>();

        @Override
        public DeploymentLogWriter open(DeploymentRunId runId) {
            StringBuilder content = new StringBuilder();
            store.put(runId.value(), content);
            return new DeploymentLogWriter() {
                @Override
                public void append(String text) {
                    content.append(text);
                }

                @Override
                public void close() {
                    // 内存替身无需关闭资源
                }
            };
        }

        @Override
        public String read(DeploymentRunId runId) {
            StringBuilder content = store.get(runId.value());
            return content == null ? "" : content.toString();
        }

        @Override
        public void delete(DeploymentRunId runId) {
            store.remove(runId.value());
        }
    }

    public static final class ScriptedExecutor implements DeploymentExecutor {

        private final List<DeploymentExecutionRequest> requests = new ArrayList<>();
        private DeploymentExecutionResult result = new DeploymentExecutionResult(
                DeploymentRunStatus.SUCCESS, 0, "部署完成");

        public void result(DeploymentExecutionResult result) {
            this.result = result;
        }

        public List<DeploymentExecutionRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public DeploymentExecutionResult execute(DeploymentExecutionRequest request,
                                                 DeploymentLogSink sink) {
            requests.add(request);
            sink.append("fake output\n");
            return result;
        }
    }
}
