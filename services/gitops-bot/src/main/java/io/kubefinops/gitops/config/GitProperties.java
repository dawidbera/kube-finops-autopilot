package io.kubefinops.gitops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gitops.repo")
public class GitProperties {
    private String url;
    private String branch = "master";
    private String username; // Will be set from GITOPS_GIT_USER
    private String password; // Will be set from GITOPS_GIT_TOKEN (PAT)
    private String clonePath = "/tmp/kubefinops-gitops-clone";
}
