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
    private final io.kubefinops.policy.repository.RecommendationRepository recommendationRepository;

    public ValidationResult validate(Recommendation recommendation) {
        List<Policy> activePolicies = policyRepository.findByNamespaceOrNamespaceIsNull(recommendation.getNamespace());
        
        if (activePolicies.isEmpty()) {
            log.info("No policies found for namespace {}. Auto-approving.", recommendation.getNamespace());
            return ValidationResult.valid();
        }

        for (Policy policy : activePolicies) {
            ValidationResult result = checkPolicy(recommendation, policy);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.valid();
    }

    private ValidationResult checkPolicy(Recommendation recommendation, Policy policy) {
        Map<String, String> suggested = recommendation.getSuggestedResources();
        
        // 1. Check individual Resource Limits
        if (policy.getMaxCpu() != null && suggested.containsKey("cpu")) {
            if (isExceeding(suggested.get("cpu"), policy.getMaxCpu())) {
                String reason = String.format("Suggested CPU %s exceeds limit %s", suggested.get("cpu"), policy.getMaxCpu());
                return ValidationResult.invalid(reason, policy.getName());
            }
        }

        // 2. Check Savings Threshold
        if (policy.getMinMonthlySavings() != null && recommendation.getEstimatedMonthlySavings() != null) {
            if (recommendation.getEstimatedMonthlySavings() < policy.getMinMonthlySavings()) {
                String reason = String.format("Estimated savings $%.2f is below threshold $%.2f", 
                        recommendation.getEstimatedMonthlySavings(), policy.getMinMonthlySavings());
                return ValidationResult.invalid(reason, policy.getName());
            }
        }

        // 3. Check Namespace Budget (AGGREGATE)
        if (policy.getMaxMonthlyCost() != null) {
            double currentTotalCost = calculateCurrentNamespaceCost(recommendation.getNamespace());
            double newRecommendationCost = estimateCost(recommendation.getSuggestedResources());
            
            if ((currentTotalCost + newRecommendationCost) > policy.getMaxMonthlyCost()) {
                String reason = String.format("Namespace budget exceeded. Current: $%.2f, New: $%.2f, Max: $%.2f", 
                        currentTotalCost, newRecommendationCost, policy.getMaxMonthlyCost());
                log.warn("Policy {} violated: {}", policy.getName(), reason);
                return ValidationResult.invalid(reason, policy.getName());
            }
        }
        
        return ValidationResult.valid();
    }

    private double calculateCurrentNamespaceCost(String namespace) {
        List<Recommendation> approved = recommendationRepository.findByNamespaceAndStatusIn(namespace, List.of("APPROVED"));
        return approved.stream()
                .mapToDouble(r -> estimateCost(r.getSuggestedResources()))
                .sum();
    }

    private double estimateCost(Map<String, String> resources) {
        if (resources == null) return 0.0;
        double cpu = parseResource(resources.getOrDefault("cpu", "0m")) / 1000.0;
        double mem = parseResource(resources.getOrDefault("memory", "0Mi")) / (1024.0 * 1024.0 * 1024.0);
        
        // Simple mock cost model: $30 per CPU core, $5 per GB RAM per month
        return (cpu * 30.0) + (mem * 5.0);
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
