package io.kubefinops.recommender.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reports")
public class RecommendationReport {
    @Id
    private String id;
    private String recommendationId;
    private String workloadRef;
    private Map<String, String> suggestedResources;
    private Double estimatedMonthlySavings;
    private String s3Path;
    private Instant generatedAt;
}
