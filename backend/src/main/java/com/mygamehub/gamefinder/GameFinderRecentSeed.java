package com.mygamehub.gamefinder;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="game_finder_recent_seeds", uniqueConstraints=@UniqueConstraint(name="uk_finder_recent_seed_user_app", columnNames={"firebase_uid","steam_app_id"}), indexes=@Index(name="idx_finder_recent_seed_user_time", columnList="firebase_uid,selected_at"))
public class GameFinderRecentSeed {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="firebase_uid",nullable=false,length=128) private String firebaseUid;
 @Column(name="steam_app_id",nullable=false) private Long steamAppId;
 @Column(name="selected_at",nullable=false) private Instant selectedAt;
 protected GameFinderRecentSeed(){}
 public GameFinderRecentSeed(String uid,long appId,Instant at){firebaseUid=uid;steamAppId=appId;selectedAt=at;}
 public void touch(Instant at){selectedAt=at;} public Long getSteamAppId(){return steamAppId;}
}
