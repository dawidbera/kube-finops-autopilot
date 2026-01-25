package io.kubefinops.gitops;

import io.kubefinops.event.GitOpsPRCreatedEvent;
import io.kubefinops.event.RecommendationApprovedEvent;
import io.kubefinops.gitops.config.GitProperties;
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
public class RecommendationApprovedListener {

    private final ManifestService manifestService;
    private final GitService gitService;
    private final StreamBridge streamBridge;
    private final GitProperties gitProperties;

    private static final String PR_CREATED_BINDING = "prCreated-out-0";

    @Bean
    public Consumer<RecommendationApprovedEvent> handleApprovedRecommendation() {
        return event -> {
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
                
                streamBridge.send(PR_CREATED_BINDING, prCreatedEvent);
                
                log.info(">>> GITOPS BOT ACTION COMPLETE <<<");
                
            } catch (Exception e) {
                log.error("Failed to process GitOps workflow for recommendation {}", event.getRecommendationId(), e);
            }
        };
    }
}
