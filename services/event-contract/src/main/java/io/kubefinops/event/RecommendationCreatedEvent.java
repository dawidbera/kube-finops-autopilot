package io.kubefinops.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCreatedEvent {
    private String id;
    private String workloadRef;
    private String namespace;
    private Map<String, String> currentResources;
    private Map<String, String> suggestedResources;
    private Double confidenceScore;
    private Double estimatedMonthlySavings;
    private String currency;
    private Instant createdAt;
}
