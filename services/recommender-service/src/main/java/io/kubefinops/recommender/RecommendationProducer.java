package io.kubefinops.recommender;

import io.kubefinops.event.RecommendationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationProducer {

    private final KafkaTemplate<String, RecommendationCreatedEvent> kafkaTemplate;
    private final io.kubefinops.recommender.client.PrometheusClient prometheusClient;
    private static final String TOPIC = "recommendation.created";

    @Scheduled(fixedRate = 30000)
    public void generateRecommendation() {
        String namespace = "prod";
        String deployment = "nginx";

        prometheusClient.getP95CpuUsage(namespace, deployment)
                .subscribe(cpuUsage -> {
                    double suggestedCpu = cpuUsage * 1.2;
                    String suggestedCpuStr = String.format("%.0fm", suggestedCpu * 1000);

                    RecommendationCreatedEvent event = RecommendationCreatedEvent.builder()
                            .id(UUID.randomUUID().toString())
                            .workloadRef("deployment/" + deployment)
                            .namespace(namespace)
                            .currentResources(Map.of("cpu", "500m", "memory", "512Mi"))
                            .suggestedResources(Map.of("cpu", suggestedCpuStr, "memory", "256Mi"))
                            .confidenceScore(0.90)
                            .createdAt(Instant.now())
                            .build();

                    log.info("Sending recommendation for {} based on P95 usage ({}): {}", 
                            deployment, cpuUsage, event.getId());
                    kafkaTemplate.send(TOPIC, event.getId(), event);
                });
    }
}
