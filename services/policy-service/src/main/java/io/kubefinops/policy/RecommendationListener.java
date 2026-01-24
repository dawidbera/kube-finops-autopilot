package io.kubefinops.policy;

import io.kubefinops.event.RecommendationCreatedEvent;
import io.kubefinops.policy.domain.Recommendation;
import io.kubefinops.policy.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationListener {

    private final RecommendationRepository repository;

    @KafkaListener(topics = "recommendation.created", groupId = "policy-group")
    public void handleRecommendationCreated(RecommendationCreatedEvent event) {
        log.info("Received recommendation for validation: {} - Workload: {}", 
                event.getId(), event.getWorkloadRef());

        Recommendation recommendation = Recommendation.builder()
                .id(event.getId())
                .workloadRef(event.getWorkloadRef())
                .namespace(event.getNamespace())
                .currentResources(event.getCurrentResources())
                .suggestedResources(event.getSuggestedResources())
                .confidenceScore(event.getConfidenceScore())
                .createdAt(event.getCreatedAt())
                .status("PENDING")
                .build();

        repository.save(recommendation);
        
        log.info("Recommendation {} saved to MongoDB with status PENDING", event.getId());
    }
}
