package io.kubefinops.gitops;

import io.kubefinops.event.RecommendationApprovedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class GitOpsBotIntegrationTest {

    @TempDir
    static Path tempGitOrigin; // This will be our "remote"

    @TempDir
    static Path tempCloneDir; // This will be our local clone

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafkaContainer::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.configuration.security.protocol", () -> "PLAINTEXT");
        registry.add("gitops.repo.url", () -> "file://" + tempGitOrigin.toAbsolutePath().toString());
        registry.add("gitops.repo.clone-path", () -> tempCloneDir.resolve("clone").toAbsolutePath().toString());
    }

    @Autowired
    private org.springframework.cloud.stream.function.StreamBridge streamBridge;

    @Test
    void shouldCommitAndPushWhenApprovedRecommendationReceived() throws Exception {
        // 0. Initialize "remote" repository
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.init().setDirectory(tempGitOrigin.toFile()).call()) {
            // Need at least one commit to be able to create branches from HEAD
            java.nio.file.Files.writeString(tempGitOrigin.resolve("README.md"), "GitOps Repo");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Initial commit").call();
            
            // Create the directory structure the bot expects
            Path devDir = tempGitOrigin.resolve("smarthealth-gitops").resolve("test-ns");
            Files.createDirectories(devDir);
            Files.writeString(devDir.resolve("nginx-app.yaml"), """
                    apiVersion: apps/v1
                    kind: Deployment
                    metadata:
                      name: nginx-app
                    spec:
                      replicas: 1
                      template:
                        spec:
                          containers:
                          - name: nginx-app
                            image: nginx
                    """);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Add manifest").call();
        }

        // 1. Prepare Approval Event
        String recId = UUID.randomUUID().toString();
        RecommendationApprovedEvent event = RecommendationApprovedEvent.builder()
                .recommendationId(recId)
                .workloadRef("deployment/nginx-app")
                .namespace("test-ns")
                .approvedResources(Map.of("cpu", "300m", "memory", "1Gi"))
                .estimatedMonthlySavings(25.50)
                .currency("USD")
                .approvedAt(Instant.now())
                .build();

        // 2. Send to Kafka via StreamBridge
        streamBridge.send("handleApprovedRecommendation-in-0", event);

        // 3. Verify that changes were pushed to "remote"
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(tempGitOrigin.toFile())) {
                String branchName = "fix/rightsize-" + recId.substring(0, 8);
                // Check if branch exists in "remote"
                boolean branchExists = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL).call()
                        .stream().anyMatch(ref -> ref.getName().contains(branchName));
                assertThat(branchExists).isTrue();
                
                // Checkout that branch and check file
                git.checkout().setName(branchName).call();
                Path expectedFile = tempGitOrigin.resolve("smarthealth-gitops").resolve("test-ns").resolve("nginx-app.yaml");
                assertThat(Files.exists(expectedFile)).isTrue();
                String content = Files.readString(expectedFile);
                assertThat(content).contains("cpu: 300m");
            }
        });
    }
}
