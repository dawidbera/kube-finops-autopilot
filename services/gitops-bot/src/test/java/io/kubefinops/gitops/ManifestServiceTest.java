package io.kubefinops.gitops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateCorrectManifestYaml() throws IOException {
        // Given
        ManifestService manifestService = new ManifestService();
        // Use reflection to set the private field for the test
        org.springframework.test.util.ReflectionTestUtils.setField(manifestService, "simulatedRepoPath", tempDir.toString());
        
        String workloadRef = "deployment/test-app";
        String namespace = "dev-test";
        Map<String, String> resources = Map.of("cpu", "250m", "memory", "512Mi");

        // When
        manifestService.updateManifest(workloadRef, namespace, resources, 15.0, "USD");

        // Then
        Path expectedFile = tempDir.resolve(namespace).resolve("deployment-test-app.yaml");
        
        assertTrue(Files.exists(expectedFile), "Manifest file should be created at: " + expectedFile);
        String content = Files.readString(expectedFile);
        
        assertTrue(content.contains("name: test-app"));
        assertTrue(content.contains("namespace: dev-test"));
        assertTrue(content.contains("cpu: 250m"));
        assertTrue(content.contains("memory: 512Mi"));
        assertTrue(content.contains("Estimated Monthly Savings: 15.00 USD"));
        
        // Cleanup after test
        Files.deleteIfExists(expectedFile);
    }
}
