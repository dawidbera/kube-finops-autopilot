package io.kubefinops.gitops;

import io.kubefinops.event.ChangeAppliedEvent;
import io.kubefinops.event.ChangeFailedEvent;
import io.kubefinops.event.GitOpsPRCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncMonitor {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final Random random = new Random();

    private static final String APPLIED_TOPIC = "change.applied";
    private static final String FAILED_TOPIC = "change.failed";

    @KafkaListener(topics = "gitops.pr.created", groupId = "sync-monitor-group")
    public void handlePRCreated(GitOpsPRCreatedEvent event) {
        log.info("Monitoring Sync status for PR: {} (Recommendation: {})", 
                event.getPrUrl(), event.getRecommendationId());

        new Thread(() -> {
            try {
                Thread.sleep(5000);
                
                // Simulate 10% failure rate
                if (random.nextDouble() > 0.1) {
                    ChangeAppliedEvent appliedEvent = ChangeAppliedEvent.builder()
                            .recommendationId(event.getRecommendationId())
                            .prUrl(event.getPrUrl())
                            .argoSyncId(UUID.randomUUID().toString())
                            .appliedAt(Instant.now())
                            .build();

                    log.info(">>> ARGO CD SYNC COMPLETE: Recommendation {} applied to cluster", 
                            event.getRecommendationId());
                    
                    meterRegistry.counter("change_applied_total", "status", "success").increment();
                    kafkaTemplate.send(APPLIED_TOPIC, event.getRecommendationId(), appliedEvent);
                } else {
                    ChangeFailedEvent failedEvent = ChangeFailedEvent.builder()
                            .recommendationId(event.getRecommendationId())
                            .prUrl(event.getPrUrl())
                            .errorMessage("Argo CD timeout: health checks failed for workload")
                            .failedAt(Instant.now())
                            .build();

                    log.error(">>> ARGO CD SYNC FAILED: Recommendation {} could not be applied", 
                            event.getRecommendationId());
                    
                    meterRegistry.counter("change_applied_total", "status", "failed").increment();
                    kafkaTemplate.send(FAILED_TOPIC, event.getRecommendationId(), failedEvent);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
