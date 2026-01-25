package io.kubefinops.gitops;

import io.kubefinops.gitops.config.GitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    private final GitProperties gitProperties;

    public Git cloneOrOpenRepo() throws GitAPIException, IOException {
        File cloneDir = new File(gitProperties.getClonePath());
        File gitDir = new File(cloneDir, ".git");
        
        if (cloneDir.exists() && gitDir.exists()) {
            log.info("Opening existing repository at {}", gitProperties.getClonePath());
            Git git = Git.open(cloneDir);
            // Optional: git.pull().setCredentialsProvider(getCredentialsProvider()).call();
            return git;
        }

        // If directory exists but is not a git repo, clean it up first
        if (cloneDir.exists()) {
            log.warn("Directory {} exists but is not a valid Git repository. Re-cloning.", gitProperties.getClonePath());
            FileSystemUtils.deleteRecursively(cloneDir);
        }

        log.info("Cloning repository {} into {}", gitProperties.getUrl(), gitProperties.getClonePath());
        return Git.cloneRepository()
                .setURI(gitProperties.getUrl())
                .setDirectory(cloneDir)
                .setCloneAllBranches(true)
                .setCredentialsProvider(getCredentialsProvider())
                .call();
    }

    public void createBranch(Git git, String branchName) throws GitAPIException {
        log.info("Creating and checking out branch: {}", branchName);
        git.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .call();
    }

    public void commitAndPush(Git git, String message) throws GitAPIException {
        log.info("Committing changes: {}", message);
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message).call();

        log.info("Pushing to origin...");
        git.push()
                .setRemote("origin")
                .setCredentialsProvider(getCredentialsProvider())
                .call();
    }

    public void cleanup() {
        File cloneDir = new File(gitProperties.getClonePath());
        if (cloneDir.exists()) {
            FileSystemUtils.deleteRecursively(cloneDir);
        }
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        if (gitProperties.getUsername() != null && gitProperties.getPassword() != null) {
            log.info("Using Git credentials for user: {}", gitProperties.getUsername());
            return new UsernamePasswordCredentialsProvider(gitProperties.getUsername(), gitProperties.getPassword());
        }
        log.warn("GIT CREDENTIALS NOT FOUND! username: {}, password present: {}", 
                gitProperties.getUsername(), gitProperties.getPassword() != null);
        return null;
    }
}
