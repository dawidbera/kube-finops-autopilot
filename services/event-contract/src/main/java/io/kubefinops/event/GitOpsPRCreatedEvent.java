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
public class GitOpsPRCreatedEvent {
    private String recommendationId;
    private String prUrl;
    private String repository;
    private String branchName;
    private Instant createdAt;
}
