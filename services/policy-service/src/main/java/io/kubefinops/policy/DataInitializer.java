package io.kubefinops.policy;

import io.kubefinops.policy.domain.Policy;
import io.kubefinops.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PolicyRepository policyRepository;

    @Override
    public void run(String... args) {
        if (policyRepository.count() == 0) {
            log.info("Seeding default policies into MongoDB...");
            
            Policy globalLimit = Policy.builder()
                    .name("Global Resource Limit")
                    .maxCpu("1000m")
                    .maxMemory("2Gi")
                    .minMonthlySavings(5.0) // Must save at least $5
                    .enabled(true)
                    .build();
            
            Policy prodLimit = Policy.builder()
                    .name("Production Namespace Limit")
                    .namespace("prod")
                    .maxCpu("2000m")
                    .maxMemory("4Gi")
                    .maxMonthlyCost(100.0) // Max $100 budget for all approved recommendations
                    .enabled(true)
                    .build();
            
            policyRepository.save(globalLimit);
            policyRepository.save(prodLimit);
            
            log.info("Default policies seeded successfully.");
        }
    }
}
