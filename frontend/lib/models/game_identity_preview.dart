import 'game_profile.dart';

class GameIdentityPreviewEntry {
  const GameIdentityPreviewEntry({
    required this.gameAccountId,
    required this.gameType,
    required this.accountName,
    required this.metricLabel,
    required this.metricValue,
    required this.topPercent,
    required this.includedInAverage,
    required this.estimated,
  });

  final int gameAccountId;
  final GameType gameType;
  final String accountName;
  final String metricLabel;
  final String metricValue;
  final double? topPercent;
  final bool includedInAverage;
  final bool estimated;

  factory GameIdentityPreviewEntry.fromJson(
    Map<String, dynamic> json,
  ) {
    return GameIdentityPreviewEntry(
      gameAccountId: (json['gameAccountId'] as num).toInt(),
      gameType: GameTypeX.fromApiValue(
        json['gameType'] as String,
      ),
      accountName: json['accountName'] as String? ?? '',
      metricLabel: json['metricLabel'] as String? ?? '-',
      metricValue: json['metricValue'] as String? ?? '-',
      topPercent: (json['topPercent'] as num?)?.toDouble(),
      includedInAverage: json['includedInAverage'] as bool? ?? false,
      estimated: json['estimated'] as bool? ?? false,
    );
  }
}

class GameIdentityPreviewResult {
  const GameIdentityPreviewResult({
    required this.displayName,
    required this.averageTopPercent,
    required this.evaluationType,
    required this.evaluationMessage,
    required this.includedGameCount,
    required this.games,
  });

  final String displayName;
  final double? averageTopPercent;
  final String evaluationType;
  final String evaluationMessage;
  final int includedGameCount;
  final List<GameIdentityPreviewEntry> games;

  factory GameIdentityPreviewResult.fromJson(
    Map<String, dynamic> json,
  ) {
    final rawGames = json['games'] as List<dynamic>? ?? const [];

    return GameIdentityPreviewResult(
      displayName: json['displayName'] as String? ?? '',
      averageTopPercent: (json['averageTopPercent'] as num?)?.toDouble(),
      evaluationType: json['evaluationType'] as String? ?? 'RPG_ONLY',
      evaluationMessage: json['evaluationMessage'] as String? ?? '',
      includedGameCount: (json['includedGameCount'] as num?)?.toInt() ?? 0,
      games: rawGames
          .map(
            (item) => GameIdentityPreviewEntry.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(),
    );
  }
}
