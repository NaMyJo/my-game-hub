class GameFinderAdminGameCatalogSyncResult {
  const GameFinderAdminGameCatalogSyncResult({
    required this.fetched,
    required this.eligibleCatalogTotal,
    required this.lastAppId,
    required this.discoveredCount,
    required this.completed,
    required this.durationMs,
  });

  final int fetched, eligibleCatalogTotal, lastAppId, discoveredCount, durationMs;
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
