package io.kubefinops.policy;

import io.kubefinops.event.PolicyViolatedEvent;
import io.kubefinops.event.RecommendationApprovedEvent;
import io.kubefinops.event.RecommendationCreatedEvent;
import io.kubefinops.policy.domain.Recommendation;
import io.kubefinops.policy.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationListener {

    private final RecommendationRepository repository;
    private final PolicyEngine policyEngine;
    private final StreamBridge streamBridge;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private static final String APPROVAL_BINDING = "recommendationApproved-out-0";
    private static final String VIOLATION_BINDING = "policyViolated-out-0";

    @Bean
    public Consumer<RecommendationCreatedEvent> validateRecommendation() {
        return event -> {
            log.info("Received recommendation for validation: {} - Workload: {}", 
                    event.getId(), event.getWorkloadRef());

            Recommendation recommendation = Recommendation.builder()
                    .id(event.getId())
                    .workloadRef(event.getWorkloadRef())
                    .namespace(event.getNamespace())
                    .currentResources(event.getCurrentResources())
                    .suggestedResources(event.getSuggestedResources())
                    .replicas(event.getReplicas())
                    .confidenceScore(event.getConfidenceScore())
                    .estimatedMonthlySavings(event.getEstimatedMonthlySavings())
                    .currency(event.getCurrency())
                    .createdAt(event.getCreatedAt())
                    .status("PENDING")
                    .build();

            // 1. Validate against policies
            ValidationResult validationResult = policyEngine.validate(recommendation);
            
            if (validationResult.isValid()) {
                recommendation.setStatus("APPROVED");
                log.info("Recommendation {} APPROVED", event.getId());
                
                meterRegistry.counter("recommendations_total", "status", "approved", "namespace", event.getNamespace()).increment();

                // 2. Send approval event
                RecommendationApprovedEvent approvedEvent = RecommendationApprovedEvent.builder()
                        .recommendationId(event.getId())
                        .workloadRef(event.getWorkloadRef())
                        .namespace(event.getNamespace())
                        .approvedResources(event.getSuggestedResources())
                        .replicas(event.getReplicas())
                        .estimatedMonthlySavings(event.getEstimatedMonthlySavings())
                        .currency(event.getCurrency())
                        .approvedAt(Instant.now())
                        .build();
                
                streamBridge.send(APPROVAL_BINDING, approvedEvent);
            } else {
                recommendation.setStatus("REJECTED");
                recommendation.setRejectionReason(validationResult.getReason());
                log.info("Recommendation {} REJECTED by policy: {}", event.getId(), validationResult.getReason());

                meterRegistry.counter("recommendations_total", "status", "rejected", "namespace", event.getNamespace()).increment();

                // 2. Send violation event
                PolicyViolatedEvent violationEvent = PolicyViolatedEvent.builder()
                        .recommendationId(event.getId())
                        .policyName(validationResult.getPolicyName())
                        .reason(validationResult.getReason())
                        .violatedAt(Instant.now())
                        .build();

                streamBridge.send(VIOLATION_BINDING, violationEvent);
            }

            // 3. Save final status to MongoDB
            repository.save(recommendation);
        };
    }
}
