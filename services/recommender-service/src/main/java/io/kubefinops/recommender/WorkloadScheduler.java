package io.kubefinops.recommender;

import io.kubefinops.event.RecommendationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkloadScheduler {

    private final StreamBridge streamBridge;
    private static final String BINDING_NAME = "recommendationCreated-out-0";

    /**
     * Sleep Cycle: Scale down to 0 replicas at 6 PM (Mon-Fri)
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void scheduleSleep() {
        triggerScaling("dev", "nginx", 0, "Nightly Sleep Cycle");
    }

    /**
     * Wake Cycle: Scale up to 1 replica at 8 AM (Mon-Fri)
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void scheduleWake() {
        triggerScaling("dev", "nginx", 1, "Morning Wake Cycle");
    }

    private void triggerScaling(String namespace, String deployment, Integer replicas, String reason) {
        log.info("Triggering {} for {}/{}", reason, namespace, deployment);
        
        RecommendationCreatedEvent event = RecommendationCreatedEvent.builder()
                .id(UUID.randomUUID().toString())
                .workloadRef("deployment/" + deployment)
                .namespace(namespace)
                .suggestedResources(Collections.emptyMap()) // We only care about replicas here
                .replicas(replicas)
                .confidenceScore(1.0)
                .estimatedMonthlySavings(replicas == 0 ? 50.0 : 0.0) // Mock savings for sleep
                .currency("USD")
                .createdAt(Instant.now())
                .build();

        streamBridge.send(BINDING_NAME, event);
    }
}
