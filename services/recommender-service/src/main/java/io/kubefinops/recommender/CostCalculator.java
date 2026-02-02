package io.kubefinops.recommender;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CostCalculator {

    // Hypothetical pricing: $30 per 1 vCPU monthly, $5 per 1 GB monthly
    private static final double CPU_MONTHLY_PRICE = 30.0;
    private static final double MEM_GB_MONTHLY_PRICE = 5.0;

    /**
     * Calculates the estimated monthly savings by switching from current to suggested resources.
     *
     * @param current   The current resource allocation.
     * @param suggested The suggested resource allocation.
     * @return The estimated monthly savings, or 0 if no savings (or cost increase).
     */
    public double calculateMonthlySavings(Map<String, String> current, Map<String, String> suggested) {
        double currentCost = calculateMonthlyCost(current);
        double suggestedCost = calculateMonthlyCost(suggested);
        return Math.max(0, currentCost - suggestedCost);
    }

    /**
     * Calculates the estimated monthly cost for a given set of resources.
     *
     * @param resources The map of resource requirements.
     * @return The estimated monthly cost.
     */
    private double calculateMonthlyCost(Map<String, String> resources) {
        double cpu = parseCpu(resources.getOrDefault("cpu", "0m"));
        double mem = parseMemoryGb(resources.getOrDefault("memory", "0Mi"));
        
        return (cpu * CPU_MONTHLY_PRICE) + (mem * MEM_GB_MONTHLY_PRICE);
    }

    /**
     * Parses a CPU resource string into a number of cores.
     *
     * @param value The CPU resource string (e.g., "500m", "1").
     * @return The number of CPU cores.
     */
    private double parseCpu(String value) {
        if (value.endsWith("m")) {
            return Double.parseDouble(value.replace("m", "")) / 1000.0;
        }
        return Double.parseDouble(value);
    }

    /**
     * Parses a memory resource string into Gigabytes.
     *
     * @param value The memory resource string (e.g., "512Mi", "1Gi").
     * @return The memory in Gigabytes.
     */
    private double parseMemoryGb(String value) {
        String clean = value.toLowerCase();
        if (clean.endsWith("gi")) {
            return Double.parseDouble(clean.replace("gi", ""));
        } else if (clean.endsWith("mi")) {
            return Double.parseDouble(clean.replace("mi", "")) / 1024.0;
        }
        return Double.parseDouble(clean) / (1024.0 * 1024.0 * 1024.0);
    }
}
