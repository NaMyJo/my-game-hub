package com.mygamehub.gamefinder;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class SteamCatalogSyncService {
    private static final Logger log=LoggerFactory.getLogger(SteamCatalogSyncService.class);
    private final SteamCatalogClient catalog; private final SteamStoreDetailClient store;
    private final SteamGameRepository games; private final CatalogSyncCheckpointRepository checkpoints; private final int batchSize;
    private final IgdbEnrichmentClient igdb;
    private final int pagesPerRun;
    public SteamCatalogSyncService(SteamCatalogClient catalog,SteamStoreDetailClient store,SteamGameRepository games,CatalogSyncCheckpointRepository checkpoints,IgdbEnrichmentClient igdb,@Value("${app.game-finder.sync-batch-size:40}")int batchSize,@Value("${app.game-finder.catalog-pages-per-run:4}")int pagesPerRun){this.catalog=catalog;this.store=store;this.games=games;this.checkpoints=checkpoints;this.igdb=igdb;this.batchSize=batchSize;this.pagesPerRun=pagesPerRun;}
    public void syncIncremental(){for(int i=0;i<pagesPerRun&&sync("steam-catalog");i++){} enrichBatch();}
    public void bootstrap(){while(sync("steam-catalog")){enrichBatch();}while(enrichBatch()>0){}}
    @Transactional
    public boolean sync(String key){var cp=checkpoints.findById(key).orElseGet(()->new CatalogSyncCheckpoint(key));cp.running();checkpoints.save(cp);
        try{long start=cp.getLastAppId()==null?0:cp.getLastAppId();var page=catalog.page(start,cp.getLastModifiedSince());long maxModified=cp.getLastModifiedSince()==null?0:cp.getLastModifiedSince();
            for(var item:page.items()){var game=games.findBySteamAppId(item.appId()).orElseGet(()->new SteamGame(item.appId(),item.name(),item.lastModified(),item.priceChangeNumber()));game.updateCatalog(item.name(),item.lastModified(),item.priceChangeNumber());games.save(game);maxModified=Math.max(maxModified,item.lastModified());}
            if(page.hasMore())cp.page(page.lastAppId());else cp.success(maxModified);checkpoints.save(cp);log.info("game_finder_catalog_page key={} count={} more={} lastAppId={}",key,page.items().size(),page.hasMore(),page.lastAppId());return page.hasMore();
        }catch(RuntimeException e){cp.failed(e.getMessage());checkpoints.save(cp);log.error("game_finder_catalog_sync_failed key={}",key,e);throw e;}}
    public int enrichBatch(){var targets=new java.util.LinkedHashMap<Long,SteamGame>();games.findByPriceUpdatedAtIsNull(PageRequest.of(0,batchSize)).forEach(g->targets.put(g.getSteamAppId(),g));if(targets.size()<batchSize)games.findByMetadataUpdatedAtIsNullOrMetadataUpdatedAtBefore(Instant.now().minus(Duration.ofDays(7)),PageRequest.of(0,batchSize-targets.size())).forEach(g->targets.put(g.getSteamAppId(),g));
        for(var game:targets.values()){try{Thread.sleep(150);var detail=store.get(game.getSteamAppId());if(detail.isEmpty()){game.markEnrichmentAttempted();games.save(game);continue;}var d=detail.get();game.updateStoreDetail(d.type(),d.image(),d.description(),d.free(),d.currency(),d.original(),d.current(),d.discount(),d.requiredAge(),d.adult(),d.releaseDate(),d.releaseText(),d.comingSoon(),d.earlyAccess(),d.genres(),d.categories(),d.single(),d.multiplayer(),d.onlineCoop(),d.offlineCoop());games.save(game);if(igdb.configured()&&game.getIgdbGameId()==null){Thread.sleep(260);igdb.findBySteamAppId(game.getSteamAppId()).ifPresent(data->{game.updateIgdb(data.gameId(),data.minPlayers(),data.maxPlayers(),data.onlineMax(),data.coopMax(),data.multiplayer(),data.onlineCoop(),data.offlineCoop());games.save(game);});}}catch(InterruptedException e){Thread.currentThread().interrupt();return targets.size();}catch(RuntimeException e){game.markEnrichmentAttempted();games.save(game);log.warn("game_finder_enrichment_failed appId={}",game.getSteamAppId(),e);}}
        return targets.size();
    }
}
