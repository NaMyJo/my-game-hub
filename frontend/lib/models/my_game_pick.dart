class MyGamePick {
  const MyGamePick({
    required this.appId,
    required this.name,
    required this.canonicalTags,
    required this.storeUrl,
    this.imageUrl,
    this.currentPrice,
    this.originalPrice,
    this.discountPercent,
    this.currency,
    this.isFree = false,
    this.releaseDate,
    this.releaseDateText,
    this.comingSoon = false,
    this.playerSummary = '',
  });

  final int appId;
  final String name, storeUrl, playerSummary;
  final String? imageUrl, currency, releaseDate, releaseDateText;
  final int? currentPrice, originalPrice, discountPercent;
  final bool isFree, comingSoon;
  final List<String> canonicalTags;

  factory MyGamePick.fromJson(Map<String, dynamic> json) => MyGamePick(
        appId: (json['steamAppId'] as num).toInt(),
        name: json['name'] as String? ?? '',
        imageUrl: json['headerImageUrl'] as String?,
        currentPrice: (json['currentPrice'] as num?)?.toInt(),
        originalPrice: (json['originalPrice'] as num?)?.toInt(),
        discountPercent: (json['discountPercent'] as num?)?.toInt(),
        currency: json['currency'] as String?,
        isFree: json['isFree'] as bool? ?? false,
        releaseDate: json['releaseDate'] as String?,
        releaseDateText: json['releaseDateText'] as String?,
        comingSoon: json['comingSoon'] as bool? ?? false,
        playerSummary: json['playerSummary'] as String? ?? '',
        canonicalTags: (json['canonicalTags'] as List<dynamic>? ?? const [])
            .map((value) => value.toString())
            .toList(),
        storeUrl: json['storeUrl'] as String? ?? '',
      );
}
