package io.kubefinops.policy;

import io.kubefinops.policy.domain.Policy;
import io.kubefinops.policy.domain.Recommendation;
import io.kubefinops.policy.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyEngineTest {

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private PolicyEngine policyEngine;

    @Test
    void shouldApproveWhenNoPoliciesExist() {
        when(policyRepository.findByNamespaceOrNamespaceIsNull(anyString())).thenReturn(Collections.emptyList());
        
        Recommendation rec = Recommendation.builder()
                .namespace("test")
                .suggestedResources(Map.of("cpu", "500m", "memory", "512Mi"))
                .build();
        
        assertTrue(policyEngine.validate(rec));
    }

    @Test
    void shouldRejectWhenCpuExceedsLimit() {
        Policy policy = Policy.builder()
                .name("Limit CPU")
                .maxCpu("200m")
                .build();
        
        when(policyRepository.findByNamespaceOrNamespaceIsNull("prod")).thenReturn(List.of(policy));
        
        Recommendation rec = Recommendation.builder()
                .namespace("prod")
                .suggestedResources(Map.of("cpu", "500m"))
                .build();
        
        assertFalse(policyEngine.validate(rec));
    }

    @Test
    void shouldApproveWhenCpuWithinLimit() {
        Policy policy = Policy.builder()
                .maxCpu("1000m")
                .build();
        
        when(policyRepository.findByNamespaceOrNamespaceIsNull("prod")).thenReturn(List.of(policy));
        
        Recommendation rec = Recommendation.builder()
                .namespace("prod")
                .suggestedResources(Map.of("cpu", "500m"))
                .build();
        
        assertTrue(policyEngine.validate(rec));
    }

    @Test
    void shouldRejectWhenMemoryExceedsLimit() {
        Policy policy = Policy.builder()
                .maxMemory("1Gi")
                .build();
        
        when(policyRepository.findByNamespaceOrNamespaceIsNull("prod")).thenReturn(List.of(policy));
        
        Recommendation rec = Recommendation.builder()
                .namespace("prod")
                .suggestedResources(Map.of("memory", "2Gi"))
                .build();
        
        assertFalse(policyEngine.validate(rec));
    }

    @Test
    void shouldRejectWhenSavingsBelowThreshold() {
        Policy policy = Policy.builder()
                .minMonthlySavings(10.0)
                .build();
        
        when(policyRepository.findByNamespaceOrNamespaceIsNull("prod")).thenReturn(List.of(policy));
        
        Recommendation rec = Recommendation.builder()
                .namespace("prod")
                .suggestedResources(Map.of("cpu", "100m"))
                .estimatedMonthlySavings(5.0)
                .build();
        
        assertFalse(policyEngine.validate(rec));
    }

    @Test
    void shouldApproveWhenSavingsAboveThreshold() {
        Policy policy = Policy.builder()
                .minMonthlySavings(10.0)
                .build();
        
        when(policyRepository.findByNamespaceOrNamespaceIsNull("prod")).thenReturn(List.of(policy));
        
        Recommendation rec = Recommendation.builder()
                .namespace("prod")
                .suggestedResources(Map.of("cpu", "100m"))
                .estimatedMonthlySavings(15.0)
                .build();
        
        assertTrue(policyEngine.validate(rec));
    }
}
