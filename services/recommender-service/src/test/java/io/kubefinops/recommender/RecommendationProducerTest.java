package io.kubefinops.recommender;

import io.kubefinops.event.RecommendationCreatedEvent;
import io.kubefinops.recommender.client.PrometheusClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationProducerTest {

    @Mock
    private StreamBridge streamBridge;

    @Mock
    private PrometheusClient prometheusClient;

    @Mock
    private CostCalculator costCalculator;

    @Mock
    private ReportService reportService;

    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Mock
    private io.micrometer.core.instrument.Counter counter;

    @InjectMocks
    private RecommendationProducer recommendationProducer;

    /**
     * Unit test for RecommendationProducer verifying the complete recommendation generation flow:
     * 1. Mocks Prometheus client to return P95 CPU and memory usage metrics
     * 2. Mocks cost calculator to return estimated monthly savings
     * 3. Triggers recommendation generation
     * 4. Verifies that the generated RecommendationCreatedEvent is sent to the correct Kafka topic
     * 5. Validates the event contains correct namespace, workload reference, and savings amount
     */
    @Test
    void shouldGenerateAndSendRecommendation() {
        // Given
        double mockCpuUsage = 0.100; // 100m
        double mockMemUsage = 1024 * 1024 * 128.0; // 128Mi
        when(prometheusClient.getP95CpuUsage(anyString(), anyString())).thenReturn(Mono.just(mockCpuUsage));
        when(prometheusClient.getP95MemoryUsage(anyString(), anyString())).thenReturn(Mono.just(mockMemUsage));
        when(costCalculator.calculateMonthlySavings(anyMap(), anyMap())).thenReturn(10.0);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        
        // When
        recommendationProducer.generateRecommendation();

        // Then
        ArgumentCaptor<RecommendationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(RecommendationCreatedEvent.class);
        verify(streamBridge, timeout(2000)).send(eq("recommendationCreated-out-0"), eventCaptor.capture());
        
        RecommendationCreatedEvent event = eventCaptor.getValue();
        assertEquals("dev", event.getNamespace());
        assertEquals("deployment/nginx", event.getWorkloadRef());
        assertEquals(10.0, event.getEstimatedMonthlySavings());
    }
}
