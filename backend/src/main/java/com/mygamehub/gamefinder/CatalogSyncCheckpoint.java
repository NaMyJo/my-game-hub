package com.mygamehub.gamefinder;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="game_finder_sync_checkpoint")
public class CatalogSyncCheckpoint {
    @Id @Column(length=40) private String syncKey;
    @Column(name="last_app_id") private Long lastAppId;
    @Column(name="last_modified_since") private Long lastModifiedSince;
    @Column(name="pending_max_modified") private Long pendingMaxModified;
    @Column(name="last_successful_sync_at") private Instant lastSuccessfulSyncAt;
    @Column(length=20) private String status;
    @Column(name="failure_info", length=1000) private String failureInfo;
    @Column(name="reconciliation_generation",length=60) private String reconciliationGeneration;
    @Column(name="processed_count") private Long processedCount;
    protected CatalogSyncCheckpoint() {}
    public CatalogSyncCheckpoint(String key){this.syncKey=key; this.status="NEW"; this.lastAppId=0L;}
    public void running(){if(lastAppId==null||lastAppId==0)pendingMaxModified=lastModifiedSince;status="RUNNING"; failureInfo=null;}
    public void page(long appId,long maxModified){lastAppId=appId;pendingMaxModified=maxModified;}
    public void progress(long appId){lastAppId=appId;lastSuccessfulSyncAt=Instant.now();status="SUCCESS";failureInfo=null;}
    public void fullSyncPage(long appId, int processed, boolean completed){
        lastAppId=appId;processedCount=(processedCount==null?0:processedCount)+processed;
        lastSuccessfulSyncAt=Instant.now();status=completed?"COMPLETED":"SUCCESS";failureInfo=null;
    }
    public void success(long modified){lastAppId=0L; lastModifiedSince=modified;pendingMaxModified=null;lastSuccessfulSyncAt=Instant.now(); status="SUCCESS"; failureInfo=null;}
    public void failed(String value){status="FAILED"; failureInfo=value == null ? null : value.substring(0, Math.min(1000,value.length()));}
    public String ensureReconciliationGeneration(){if(reconciliationGeneration==null||reconciliationGeneration.isBlank())reconciliationGeneration=java.util.UUID.randomUUID().toString();return reconciliationGeneration;}
    public void clearReconciliationGeneration(){reconciliationGeneration=null;}
    public Long getLastAppId(){return lastAppId;} public Long getLastModifiedSince(){return lastModifiedSince;}
    public Long getPendingMaxModified(){return pendingMaxModified;}
    public Instant getLastSuccessfulSyncAt(){return lastSuccessfulSyncAt;}
    public String getStatus(){return status;} public String getFailureInfo(){return failureInfo;}
    public Long getProcessedCount(){return processedCount;}
    public String getReconciliationGeneration(){return reconciliationGeneration;}
}
