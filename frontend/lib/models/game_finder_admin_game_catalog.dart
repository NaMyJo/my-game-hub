class GameFinderAdminGameCatalogSyncResult {
  const GameFinderAdminGameCatalogSyncResult({
    required this.fetched,
    required this.eligibleCatalogTotal,
    required this.lastAppId,
    required this.discoveredCount,
    required this.completed,
    required this.durationMs,
  });

  final int fetched,
      eligibleCatalogTotal,
      lastAppId,
      discoveredCount,
      durationMs;
  final bool completed;

  factory GameFinderAdminGameCatalogSyncResult.fromJson(
          Map<String, dynamic> json) =>
      GameFinderAdminGameCatalogSyncResult(
        fetched: (json['fetched'] as num?)?.toInt() ?? 0,
        eligibleCatalogTotal:
            (json['eligibleCatalogTotal'] as num?)?.toInt() ?? 0,
        lastAppId: (json['lastAppId'] as num?)?.toInt() ?? 0,
        discoveredCount: (json['discoveredCount'] as num?)?.toInt() ?? 0,
        completed: json['completed'] as bool? ?? false,
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
      );
}

class GameFinderAdminNewGamesSyncResult {
  const GameFinderAdminNewGamesSyncResult({
    required this.fetched,
    required this.upserted,
    required this.newlySaved,
    required this.currentCatalog,
    required this.previousLastAppId,
    required this.currentLastAppId,
    required this.hasMore,
    required this.lastRunAt,
  });

  final int fetched,
      upserted,
      newlySaved,
      currentCatalog,
      previousLastAppId,
      currentLastAppId;
  final bool hasMore;
  final DateTime? lastRunAt;

  factory GameFinderAdminNewGamesSyncResult.fromJson(
          Map<String, dynamic> json) =>
      GameFinderAdminNewGamesSyncResult(
        fetched: (json['fetched'] as num?)?.toInt() ?? 0,
        upserted: (json['upserted'] as num?)?.toInt() ?? 0,
        newlySaved: (json['newlySaved'] as num?)?.toInt() ?? 0,
        currentCatalog: (json['currentCatalog'] as num?)?.toInt() ?? 0,
        previousLastAppId: (json['previousLastAppId'] as num?)?.toInt() ?? 0,
        currentLastAppId: (json['currentLastAppId'] as num?)?.toInt() ?? 0,
        hasMore: json['hasMore'] as bool? ?? false,
        lastRunAt: DateTime.tryParse(json['lastRunAt'] as String? ?? ''),
      );
}
