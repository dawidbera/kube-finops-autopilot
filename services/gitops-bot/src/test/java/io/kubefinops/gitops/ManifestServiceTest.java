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

    /**
     * Unit test for ManifestService verifying that it correctly updates Kubernetes deployment manifests
     * with recommended resource values. Tests:
     * 1. Creates a temporary GitOps directory with a test deployment manifest
     * 2. Updates the manifest with new resource requests (cpu: 250m, memory: 512Mi)
     * 3. Verifies that the manifest is correctly modified to reflect the new values
     * 4. Validates that replica count and resource limits are also updated
     */
    @Test
    void shouldUpdateManifestYaml() throws IOException {
        // Given
        ManifestService manifestService = new ManifestService();
        String workloadRef = "deployment/test-app";
        String namespace = "dev-test";
        
        Path gitopsDir = tempDir.resolve("smarthealth-gitops");
        Path nsDir = gitopsDir.resolve(namespace);
        Files.createDirectories(nsDir);
        Path manifestFile = nsDir.resolve("test-app.yaml");
        Files.writeString(manifestFile, """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: test-app
                  namespace: dev-test
                spec:
                  replicas: 1
                  template:
                    spec:
                      containers:
                      - name: test-app
                        resources:
                          requests:
                            cpu: 100m
                """);

        Map<String, String> resources = Map.of("cpu", "250m", "memory", "512Mi");

        // When
        manifestService.updateManifest(gitopsDir.toString(), workloadRef, namespace, resources, 0, 15.0, "USD");

        // Then
        String content = Files.readString(manifestFile);
        assertTrue(content.contains("replicas: 0"));
        assertTrue(content.contains("cpu: 250m"));
        assertTrue(content.contains("memory: 512Mi"));
    }
}
