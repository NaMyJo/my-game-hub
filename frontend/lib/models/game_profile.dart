enum GameType {
  lostArk,
  leagueOfLegends,
  tft,
  eternalReturn,
}

extension GameTypeX on GameType {
  String get displayName {
    switch (this) {
      case GameType.lostArk:
        return 'LOST ARK';
      case GameType.leagueOfLegends:
        return 'LEAGUE OF LEGENDS';
      case GameType.tft:
        return 'TEAMFIGHT TACTICS';
      case GameType.eternalReturn:
        return 'ETERNAL RETURN';
    }
  }

  String get apiValue {
    switch (this) {
      case GameType.lostArk:
        return 'LOST_ARK';
      case GameType.leagueOfLegends:
        return 'LEAGUE_OF_LEGENDS';
      case GameType.tft:
        return 'TFT';
      case GameType.eternalReturn:
        return 'ETERNAL_RETURN';
    }
  }

  static GameType fromApiValue(String value) {
    switch (value) {
      case 'LOST_ARK':
        return GameType.lostArk;
      case 'LEAGUE_OF_LEGENDS':
        return GameType.leagueOfLegends;
      case 'TFT':
        return GameType.tft;
      case 'ETERNAL_RETURN':
        return GameType.eternalReturn;
      default:
        throw ArgumentError('Unknown game type: $value');
    }
  }

  String get accountLabel {
    switch (this) {
      case GameType.lostArk:
        return '캐릭터 이름';
      case GameType.leagueOfLegends:
      case GameType.tft:
        return 'Riot ID (게임이름#태그)';
      case GameType.eternalReturn:
        return '닉네임';
    }
  }
}

class FavoriteCharacter {
  const FavoriteCharacter({
    required this.characterCode,
    required this.name,
    required this.totalGames,
    required this.averageRank,
  });

  final int? characterCode;
  final String? name;
  final int? totalGames;
  final double? averageRank;

  factory FavoriteCharacter.fromJson(Map<String, dynamic> json) {
    return FavoriteCharacter(
      characterCode: (json['characterCode'] as num?)?.toInt(),
      name: json['name'] as String?,
      totalGames: (json['totalGames'] as num?)?.toInt(),
      averageRank: (json['averageRank'] as num?)?.toDouble(),
    );
  }
}

class GameProfile {
  const GameProfile({
    required this.id,
    required this.type,
    required this.accountName,
    required this.primaryLabel,
    required this.primaryValue,
    this.secondaryLabel,
    this.secondaryValue,
    this.tertiaryLabel,
    this.tertiaryValue,
    this.updatedAt,

    // Eternal Return
    this.totalGames,
    this.averagePlacement,
    this.favoriteCharacter1,
    this.favoriteCharacter2,
    this.favoriteCharacter3,
  });

  final int id;
  final GameType type;
  final String accountName;

  final String primaryLabel;
  final String primaryValue;

  final String? secondaryLabel;
  final String? secondaryValue;

  final String? tertiaryLabel;
  final String? tertiaryValue;
  final DateTime? updatedAt;

  // Eternal Return 전용
  final int? totalGames;
  final double? averagePlacement;
  final FavoriteCharacter? favoriteCharacter1;
  final FavoriteCharacter? favoriteCharacter2;
  final FavoriteCharacter? favoriteCharacter3;

  factory GameProfile.fromJson(Map<String, dynamic> json) {
    return GameProfile(
      id: (json['id'] as num).toInt(),
      type: GameTypeX.fromApiValue(json['gameType'] as String),
      accountName: json['accountName'] as String? ?? '',
      primaryLabel: json['primaryLabel'] as String? ?? '-',
      primaryValue: json['primaryValue'] as String? ?? '-',
      secondaryLabel: json['secondaryLabel'] as String?,
      secondaryValue: json['secondaryValue'] as String?,
      tertiaryLabel: json['tertiaryLabel'] as String?,
      tertiaryValue: json['tertiaryValue'] as String?,
      totalGames: (json['totalGames'] as num?)?.toInt(),
      averagePlacement: (json['averagePlacement'] as num?)?.toDouble(),
      updatedAt: json['updatedAt'] == null
          ? null
          : DateTime.tryParse(json['updatedAt'].toString()),
      favoriteCharacter1: json['favoriteCharacter1'] == null
          ? null
          : FavoriteCharacter.fromJson(
              json['favoriteCharacter1'] as Map<String, dynamic>,
            ),
      favoriteCharacter2: json['favoriteCharacter2'] == null
          ? null
          : FavoriteCharacter.fromJson(
              json['favoriteCharacter2'] as Map<String, dynamic>,
            ),
      favoriteCharacter3: json['favoriteCharacter3'] == null
          ? null
          : FavoriteCharacter.fromJson(
              json['favoriteCharacter3'] as Map<String, dynamic>,
            ),
    );
  }
}
