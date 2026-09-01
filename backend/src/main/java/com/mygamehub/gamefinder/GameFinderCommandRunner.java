package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnExpression("'${app.game-finder.command:}' != ''")
public class GameFinderCommandRunner implements ApplicationRunner {
    private final SteamCatalogSyncService sync;
    private final GameFinderSmokeService smoke;
    private final ConfigurableApplicationContext context;
    private final String command;
    private final List<Long> smokeAppIds;

    public GameFinderCommandRunner(SteamCatalogSyncService sync,
            GameFinderSmokeService smoke, ConfigurableApplicationContext context,
            @Value("${app.game-finder.command:}") String command,
            @Value("${app.game-finder.smoke-app-ids:570,1245620,4436560}")
            String smokeAppIds) {
        this.sync = sync;
        this.smoke = smoke;
        this.context = context;
        this.command = command.trim().toLowerCase();
        this.smokeAppIds = Arrays.stream(smokeAppIds.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(Long::valueOf).toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            switch (command) {
                case "smoke" -> smoke.run(smokeAppIds);
                case "bootstrap" -> sync.bootstrap();
                case "sync" -> sync.syncIncremental();
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 GAME FINDER command: " + command);
            }
        } finally {
            SpringApplication.exit(context);
        }
    }
}
