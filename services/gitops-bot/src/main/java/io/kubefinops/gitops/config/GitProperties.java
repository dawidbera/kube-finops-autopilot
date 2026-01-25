package io.kubefinops.gitops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gitops.repo")
public class GitProperties {
    private String url = "https://github.com/dawidbera/smarthealth-gitops.git";
    private String branch = "main";
    private String username;
    private String password; // PAT or actual password
    private String clonePath = "/tmp/kubefinops-gitops-repo";
}
