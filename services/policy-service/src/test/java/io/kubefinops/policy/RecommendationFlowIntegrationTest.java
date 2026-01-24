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
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RecommendationRepository recommendationRepository;

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

        // 3. Send event to Kafka
        kafkaTemplate.send("recommendation.created", recId, event);

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
