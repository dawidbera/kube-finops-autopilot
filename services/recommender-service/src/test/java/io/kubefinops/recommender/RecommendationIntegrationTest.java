package io.kubefinops.recommender;

import io.kubefinops.event.RecommendationCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RecommendationIntegrationTest {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static org.testcontainers.containers.MongoDBContainer mongoContainer = new org.testcontainers.containers.MongoDBContainer(DockerImageName.parse("mongo:7.0.0"));

    @org.junit.jupiter.api.extension.RegisterExtension
    static com.github.tomakehurst.wiremock.junit5.WireMockExtension wireMock = com.github.tomakehurst.wiremock.junit5.WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafkaContainer::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl);
        registry.add("spring.cloud.stream.kafka.binder.configuration.security.protocol", () -> "PLAINTEXT");
        registry.add("prometheus.url", wireMock::baseUrl);
        registry.add("app.scheduler.rate", () -> 1000000);
        registry.add("app.scheduler.delay", () -> 1000000);
    }

    @Autowired
    private RecommendationProducer recommendationProducer;

    @Test
    void shouldFetchMetricsAndSendKafkaEvent() throws Exception {
        // 1. Mock Prometheus Response
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/query"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"value\":[1643061600,\"0.150\"]}]}}")));

        // 2. Setup Kafka Consumer
        BlockingQueue<RecommendationCreatedEvent> events = new LinkedBlockingQueue<>();
        setupKafkaConsumer(events);

        // 3. Trigger recommendation generation
        recommendationProducer.generateRecommendation();

        // 4. Verify Kafka Event
        RecommendationCreatedEvent event = events.poll(30, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getNamespace()).isEqualTo("dev");
        assertThat(event.getWorkloadRef()).isEqualTo("deployment/nginx");
        // 0.150 * 1.2 = 0.180 -> "180m"
        assertThat(event.getSuggestedResources().get("cpu")).isEqualTo("180m");
        assertThat(event.getEstimatedMonthlySavings()).isNotNull();
        assertThat(event.getEstimatedMonthlySavings()).isGreaterThan(0);
    }

    private void setupKafkaConsumer(BlockingQueue<RecommendationCreatedEvent> queue) {
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-recommender-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        JsonDeserializer<RecommendationCreatedEvent> deserializer = new JsonDeserializer<>(RecommendationCreatedEvent.class);
        deserializer.addTrustedPackages("io.kubefinops.*", "io.kubefinops.*");

        DefaultKafkaConsumerFactory<String, RecommendationCreatedEvent> cf = 
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties("recommendation.created");
        KafkaMessageListenerContainer<String, RecommendationCreatedEvent> container = 
                new KafkaMessageListenerContainer<>(cf, containerProperties);
        
        container.setupMessageListener((MessageListener<String, RecommendationCreatedEvent>) record -> queue.add(record.value()));
        container.start();
    }
}
