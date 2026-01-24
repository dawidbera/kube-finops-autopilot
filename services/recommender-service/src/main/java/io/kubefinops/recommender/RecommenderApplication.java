package io.kubefinops.recommender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecommenderApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecommenderApplication.class, args);
    }
}
