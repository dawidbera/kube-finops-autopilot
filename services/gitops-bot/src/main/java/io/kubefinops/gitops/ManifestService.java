package io.kubefinops.gitops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Service
public class ManifestService {

    private final ObjectMapper yamlMapper;

    public ManifestService() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS);
        this.yamlMapper = new ObjectMapper(factory);
    }

    public void updateManifest(String basePath, String workloadRef, String namespace, Map<String, String> resources, Integer replicas, Double savings, String currency) {
        try {
            String deploymentName = workloadRef.split("/")[1];
            Path manifestPath = Path.of(basePath, "smarthealth-gitops", namespace, deploymentName + ".yaml");
            File file = manifestPath.toFile();

            if (!file.exists()) {
                manifestPath = Path.of(basePath, "smarthealth-gitops", namespace, "deployment-" + deploymentName + ".yaml");
                file = manifestPath.toFile();
            }

            if (!file.exists()) {
                manifestPath = Path.of(basePath, "smarthealth-gitops", namespace, deploymentName + "-deployment.yaml");
                file = manifestPath.toFile();
            }

            if (!file.exists()) {
                log.error("Manifest file not found at expected locations for {}", workloadRef);
                return;
            }

            JsonNode root = yamlMapper.readTree(file);
            
            // Update Replicas if provided
            if (replicas != null) {
                log.info("Updating replicas to: {}", replicas);
                ((ObjectNode) root.path("spec")).put("replicas", replicas);
            }

            // Update Resources if provided
            if (resources != null && !resources.isEmpty()) {
                updateResources(root, deploymentName, resources);
            }

            yamlMapper.writeValue(file, root);
            log.info("INTELLIGENT MANIFEST UPDATE: {} (Savings: {} {})", file.getAbsolutePath(), savings, currency);
        } catch (IOException e) {
            log.error("Failed to update manifest for {}", workloadRef, e);
        }
    }

    private void updateResources(JsonNode root, String workloadName, Map<String, String> resources) {
        JsonNode spec = root.path("spec");
        JsonNode template = spec.path("template");
        JsonNode podSpec = template.path("spec");
        JsonNode containers = podSpec.path("containers");

        if (containers.isArray()) {
            for (JsonNode container : containers) {
                String name = container.path("name").asText();
                // Match container name with workload name (common convention) or update the first one
                if (name.equals(workloadName) || containers.size() == 1) {
                    log.info("Updating resources for container: {}", name);
                    ObjectNode containerNode = (ObjectNode) container;
                    ObjectNode resourcesNode = containerNode.path("resources").isMissingNode() ? 
                            containerNode.putObject("resources") : (ObjectNode) containerNode.path("resources");

                    updateResourceSection(resourcesNode, "requests", resources);
                    updateResourceSection(resourcesNode, "limits", resources);
                    break; // Found and updated
                }
            }
        }
    }

    private void updateResourceSection(ObjectNode resourcesNode, String section, Map<String, String> resources) {
        ObjectNode sectionNode = resourcesNode.path(section).isMissingNode() ? 
                resourcesNode.putObject(section) : (ObjectNode) resourcesNode.path(section);

        if (resources.containsKey("cpu")) {
            sectionNode.put("cpu", resources.get("cpu"));
        }
        if (resources.containsKey("memory")) {
            sectionNode.put("memory", resources.get("memory"));
        }
    }
}
