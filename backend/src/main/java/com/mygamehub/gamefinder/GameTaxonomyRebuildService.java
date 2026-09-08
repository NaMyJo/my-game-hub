package com.mygamehub.gamefinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class GameTaxonomyRebuildService {
    static final String CHECKPOINT_KEY = "steam-taxonomy-" + GameTagTaxonomy.STEAM_VERSION;
    static final int MIN_BATCH_SIZE = 100;
    static final int MAX_BATCH_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(GameTaxonomyRebuildService.class);
    private final SteamGameRepository games;
    private final GameTagService tags;
    private final CatalogSyncCheckpointRepository checkpoints;

    public GameTaxonomyRebuildService(SteamGameRepository games, GameTagService tags,
            CatalogSyncCheckpointRepository checkpoints) {
        this.games = games; this.tags = tags; this.checkpoints = checkpoints;
    }

    @Transactional
    public TaxonomyRebuildResult rebuildBatch(int requestedBatchSize) {
        int batchSize = Math.max(MIN_BATCH_SIZE, Math.min(MAX_BATCH_SIZE, requestedBatchSize));
        CatalogSyncCheckpoint checkpoint = checkpoints.findById(CHECKPOINT_KEY)
                .orElseGet(() -> new CatalogSyncCheckpoint(CHECKPOINT_KEY));
        List<SteamGame> targets = games.findTaxonomyVersionCandidates(
                GameTagTaxonomy.STEAM_VERSION, PageRequest.of(0, batchSize));
        for (SteamGame game : targets) tags.rebuild(game);
        long lastAppId = targets.isEmpty()
                ? (checkpoint.getLastAppId() == null ? 0 : checkpoint.getLastAppId())
                : targets.get(targets.size() - 1).getSteamAppId();
        boolean completed = targets.size() < batchSize;
        checkpoint.fullSyncPage(lastAppId, targets.size(), completed);
        checkpoints.save(checkpoint);
        log.info("game_finder_taxonomy_rebuild_complete version={} processed={} lastAppId={} completed={}",
                GameTagTaxonomy.STEAM_VERSION, targets.size(), lastAppId, completed);
        return new TaxonomyRebuildResult(GameTagTaxonomy.STEAM_VERSION,
                targets.size(), lastAppId, completed);
    }

    public record TaxonomyRebuildResult(String version, int processed,
            long lastAppId, boolean completed) {}
}
