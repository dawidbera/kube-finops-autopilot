package io.kubefinops.recommender;

import io.kubefinops.event.RecommendationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationProducer {

    private final StreamBridge streamBridge;
    private final io.kubefinops.recommender.client.PrometheusClient prometheusClient;
    private final CostCalculator costCalculator;
    private final ReportService reportService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private static final String BINDING_NAME = "recommendationCreated-out-0";

    /**
     * Periodically generates resource recommendations for workloads.
     * Fetches usage metrics from Prometheus, calculates savings, and publishes a RecommendationCreatedEvent.
     */
    @Scheduled(fixedRateString = "${app.scheduler.rate:30000}", initialDelayString = "${app.scheduler.delay:0}")
    public void generateRecommendation() {
        String namespace = "dev";
        String deployment = "nginx";

        reactor.core.publisher.Mono.zip(
                prometheusClient.getP95CpuUsage(namespace, deployment),
                prometheusClient.getP95MemoryUsage(namespace, deployment)
        ).subscribe(tuple -> {
            Double cpuUsage = tuple.getT1();
            Double memUsage = tuple.getT2();

            // Rightsizing logic: 20% buffer
            String suggestedCpu = String.format("%.0fm", Math.max(cpuUsage, 0.01) * 1200);
            String suggestedMem = String.format("%.0fMi", (Math.max(memUsage, 1024 * 1024 * 64) / (1024 * 1024)) * 1.2);

            Map<String, String> currentResources = Map.of("cpu", "500m", "memory", "512Mi");
            Map<String, String> suggestedResources = Map.of("cpu", suggestedCpu, "memory", suggestedMem);

            double monthlySavings = costCalculator.calculateMonthlySavings(currentResources, suggestedResources);
            String recId = UUID.randomUUID().toString();

            RecommendationCreatedEvent event = RecommendationCreatedEvent.builder()
                    .id(recId)
                    .workloadRef("deployment/" + deployment)
                    .namespace(namespace)
                    .currentResources(currentResources)
                    .suggestedResources(suggestedResources)
                    .confidenceScore(0.90)
                    .estimatedMonthlySavings(monthlySavings)
                    .currency("USD")
                    .createdAt(Instant.now())
                    .build();

            log.info("Sending recommendation for {} (CPU: {}, MEM: {}, Savings: ${})", 
                    deployment, suggestedCpu, suggestedMem, String.format("%.2f", monthlySavings));
            
            // Export metrics
            meterRegistry.counter("recommendations_created_total", "namespace", namespace).increment();
            meterRegistry.counter("recommendation_savings_total", "namespace", namespace).increment(monthlySavings);

            streamBridge.send(BINDING_NAME, event);

            // Generate report
            reportService.generateAndStoreReport(recId, event.getWorkloadRef(), suggestedResources, monthlySavings);
        });
    }
}
