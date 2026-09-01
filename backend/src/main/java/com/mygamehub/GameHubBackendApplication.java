package com.mygamehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GameHubBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameHubBackendApplication.class, args);
    }
}
