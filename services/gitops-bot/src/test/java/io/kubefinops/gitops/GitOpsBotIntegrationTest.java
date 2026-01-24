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
    static Path tempRepoDir;

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("gitops.simulated-repo-path", tempRepoDir::toString);
        registry.add("spring.kafka.producer.value-serializer", () -> "org.springframework.kafka.support.serializer.JsonSerializer");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void shouldCreateFileWhenApprovedRecommendationReceived() {
        // 1. Prepare Approval Event
        String recId = UUID.randomUUID().toString();
        RecommendationApprovedEvent event = RecommendationApprovedEvent.builder()
                .recommendationId(recId)
                .workloadRef("deployment/nginx-app")
                .namespace("test-ns")
                .approvedResources(Map.of("cpu", "300m", "memory", "1Gi"))
                .approvedAt(Instant.now())
                .build();

        // 2. Send to Kafka
        kafkaTemplate.send("recommendation.approved", recId, event);

        // 3. Verify that ManifestService created the file
        Path expectedFile = tempRepoDir.resolve("test-ns").resolve("deployment-nginx-app.yaml");
        
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(Files.exists(expectedFile)).isTrue();
            String content = Files.readString(expectedFile);
            assertThat(content).contains("cpu: 300m");
            assertThat(content).contains("memory: 1Gi");
        });
    }
}
