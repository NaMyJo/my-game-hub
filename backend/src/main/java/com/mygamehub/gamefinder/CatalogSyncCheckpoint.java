package com.mygamehub.gamefinder;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="game_finder_sync_checkpoint")
public class CatalogSyncCheckpoint {
    @Id @Column(length=40) private String syncKey;
    @Column(name="last_app_id") private Long lastAppId;
    @Column(name="last_modified_since") private Long lastModifiedSince;
    @Column(name="last_successful_sync_at") private Instant lastSuccessfulSyncAt;
    @Column(length=20) private String status;
    @Column(name="failure_info", length=1000) private String failureInfo;
    protected CatalogSyncCheckpoint() {}
    public CatalogSyncCheckpoint(String key){this.syncKey=key; this.status="NEW"; this.lastAppId=0L;}
    public void running(){status="RUNNING"; failureInfo=null;}
    public void page(long appId){lastAppId=appId;}
    public void success(long modified){lastAppId=0L; lastModifiedSince=modified; lastSuccessfulSyncAt=Instant.now(); status="SUCCESS"; failureInfo=null;}
    public void failed(String value){status="FAILED"; failureInfo=value == null ? null : value.substring(0, Math.min(1000,value.length()));}
    public Long getLastAppId(){return lastAppId;} public Long getLastModifiedSince(){return lastModifiedSince;}
    public Instant getLastSuccessfulSyncAt(){return lastSuccessfulSyncAt;}
    public String getStatus(){return status;} public String getFailureInfo(){return failureInfo;}
}
