package com.mygamehub.gamefinder;

import jakarta.persistence.*;

@Entity @Table(name="steam_game_tags",uniqueConstraints=@UniqueConstraint(name="uk_steam_game_tag",columnNames={"steam_app_id","tag_id"}),indexes=@Index(name="idx_steam_game_tags_tag",columnList="tag_id,steam_app_id"))
public class SteamGameTag {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="steam_app_id",nullable=false) private Long steamAppId;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="tag_id",nullable=false) private GameTag tag;
    @Column(nullable=false,length=30) private String source;
    protected SteamGameTag(){}
    public SteamGameTag(long appId,GameTag tag,String source){steamAppId=appId;this.tag=tag;this.source=source;}
    public Long getSteamAppId(){return steamAppId;} public GameTag getTag(){return tag;} public String getSource(){return source;}
}
