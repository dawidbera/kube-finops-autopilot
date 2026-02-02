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

    /**
     * Updates the Kubernetes manifest file for a specific workload with new resource limits, replicas, etc.
     * It searches for the manifest file using common naming patterns.
     *
     * @param basePath    The base path of the git repository.
     * @param workloadRef The reference to the workload (e.g., "deployment/my-app").
     * @param namespace   The namespace of the workload.
     * @param resources   The map of resource requirements to update (cpu, memory).
     * @param replicas    The number of replicas to set (optional).
     * @param savings     The estimated savings (for logging purposes).
     * @param currency    The currency of the savings.
     */
    public void updateManifest(String basePath, String workloadRef, String namespace, Map<String, String> resources, Integer replicas, Double savings, String currency) {
        try {
            String deploymentName = workloadRef.contains("/") ? workloadRef.split("/")[1] : workloadRef;
            
            // Standard naming patterns in GitOps repo
            String[] possibleFileNames = {
                deploymentName + ".yaml",
                deploymentName + ".yml",
                "deployment-" + deploymentName + ".yaml",
                deploymentName + "-deployment.yaml",
                deploymentName + "-deployment.yml"
            };

            File file = null;
            for (String fileName : possibleFileNames) {
                Path path = Path.of(basePath, namespace, fileName);
                log.debug("Checking manifest path: {}", path);
                if (path.toFile().exists()) {
                    file = path.toFile();
                    break;
                }
            }

            if (file == null) {
                log.error("Manifest file not found for {} in namespace {}. Checked patterns in {}", 
                        workloadRef, namespace, basePath);
                return;
            }

            log.info("Found manifest file: {}", file.getAbsolutePath());
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

    /**
     * Updates the resource requests and limits in the given JSON tree for the specified workload container.
     *
     * @param root         The root JSON node of the manifest.
     * @param workloadName The name of the container/workload to update.
     * @param resources    The map of new resource values.
     */
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

    /**
     * Updates a specific section (requests or limits) within the resources node.
     *
     * @param resourcesNode The JSON node representing the resources.
     * @param section       The section to update ("requests" or "limits").
     * @param resources     The map containing the new values.
     */
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
