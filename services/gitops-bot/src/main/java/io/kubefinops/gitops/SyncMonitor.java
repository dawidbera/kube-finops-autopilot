package io.kubefinops.gitops;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kubefinops.event.ChangeAppliedEvent;
import io.kubefinops.event.RecommendationApprovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class SyncMonitor {

    private KubernetesClient kubernetesClient;
    private final StreamBridge streamBridge;
    private final Map<String, RecommendationApprovedEvent> pendingVerifications = new ConcurrentHashMap<>();

    public SyncMonitor(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
        try {
            this.kubernetesClient = new KubernetesClientBuilder().build();
        } catch (Exception e) {
            log.warn("Kubernetes client could not be initialized. Sync monitoring will be disabled. Error: {}", e.getMessage());
        }
    }

    @Bean
    public Consumer<RecommendationApprovedEvent> monitorSync() {
        return event -> {
            if (kubernetesClient == null) {
                log.warn("Skipping sync monitoring for {} - Kubernetes client not available", event.getRecommendationId());
                return;
            }
            log.info("Started monitoring sync for recommendation: {}", event.getRecommendationId());
            pendingVerifications.put(event.getRecommendationId(), event);
        };
    }

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void verifyAppliedChanges() {
        if (kubernetesClient == null || pendingVerifications.isEmpty()) return;

        log.debug("Verifying {} pending changes in cluster...", pendingVerifications.size());
        
        pendingVerifications.values().forEach(event -> {
            try {
                String[] ref = event.getWorkloadRef().split("/");
                String name = ref[1];
                
                Deployment deployment = kubernetesClient.apps().deployments()
                        .inNamespace(event.getNamespace())
                        .withName(name)
                        .get();

                if (deployment != null && isSynchronized(deployment, event)) {
                    log.info("✅ SUCCESS: Change for {} applied successfully in k3s!", event.getWorkloadRef());
                    
                    ChangeAppliedEvent appliedEvent = ChangeAppliedEvent.builder()
                            .recommendationId(event.getRecommendationId())
                            .workloadRef(event.getWorkloadRef())
                            .namespace(event.getNamespace())
                            .appliedResources(event.getApprovedResources())
                            .replicas(event.getReplicas())
                            .appliedAt(Instant.now())
                            .build();

                    streamBridge.send("changeApplied-out-0", appliedEvent);
                    pendingVerifications.remove(event.getRecommendationId());
                }
            } catch (Exception e) {
                log.error("Error during cluster sync verification for {}", event.getRecommendationId(), e);
            }
        });
    }

    private boolean isSynchronized(Deployment deployment, RecommendationApprovedEvent event) {
        // 1. Check replicas
        if (event.getReplicas() != null) {
            Integer actualReplicas = deployment.getSpec().getReplicas();
            if (!event.getReplicas().equals(actualReplicas)) return false;
        }

        // 2. Check resources (simplified check)
        if (event.getApprovedResources() != null && !event.getApprovedResources().isEmpty()) {
            var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
            var requests = container.getResources().getRequests();
            
            if (event.getApprovedResources().containsKey("cpu")) {
                String approvedCpu = event.getApprovedResources().get("cpu");
                String actualCpu = requests.get("cpu").getAmount();
                if (!approvedCpu.equals(actualCpu)) return false;
            }
        }

        return true;
    }
}
