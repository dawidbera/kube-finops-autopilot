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

    /**
     * Validates a recommendation against all active policies for the given namespace.
     *
     * @param recommendation The recommendation to validate.
     * @return A ValidationResult indicating if the recommendation is valid or providing a reason for rejection.
     */
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

    /**
     * Checks if a recommendation complies with a specific policy.
     * Verifies resource limits, savings thresholds, and aggregate namespace budgets.
     *
     * @param recommendation The recommendation to check.
     * @param policy         The policy to enforce.
     * @return A ValidationResult indicating compliance or violation.
     */
    private ValidationResult checkPolicy(Recommendation recommendation, Policy policy) {
        Map<String, String> suggested = recommendation.getSuggestedResources();
        
        // 1. Check individual Resource Limits
        if (policy.getMaxCpu() != null && suggested.containsKey("cpu")) {
            if (isExceeding(suggested.get("cpu"), policy.getMaxCpu())) {
                String reason = String.format("Suggested CPU %s exceeds limit %s", suggested.get("cpu"), policy.getMaxCpu());
                return ValidationResult.invalid(reason, policy.getName());
            }
        }

        if (policy.getMaxMemory() != null && suggested.containsKey("memory")) {
            if (isExceeding(suggested.get("memory"), policy.getMaxMemory())) {
                String reason = String.format("Suggested Memory %s exceeds limit %s", suggested.get("memory"), policy.getMaxMemory());
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

    /**
     * Calculates the total estimated monthly cost of all approved recommendations in a namespace.
     *
     * @param namespace The namespace to calculate cost for.
     * @return The total estimated monthly cost.
     */
    private double calculateCurrentNamespaceCost(String namespace) {
        List<Recommendation> approved = recommendationRepository.findByNamespaceAndStatusIn(namespace, List.of("APPROVED"));
        return approved.stream()
                .mapToDouble(r -> estimateCost(r.getSuggestedResources()))
                .sum();
    }

    /**
     * Estimates the monthly cost of a given set of resources based on a simple cost model.
     *
     * @param resources The map of resource requirements.
     * @return The estimated monthly cost.
     */
    private double estimateCost(Map<String, String> resources) {
        if (resources == null) return 0.0;
        double cpu = parseResource(resources.getOrDefault("cpu", "0m")) / 1000.0;
        double mem = parseResource(resources.getOrDefault("memory", "0Mi")) / (1024.0 * 1024.0 * 1024.0);
        
        // Simple mock cost model: $30 per CPU core, $5 per GB RAM per month
        return (cpu * 30.0) + (mem * 5.0);
    }

    /**
     * Checks if a suggested resource value exceeds a defined limit.
     *
     * @param suggested The suggested resource value (e.g., "500m").
     * @param limit     The limit value (e.g., "1000m").
     * @return True if suggested value exceeds the limit, false otherwise.
     */
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

    /**
     * Parses a Kubernetes resource string into a double value representing the quantity.
     * Handles units like 'm' (milli-cores), 'Gi' (Gibibytes), 'Mi', 'Ki'.
     *
     * @param value The resource string to parse.
     * @return The parsed value as a double.
     */
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
