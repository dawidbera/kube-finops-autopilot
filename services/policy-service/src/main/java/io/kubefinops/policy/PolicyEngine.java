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
        
        // Check CPU
        if (policy.getMaxCpu() != null && suggested.containsKey("cpu")) {
            if (isExceeding(suggested.get("cpu"), policy.getMaxCpu())) {
                log.warn("Policy {} violated: Suggested CPU {} exceeds limit {}", 
                        policy.getName(), suggested.get("cpu"), policy.getMaxCpu());
                return false;
            }
        }

        // Check Memory
        if (policy.getMaxMemory() != null && suggested.containsKey("memory")) {
            if (isExceeding(suggested.get("memory"), policy.getMaxMemory())) {
                log.warn("Policy {} violated: Suggested Memory {} exceeds limit {}", 
                        policy.getName(), suggested.get("memory"), policy.getMaxMemory());
                return false;
            }
        }
        
        return true;
    }

    private boolean isExceeding(String suggested, String limit) {
        try {
            double sVal = parseResource(suggested);
            double lVal = parseResource(limit);
            return sVal > lVal;
        } catch (Exception e) {
            log.error("Error parsing resources: {} vs {}", suggested, limit);
            return false; 
        }
    }

    private double parseResource(String value) {
        String cleanValue = value.toLowerCase().trim();
        if (cleanValue.endsWith("m")) {
            return Double.parseDouble(cleanValue.replace("m", ""));
        } else if (cleanValue.endsWith("gi")) {
            return Double.parseDouble(cleanValue.replace("gi", "")) * 1024 * 1024 * 1024;
        } else if (cleanValue.endsWith("mi")) {
            return Double.parseDouble(cleanValue.replace("mi", "")) * 1024 * 1024;
        } else if (cleanValue.endsWith("ki")) {
            return Double.parseDouble(cleanValue.replace("ki", "")) * 1024;
        }
        // Assume raw number is bytes or full CPU units
        try {
            return Double.parseDouble(cleanValue) * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
