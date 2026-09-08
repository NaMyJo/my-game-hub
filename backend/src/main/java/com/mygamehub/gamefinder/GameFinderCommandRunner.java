package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnExpression("'${app.game-finder.command:}' != ''")
public class GameFinderCommandRunner implements ApplicationRunner {
    private final SteamCatalogSyncService sync;
    private final GameFinderSmokeService smoke;
    private final SteamMetadataVerificationService metadataVerifier;
    private final GameTaxonomyRebuildService taxonomyRebuilder;
    private final IgdbTaxonomyDiagnosticService igdbTaxonomyDiagnostic;
    private final ConfigurableApplicationContext context;
    private final String command;
    private final List<Long> smokeAppIds;

    public GameFinderCommandRunner(SteamCatalogSyncService sync,
            @Lazy GameFinderSmokeService smoke,
            @Lazy SteamMetadataVerificationService metadataVerifier,
            @Lazy GameTaxonomyRebuildService taxonomyRebuilder,
            @Lazy IgdbTaxonomyDiagnosticService igdbTaxonomyDiagnostic,
            ConfigurableApplicationContext context,
            @Value("${app.game-finder.command:}") String command,
            @Value("${app.game-finder.smoke-app-ids:570,1245620,4436560}")
            String smokeAppIds,
            @Value("${app.game-finder.metadata-verify-sample-size:100}") int verifySampleSize,
            @Value("${app.game-finder.metadata-verify-mode:RANDOM}") String verifyMode,
            @Value("${app.game-finder.taxonomy-batch-size:200}") int taxonomyBatchSize) {
        this.sync = sync;
        this.smoke = smoke;
        this.metadataVerifier = metadataVerifier;
        this.taxonomyRebuilder = taxonomyRebuilder;
        this.igdbTaxonomyDiagnostic = igdbTaxonomyDiagnostic;
        this.context = context;
        this.command = command.trim().toLowerCase();
        this.smokeAppIds = Arrays.stream(smokeAppIds.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(Long::valueOf).toList();
        this.verifySampleSize = verifySampleSize;
        this.verifyMode = SteamMetadataVerificationService.VerificationMode.valueOf(
                verifyMode.trim().toUpperCase());
        this.taxonomyBatchSize = taxonomyBatchSize;
    }

    private final int verifySampleSize;
    private final SteamMetadataVerificationService.VerificationMode verifyMode;
    private final int taxonomyBatchSize;

    @Override
    public void run(ApplicationArguments args) {
        try {
            System.out.printf("game_finder_command_start command=%s%n", command);
            switch (command) {
                case "smoke" -> smoke.run(smokeAppIds);
                case "bootstrap" -> sync.bootstrap();
                case "bootstrap-dry-run" -> sync.dryRun();
                case "catalog-diagnostic" -> sync.catalogDiagnostic();
                case "catalog-persist-diagnostic" -> sync.catalogPersistDiagnostic();
                case "enrich" -> sync.enrichBatch();
                case "metadata-verify" -> metadataVerifier.verify(verifySampleSize, verifyMode);
                case "taxonomy" -> taxonomyRebuilder.rebuildBatch(taxonomyBatchSize);
                case "igdb-taxonomy-diagnostic" -> igdbTaxonomyDiagnostic.run();
                case "reconcile" -> sync.reconcile();
                case "sync" -> sync.syncIncremental();
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 GAME FINDER command: " + command);
            }
        } finally {
            System.out.printf("game_finder_command_complete command=%s contextClosing=true%n", command);
            context.close();
        }
    }
}
