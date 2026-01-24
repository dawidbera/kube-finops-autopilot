package io.kubefinops.policy;

import io.kubefinops.policy.domain.Policy;
import io.kubefinops.policy.domain.Recommendation;
import io.kubefinops.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEngine {

    private final PolicyRepository policyRepository;

    public boolean validate(Recommendation recommendation) {
        List<Policy> activePolicies = policyRepository.findByNamespaceOrNamespaceIsNull(recommendation.getNamespace());
        
        if (activePolicies.isEmpty()) {
            log.info("No policies found for namespace {}. Auto-approving.", recommendation.getNamespace());
            return true;
        }

        for (Policy policy : activePolicies) {
            if (!checkPolicy(recommendation, policy)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkPolicy(Recommendation recommendation, Policy policy) {
        Map<String, String> suggested = recommendation.getSuggestedResources();
        
        // Simple CPU check (basic string comparison for now, to be improved)
        if (policy.getMaxCpu() != null && suggested.containsKey("cpu")) {
            if (isExceeding(suggested.get("cpu"), policy.getMaxCpu())) {
                log.warn("Policy {} violated: Suggested CPU {} exceeds limit {}", 
                        policy.getName(), suggested.get("cpu"), policy.getMaxCpu());
                return false;
            }
        }
        
        return true;
    }

    // Placeholder for K8s resource comparison logic
    private boolean isExceeding(String suggested, String limit) {
        // Very basic logic for MVP: just compare raw strings or simple numeric values if possible
        try {
            double sVal = parseResource(suggested);
            double lVal = parseResource(limit);
            return sVal > lVal;
        } catch (Exception e) {
            return false; 
        }
    }

    private double parseResource(String value) {
        if (value.endsWith("m")) {
            return Double.parseDouble(value.replace("m", ""));
        }
        return Double.parseDouble(value) * 1000;
    }
}
