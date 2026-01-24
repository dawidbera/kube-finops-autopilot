package io.kubefinops.recommender;

import io.kubefinops.event.RecommendationCreatedEvent;
import io.kubefinops.recommender.client.PrometheusClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationProducerTest {

    @Mock
    private KafkaTemplate<String, RecommendationCreatedEvent> kafkaTemplate;

    @Mock
    private PrometheusClient prometheusClient;

    @InjectMocks
    private RecommendationProducer recommendationProducer;

    @Test
    void shouldGenerateAndSendRecommendation() {
        // Given
        double mockCpuUsage = 0.100; // 100m
        when(prometheusClient.getP95CpuUsage(anyString(), anyString())).thenReturn(Mono.just(mockCpuUsage));
        
        // When
        recommendationProducer.generateRecommendation();

        // Then
        ArgumentCaptor<RecommendationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(RecommendationCreatedEvent.class);
        verify(kafkaTemplate, timeout(1000)).send(eq("recommendation.created"), anyString(), eventCaptor.capture());
        
        RecommendationCreatedEvent event = eventCaptor.getValue();
        assertEquals("prod", event.getNamespace());
        assertEquals("deployment/nginx", event.getWorkloadRef());
        // 100m * 1.2 = 120m
        assertEquals("120m", event.getSuggestedResources().get("cpu"));
    }
}
