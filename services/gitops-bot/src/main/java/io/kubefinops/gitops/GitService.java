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

    /**
     * Clones the repository defined in properties or opens it if it already exists.
     * If the directory exists but is not a valid repository, it is cleaned up and re-cloned.
     *
     * @return The Git object representing the repository.
     * @throws GitAPIException If a Git error occurs.
     * @throws IOException     If an I/O error occurs.
     */
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

    /**
     * Creates a new branch with the given name and checks it out.
     *
     * @param git        The Git object.
     * @param branchName The name of the branch to create.
     * @throws GitAPIException If a Git error occurs.
     */
    public void createBranch(Git git, String branchName) throws GitAPIException {
        log.info("Creating and checking out branch: {}", branchName);
        git.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .call();
    }

    /**
     * Commits all changes in the working directory and pushes them to the origin remote.
     *
     * @param git     The Git object.
     * @param message The commit message.
     * @throws GitAPIException If a Git error occurs.
     */
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

    /**
     * Deletes the local clone directory to clean up resources.
     */
    public void cleanup() {
        File cloneDir = new File(gitProperties.getClonePath());
        if (cloneDir.exists()) {
            FileSystemUtils.deleteRecursively(cloneDir);
        }
    }

    /**
     * Creates a credentials provider using the configured username and password.
     *
     * @return The credentials provider, or null if credentials are not configured.
     */
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
