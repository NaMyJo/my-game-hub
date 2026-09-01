package com.mygamehub.gamefinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="app.game-finder.sync-enabled",havingValue="true")
public class GameFinderSyncScheduler {
    private final SteamCatalogSyncService service;
    public GameFinderSyncScheduler(SteamCatalogSyncService service){this.service=service;}
    @Scheduled(cron="${app.game-finder.sync-cron:0 0 */6 * * *}") public void run(){service.syncIncremental();}
}
