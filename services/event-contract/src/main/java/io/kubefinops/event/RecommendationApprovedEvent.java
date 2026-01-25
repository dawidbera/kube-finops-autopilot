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
public class RecommendationApprovedEvent {
    private String recommendationId;
    private String workloadRef;
    private String namespace;
    private Map<String, String> approvedResources;
    private Integer replicas;
    private Double estimatedMonthlySavings;
    private String currency;
    private String approvedBy;
    private Instant approvedAt;
}
