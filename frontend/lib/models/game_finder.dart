class SteamGameSearchItem {
  const SteamGameSearchItem(
      {required this.appId, required this.name, this.imageUrl});
  final int appId;
  final String name;
  final String? imageUrl;
  factory SteamGameSearchItem.fromJson(Map<String, dynamic> json) =>
      SteamGameSearchItem(
          appId: (json['steamAppId'] as num).toInt(),
          name: json['name'] as String? ?? '',
          imageUrl: json['headerImageUrl'] as String?);
}

class GameFinderRecommendation {
  const GameFinderRecommendation(
      {required this.appId,
      required this.name,
      required this.matchScore,
      this.imageUrl,
      this.currentPrice,
      this.originalPrice,
      this.discountPercent,
      this.currency,
      required this.isFree,
      this.releaseDate,
      this.releaseDateText,
      required this.comingSoon,
      this.singlePlayer,
      this.multiplayer,
      this.onlineCoop,
      this.maxPlayers,
      required this.genres,
      required this.storeUrl});
  final int appId, matchScore;
  final String name, storeUrl;
  final String? imageUrl, currency, releaseDate, releaseDateText;
  final int? currentPrice, originalPrice, discountPercent, maxPlayers;
  final bool isFree, comingSoon;
  final bool? singlePlayer, multiplayer, onlineCoop;
  final List<String> genres;
  factory GameFinderRecommendation.fromJson(Map<String, dynamic> j) =>
      GameFinderRecommendation(
          appId: (j['steamAppId'] as num).toInt(),
          name: j['name'] as String? ?? '',
          matchScore: (j['matchScore'] as num?)?.toInt() ?? 0,
          imageUrl: j['headerImageUrl'] as String?,
          currentPrice: (j['currentPrice'] as num?)?.toInt(),
          originalPrice: (j['originalPrice'] as num?)?.toInt(),
          discountPercent: (j['discountPercent'] as num?)?.toInt(),
          currency: j['currency'] as String?,
          isFree: j['isFree'] as bool? ?? false,
          releaseDate: j['releaseDate'] as String?,
          releaseDateText: j['releaseDateText'] as String?,
          comingSoon: j['comingSoon'] as bool? ?? false,
          singlePlayer: j['singlePlayer'] as bool?,
          multiplayer: j['multiplayer'] as bool?,
          onlineCoop: j['onlineCoop'] as bool?,
          maxPlayers: (j['maxPlayers'] as num?)?.toInt(),
          genres: (j['genres'] as List<dynamic>? ?? const [])
              .map((v) => v.toString())
              .toList(),
          storeUrl: j['storeUrl'] as String? ?? '');
}

class GameFinderTagSearchResult {
  const GameFinderTagSearchResult({
    required this.appId,
    required this.name,
    required this.canonicalTags,
    required this.storeUrl,
    this.imageUrl,
    this.currentPrice,
    this.originalPrice,
    this.discountPercent,
    this.isFree = false,
    this.multiplayer,
    this.onlineCoop,
    this.minPlayers,
    this.maxPlayers,
    this.comingSoon = false,
  });

  final int appId;
  final String name, storeUrl;
  final String? imageUrl;
  final int? currentPrice, originalPrice, discountPercent, minPlayers, maxPlayers;
  final bool isFree, comingSoon;
  final bool? multiplayer, onlineCoop;
  final List<String> canonicalTags;

  factory GameFinderTagSearchResult.fromJson(Map<String, dynamic> json) =>
      GameFinderTagSearchResult(
        appId: (json['steamAppId'] as num).toInt(),
        name: json['name'] as String? ?? '',
        imageUrl: json['headerImageUrl'] as String?,
        currentPrice: (json['currentPrice'] as num?)?.toInt(),
        originalPrice: (json['originalPrice'] as num?)?.toInt(),
        discountPercent: (json['discountPercent'] as num?)?.toInt(),
        isFree: json['isFree'] as bool? ?? false,
        multiplayer: json['multiplayer'] as bool?,
        onlineCoop: json['onlineCoop'] as bool?,
        minPlayers: (json['minPlayers'] as num?)?.toInt(),
        maxPlayers: (json['maxPlayers'] as num?)?.toInt(),
        comingSoon: json['comingSoon'] as bool? ?? false,
        canonicalTags: (json['canonicalTags'] as List<dynamic>? ?? const [])
            .map((value) => value.toString())
            .toList(),
        storeUrl: json['storeUrl'] as String? ?? '',
      );
}
