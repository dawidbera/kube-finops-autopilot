package io.kubefinops.recommender;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CostCalculator {

    // Hypothetical pricing: $30 per 1 vCPU monthly, $5 per 1 GB monthly
    private static final double CPU_MONTHLY_PRICE = 30.0;
    private static final double MEM_GB_MONTHLY_PRICE = 5.0;

    public double calculateMonthlySavings(Map<String, String> current, Map<String, String> suggested) {
        double currentCost = calculateMonthlyCost(current);
        double suggestedCost = calculateMonthlyCost(suggested);
        return Math.max(0, currentCost - suggestedCost);
    }

    private double calculateMonthlyCost(Map<String, String> resources) {
        double cpu = parseCpu(resources.getOrDefault("cpu", "0m"));
        double mem = parseMemoryGb(resources.getOrDefault("memory", "0Mi"));
        
        return (cpu * CPU_MONTHLY_PRICE) + (mem * MEM_GB_MONTHLY_PRICE);
    }

    private double parseCpu(String value) {
        if (value.endsWith("m")) {
            return Double.parseDouble(value.replace("m", "")) / 1000.0;
        }
        return Double.parseDouble(value);
    }

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
