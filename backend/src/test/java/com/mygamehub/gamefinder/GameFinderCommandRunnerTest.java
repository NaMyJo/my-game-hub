package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameFinderCommandRunnerTest {
    @Test
    void enrichRunsAndClosesApplicationContext() {
        var sync = mock(SteamCatalogSyncService.class);
        var smoke = mock(GameFinderSmokeService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, context, "enrich", "570");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(sync).enrichBatch();
        verify(context).close();
    }
}
