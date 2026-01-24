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
    private static final String TOPIC = "recommendation.created";

    @Scheduled(fixedRate = 10000)
    public void generateFakeRecommendation() {
        RecommendationCreatedEvent event = RecommendationCreatedEvent.builder()
                .id(UUID.randomUUID().toString())
                .workloadRef("deployment/nginx")
                .namespace("prod")
                .currentResources(Map.of("cpu", "500m", "memory", "512Mi"))
                .suggestedResources(Map.of("cpu", "200m", "memory", "256Mi"))
                .confidenceScore(0.95)
                .createdAt(Instant.now())
                .build();

        log.info("Sending recommendation created event: {}", event.getId());
        kafkaTemplate.send(TOPIC, event.getId(), event);
    }
}
