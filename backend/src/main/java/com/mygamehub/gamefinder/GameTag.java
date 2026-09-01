package com.mygamehub.gamefinder;

import jakarta.persistence.*;

@Entity @Table(name="game_tags", uniqueConstraints=@UniqueConstraint(name="uk_game_tags_canonical",columnNames="canonical_name"))
public class GameTag {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="canonical_name",nullable=false,length=60) private String canonicalName;
    @Column(name="display_name_ko",nullable=false,length=60) private String displayNameKo;
    @Column(nullable=false,length=20) private String type;
    protected GameTag(){}
    public GameTag(String canonical,String display,String type){canonicalName=canonical;displayNameKo=display;this.type=type;}
    public Long getId(){return id;} public String getCanonicalName(){return canonicalName;}
    public String getDisplayNameKo(){return displayNameKo;} public String getType(){return type;}
}
