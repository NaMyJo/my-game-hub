package com.mygamehub.gamefinder;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.time.*;

@Service
public class SteamCatalogSyncService {
    private static final Logger log=LoggerFactory.getLogger(SteamCatalogSyncService.class);
    private final SteamCatalogClient catalog; private final SteamStoreDetailClient store;
    private final SteamGameRepository games; private final CatalogSyncCheckpointRepository checkpoints; private final int batchSize;
    private final IgdbEnrichmentClient igdb;
    private final int pagesPerRun;
    private final int bootstrapMaxApps;
    private final long storeDelayMs;
    private final long igdbIntervalMs;
    @Autowired
    public SteamCatalogSyncService(SteamCatalogClient catalog,SteamStoreDetailClient store,SteamGameRepository games,CatalogSyncCheckpointRepository checkpoints,IgdbEnrichmentClient igdb,@Value("${app.game-finder.sync-batch-size:40}")int batchSize,@Value("${app.game-finder.catalog-pages-per-run:4}")int pagesPerRun,@Value("${app.game-finder.bootstrap-max-apps:0}")int bootstrapMaxApps,@Value("${app.game-finder.steam-store-request-delay-ms:500}")long storeDelayMs,@Value("${app.game-finder.igdb-request-interval-ms:260}")long igdbIntervalMs){this.catalog=catalog;this.store=store;this.games=games;this.checkpoints=checkpoints;this.igdb=igdb;this.batchSize=batchSize;this.pagesPerRun=pagesPerRun;this.bootstrapMaxApps=Math.max(0,bootstrapMaxApps);this.storeDelayMs=storeDelayMs;this.igdbIntervalMs=igdbIntervalMs;}
    SteamCatalogSyncService(SteamCatalogClient catalog,SteamStoreDetailClient store,SteamGameRepository games,CatalogSyncCheckpointRepository checkpoints,IgdbEnrichmentClient igdb,int batchSize,int pagesPerRun){this(catalog,store,games,checkpoints,igdb,batchSize,pagesPerRun,0,0,0);}
    @PostConstruct
    void logConfiguration(){log.info("game_finder_bootstrap_config catalogPageSize={} pagesPerRun={} storeDelayMs={} igdbMaxRps={} batchSize={} bootstrapMaxApps={}",catalog.pageSize(),pagesPerRun,storeDelayMs,igdbIntervalMs<=0?"unlimited":String.format(java.util.Locale.ROOT,"%.2f",1000.0/igdbIntervalMs),batchSize,bootstrapMaxApps);}
    public void syncIncremental(){for(int i=0;i<pagesPerRun&&sync("steam-catalog");i++){} enrichBatch();}
    public void bootstrap(){if(bootstrapMaxApps>0){bootstrapLimited(bootstrapMaxApps);return;}while(sync("steam-catalog")){enrichBatch();}while(enrichBatch()>0){}}
    public void dryRun(){var cp=checkpoints.findById("steam-catalog").orElseGet(()->new CatalogSyncCheckpoint("steam-catalog"));log.info("game_finder_bootstrap_dry_run startLastAppId={} modifiedSince={} catalogPageSize={} pagesPerRun={} storeDelayMs={} igdbMaxRps={} batchSize={} bootstrapMaxApps={}",cp.getLastAppId(),cp.getLastModifiedSince(),catalog.pageSize(),pagesPerRun,storeDelayMs,igdbIntervalMs<=0?"unlimited":String.format(java.util.Locale.ROOT,"%.2f",1000.0/igdbIntervalMs),batchSize,bootstrapMaxApps);}
    public void catalogDiagnostic(){catalog.diagnose();}
    public int bootstrapLimited(int maxApps){var cp=checkpoints.findById("steam-catalog").orElseGet(()->new CatalogSyncCheckpoint("steam-catalog"));var page=catalog.page(cp.getLastAppId()==null?0:cp.getLastAppId(),cp.getLastModifiedSince());int processed=0;for(var item:page.items()){if(processed>=maxApps)break;var game=upsertCatalog(item);enrichOne(game);processed++;}log.info("game_finder_bootstrap_limited_complete processed={} checkpointChanged=false",processed);return processed;}
    public boolean sync(String key){var cp=checkpoints.findById(key).orElseGet(()->new CatalogSyncCheckpoint(key));cp.running();checkpoints.save(cp);
        try{long start=cp.getLastAppId()==null?0:cp.getLastAppId();var page=catalog.page(start,cp.getLastModifiedSince());long maxModified=cp.getPendingMaxModified()==null?(cp.getLastModifiedSince()==null?0:cp.getLastModifiedSince()):cp.getPendingMaxModified();
            for(var item:page.items()){upsertCatalog(item);maxModified=Math.max(maxModified,item.lastModified());}
            if(page.hasMore())cp.page(page.lastAppId(),maxModified);else cp.success(maxModified);checkpoints.save(cp);log.info("game_finder_catalog_page key={} count={} more={} lastAppId={}",key,page.items().size(),page.hasMore(),page.lastAppId());return page.hasMore();
        }catch(RuntimeException e){cp.failed(e.getMessage());checkpoints.save(cp);log.error("game_finder_catalog_sync_failed key={}",key,e);throw e;}}
    public int enrichBatch(){var targets=new java.util.LinkedHashMap<Long,SteamGame>();games.findByPriceUpdatedAtIsNull(PageRequest.of(0,batchSize)).forEach(g->targets.put(g.getSteamAppId(),g));if(targets.size()<batchSize)games.findByMetadataUpdatedAtIsNullOrMetadataUpdatedAtBefore(Instant.now().minus(Duration.ofDays(7)),PageRequest.of(0,batchSize-targets.size())).forEach(g->targets.put(g.getSteamAppId(),g));
        for(var game:targets.values())enrichOne(game);
        return targets.size();
    }
    private SteamGame upsertCatalog(SteamCatalogClient.CatalogItem item){var game=games.findBySteamAppId(item.appId()).orElseGet(()->new SteamGame(item.appId(),item.name(),item.lastModified(),item.priceChangeNumber()));game.updateCatalog(item.name(),item.lastModified(),item.priceChangeNumber());return games.save(game);}
    private void enrichOne(SteamGame game){try{var detail=store.get(game.getSteamAppId());if(detail.isEmpty()){game.markEnrichmentAttempted();games.save(game);return;}var d=detail.get();game.updateStoreDetail(d.type(),d.image(),d.description(),d.free(),d.currency(),d.original(),d.current(),d.discount(),d.requiredAge(),d.adult(),d.releaseDate(),d.releaseText(),d.comingSoon(),d.earlyAccess(),d.genres(),d.categories(),d.single(),d.multiplayer(),d.onlineCoop(),d.offlineCoop());games.save(game);if(igdb.configured()&&game.getIgdbGameId()==null)igdb.findBySteamAppId(game.getSteamAppId()).ifPresent(data->{game.updateIgdb(data.gameId(),data.minPlayers(),data.maxPlayers(),data.onlineMax(),data.coopMax(),data.multiplayer(),data.onlineCoop(),data.offlineCoop());games.save(game);});}catch(RuntimeException e){game.markEnrichmentAttempted();games.save(game);log.warn("game_finder_enrichment_failed appId={} errorType={} message={}",game.getSteamAppId(),e.getClass().getSimpleName(),e.getMessage());}}
}
