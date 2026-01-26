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
