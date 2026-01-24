package io.kubefinops.policy;

import io.kubefinops.event.RecommendationApprovedEvent;
import io.kubefinops.event.RecommendationCreatedEvent;
import io.kubefinops.policy.domain.Recommendation;
import io.kubefinops.policy.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationListener {

    private final RecommendationRepository repository;
    private final PolicyEngine policyEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String APPROVAL_TOPIC = "recommendation.approved";

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

        // 1. Validate against policies
        if (policyEngine.validate(recommendation)) {
            recommendation.setStatus("APPROVED");
            log.info("Recommendation {} APPROVED", event.getId());
            
            // 2. Send approval event
            RecommendationApprovedEvent approvedEvent = RecommendationApprovedEvent.builder()
                    .recommendationId(event.getId())
                    .workloadRef(event.getWorkloadRef())
                    .namespace(event.getNamespace())
                    .approvedResources(event.getSuggestedResources())
                    .approvedAt(Instant.now())
                    .build();
            
            kafkaTemplate.send(APPROVAL_TOPIC, event.getId(), approvedEvent);
        } else {
            recommendation.setStatus("REJECTED");
            log.info("Recommendation {} REJECTED by policy", event.getId());
        }

        // 3. Save final status to MongoDB
        repository.save(recommendation);
    }
}
