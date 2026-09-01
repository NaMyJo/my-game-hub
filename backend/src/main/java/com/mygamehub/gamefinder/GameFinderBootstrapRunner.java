package com.mygamehub.gamefinder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnExpression("${app.game-finder.bootstrap-on-start:false} && '${app.game-finder.command:}' == ''")
public class GameFinderBootstrapRunner implements ApplicationRunner {
    private final SteamCatalogSyncService service;
    public GameFinderBootstrapRunner(SteamCatalogSyncService service){this.service=service;}
    @Override public void run(ApplicationArguments args){service.bootstrap();}
}
