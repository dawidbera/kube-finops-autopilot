package io.kubefinops.policy;

import io.kubefinops.event.RecommendationApprovedEvent;
import io.kubefinops.event.RecommendationCreatedEvent;
import io.kubefinops.policy.domain.Recommendation;
import io.kubefinops.policy.repository.RecommendationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class RecommendationFlowIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.0"));

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafkaContainer::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.configuration.security.protocol", () -> "PLAINTEXT");
    }

    @Autowired
    private org.springframework.cloud.stream.function.StreamBridge streamBridge;

    @Autowired
    private RecommendationRepository recommendationRepository;

    /**
     * Integration test verifying the complete recommendation flow through policy service:
     * 1. Creates a RecommendationCreatedEvent with resource suggestions
     * 2. Sets up a Kafka consumer to capture approval events
     * 3. Sends the recommendation to the policy service for validation via Kafka
     * 4. Verifies the recommendation is persisted to MongoDB with APPROVED status
     * 5. Confirms that a RecommendationApprovedEvent is emitted back to Kafka
     */
    @Test
    void shouldProcessRecommendationThroughWholeFlow() throws Exception {
        // 1. Prepare Event
        String recId = UUID.randomUUID().toString();
        RecommendationCreatedEvent event = RecommendationCreatedEvent.builder()
                .id(recId)
                .workloadRef("deployment/test-app")
                .namespace("prod")
                .suggestedResources(Map.of("cpu", "100m", "memory", "128Mi"))
                .estimatedMonthlySavings(10.0)
                .currency("USD")
                .createdAt(Instant.now())
                .build();

        // 2. Setup consumer to listen for approval event
        BlockingQueue<RecommendationApprovedEvent> approvals = new LinkedBlockingQueue<>();
        setupApprovalConsumer(approvals);

        // 3. Send event to Kafka via StreamBridge
        streamBridge.send("validateRecommendation-in-0", event);

        // 4. Verify MongoDB record (use awaitility for async processing)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Recommendation saved = recommendationRepository.findById(recId).orElse(null);
            assertThat(saved).isNotNull();
            assertThat(saved.getStatus()).isEqualTo("APPROVED");
        });

        // 5. Verify Approval Event was sent back to Kafka
        RecommendationApprovedEvent approvedEvent = approvals.poll(10, TimeUnit.SECONDS);
        assertThat(approvedEvent).isNotNull();
        assertThat(approvedEvent.getRecommendationId()).isEqualTo(recId);
        assertThat(approvedEvent.getWorkloadRef()).isEqualTo("deployment/test-app");
    }

    /**
     * Helper method to configure and start a Kafka consumer listening to the recommendation.approved topic.
     * The consumer deserializes RecommendationApprovedEvent messages and adds them to the provided queue
     * for test assertions.
     *
     * @param queue the blocking queue to receive deserialized approval events from Kafka
     */
    private void setupApprovalConsumer(BlockingQueue<RecommendationApprovedEvent> queue) {
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        JsonDeserializer<RecommendationApprovedEvent> deserializer = new JsonDeserializer<>(RecommendationApprovedEvent.class);
        deserializer.addTrustedPackages("io.kubefinops.*");

        DefaultKafkaConsumerFactory<String, RecommendationApprovedEvent> cf = 
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties("recommendation.approved");
        KafkaMessageListenerContainer<String, RecommendationApprovedEvent> container = 
                new KafkaMessageListenerContainer<>(cf, containerProperties);
        
        container.setupMessageListener((MessageListener<String, RecommendationApprovedEvent>) record -> queue.add(record.value()));
        container.start();
    }
}
