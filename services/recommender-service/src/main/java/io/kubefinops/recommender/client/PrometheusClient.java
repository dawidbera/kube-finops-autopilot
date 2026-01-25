package io.kubefinops.recommender.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
public class PrometheusClient {

    private final WebClient webClient;
    private final String prometheusUrl;

    public PrometheusClient(WebClient.Builder webClientBuilder, @Value("${prometheus.url}") String prometheusUrl) {
        this.prometheusUrl = prometheusUrl;
        this.webClient = webClientBuilder.baseUrl(prometheusUrl).build();
    }

    public Mono<Double> getP95CpuUsage(String namespace, String deployment) {
        String query = String.format("histogram_quantile(0.95, sum(rate(container_cpu_usage_seconds_total{namespace='%s', pod=~'%s-.*'}[5m])) by (le))", 
                namespace, deployment);
        return queryPrometheus(query);
    }

    public Mono<Double> getP95MemoryUsage(String namespace, String deployment) {
        String query = String.format("quantile(0.95, sum(container_memory_working_set_bytes{namespace='%s', pod=~'%s-.*'}) by (pod))", 
                namespace, deployment);
        return queryPrometheus(query);
    }

    private Mono<Double> queryPrometheus(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "{query}")
                        .build(query))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    try {
                        Map<String, Object> data = (Map<String, Object>) response.get("data");
                        if (data == null) return 0.0;
                        
                        java.util.List<Object> result = (java.util.List<Object>) data.get("result");
                        if (result == null || result.isEmpty()) return 0.0;
                        
                        Map<String, Object> firstResult = (Map<String, Object>) result.get(0);
                        java.util.List<Object> value = (java.util.List<Object>) firstResult.get("value");
                        
                        if (value != null && value.size() >= 2) {
                            String valStr = (String) value.get(1);
                            return Double.parseDouble(valStr);
                        }
                        return 0.0;
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .onErrorReturn(0.0);
    }
}
