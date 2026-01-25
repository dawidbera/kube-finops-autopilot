package io.kubefinops.gitops;

import io.kubefinops.event.GitOpsPRCreatedEvent;
import io.kubefinops.event.RecommendationApprovedEvent;
import io.kubefinops.gitops.config.GitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class RecommendationApprovedListener {

    private final ManifestService manifestService;
    private final GitService gitService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GitProperties gitProperties;

    private static final String PR_CREATED_TOPIC = "gitops.pr.created";

    @KafkaListener(topics = "recommendation.approved", groupId = "gitops-bot-group")
    public void handleApproval(RecommendationApprovedEvent event) {
        log.info("RECEIVED APPROVED RECOMMENDATION: {} for workload {}", 
                event.getRecommendationId(), event.getWorkloadRef());
        
        String branchName = "fix/rightsize-" + event.getRecommendationId().substring(0, 8);
        
        try (org.eclipse.jgit.api.Git git = gitService.cloneOrOpenRepo()) {
            log.info(">>> GITOPS BOT ACTION START <<<");
            
            gitService.createBranch(git, branchName);
            
            String repoPath = git.getRepository().getWorkTree().getAbsolutePath();
            manifestService.updateManifest(repoPath, event.getWorkloadRef(), event.getNamespace(), 
                    event.getApprovedResources(), event.getReplicas(), event.getEstimatedMonthlySavings(), event.getCurrency());
            
            String commitMessage = String.format("chore: rightsizing %s based on recommendation %s", 
                    event.getWorkloadRef(), event.getRecommendationId());
            
            gitService.commitAndPush(git, commitMessage);
            
            log.info("6. CREATING PULL REQUEST (Simulated) in GitOps repo for recommendation {}", event.getRecommendationId());
            
            // Send GitOpsPRCreatedEvent
            GitOpsPRCreatedEvent prCreatedEvent = GitOpsPRCreatedEvent.builder()
                    .recommendationId(event.getRecommendationId())
                    .prUrl("https://github.com/simulated/repo/pull/123") // Simulated URL
                    .repository(gitProperties.getUrl())
                    .branchName(branchName)
                    .createdAt(Instant.now())
                    .build();
            
            kafkaTemplate.send(PR_CREATED_TOPIC, event.getRecommendationId(), prCreatedEvent);
            
            log.info(">>> GITOPS BOT ACTION COMPLETE <<<");
            
        } catch (Exception e) {
            log.error("Failed to process GitOps workflow for recommendation {}", event.getRecommendationId(), e);
        }
    }
}
