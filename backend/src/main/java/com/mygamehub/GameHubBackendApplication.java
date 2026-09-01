package com.mygamehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@EnableScheduling
public class GameHubBackendApplication {
    public static void main(String[] args) {
        run(args, System.getenv());
    }

    static ConfigurableApplicationContext run(String[] args, Map<String, String> environment) {
        SpringApplication application = createApplication(args, environment);
        return application.run(args);
    }

    static SpringApplication createApplication(String[] args, Map<String, String> environment) {
        SpringApplication application = new SpringApplication(GameHubBackendApplication.class);
        configureApplication(application, args, environment);
        return application;
    }

    static void configureApplication(
            SpringApplication application, String[] args, Map<String, String> environment) {
        Optional<String> command = command(args, environment);
        if (command.isPresent()) {
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setLazyInitialization(true);
            System.out.printf(
                    "game_finder_startup_mode commandMode=true command=%s webApplicationType=NONE lazyInitialization=true%n",
                    command.get());
        }
    }

    static Optional<String> command(String[] args, Map<String, String> environment) {
        String prefix = "--app.game-finder.command=";
        Optional<String> commandLine = Arrays.stream(args)
                .filter(argument -> argument.startsWith(prefix))
                .map(argument -> argument.substring(prefix.length()).trim())
                .filter(value -> !value.isEmpty())
                .findFirst();
        if (commandLine.isPresent()) return commandLine;
        String value = environment.getOrDefault("GAME_FINDER_COMMAND", "").trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
