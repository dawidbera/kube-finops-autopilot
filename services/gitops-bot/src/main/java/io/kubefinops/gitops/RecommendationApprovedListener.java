package io.kubefinops.gitops;

import io.kubefinops.event.RecommendationApprovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class RecommendationApprovedListener {

    private final ManifestService manifestService;

    @KafkaListener(topics = "recommendation.approved", groupId = "gitops-bot-group")
    public void handleApproval(RecommendationApprovedEvent event) {
        log.info("RECEIVED APPROVED RECOMMENDATION: {} for workload {}", 
                event.getRecommendationId(), event.getWorkloadRef());
        
        log.info(">>> GITOPS BOT ACTION START <<<");
        log.info("1. Cloning GitOps repository...");
        log.info("2. Creating new branch: fix/rightsize-{}", event.getRecommendationId().substring(0, 8));
        
        // Real logic simulation:
        manifestService.updateManifest(event.getWorkloadRef(), event.getNamespace(), event.getApprovedResources());
        
        log.info("4. Committing changes...");
        log.info("5. Pushing to origin...");
        log.info("6. CREATING PULL REQUEST in GitOps repo for recommendation {}", event.getRecommendationId());
        log.info(">>> GITOPS BOT ACTION COMPLETE <<<");
    }
}
