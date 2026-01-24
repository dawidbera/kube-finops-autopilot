package io.kubefinops.policy.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "policies")
public class Policy {

    @Id
    private String id;
    private String name;
    private String namespace; // If null, applies globally
    
    // Constraints
    private String maxCpu;    // e.g., "1000m"
    private String maxMemory; // e.g., "2Gi"
    private Double minMonthlySavings; // e.g., 2.0 (only approve if savings >= $2)
    
    @Builder.Default
    private boolean enabled = true;
}
