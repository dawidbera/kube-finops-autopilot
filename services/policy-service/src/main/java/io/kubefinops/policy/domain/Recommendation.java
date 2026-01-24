package io.kubefinops.policy.domain;

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
@Document(collection = "recommendations")
public class Recommendation {

    @Id
    private String id;
    private String workloadRef;
    private String namespace;
    private Map<String, String> currentResources;
    private Map<String, String> suggestedResources;
    private Double confidenceScore;
    private Instant createdAt;
    
    // Status field for future processing (e.g. PENDING, APPROVED, REJECTED)
    @Builder.Default
    private String status = "PENDING";
}
