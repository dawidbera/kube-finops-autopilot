package io.kubefinops.gitops;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class GitOpsBotApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(GitOpsBotApplication.class, args);
    }
}
