package io.kubefinops.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyViolatedEvent {
    private String recommendationId;
    private String reason;
    private String policyName;
    private String details;
    private Instant violatedAt;
}
