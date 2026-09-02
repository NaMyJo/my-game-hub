class GameFinderAdminStatus {
  const GameFinderAdminStatus({
    required this.total,
    required this.active,
    required this.unavailable,
    required this.removed,
    required this.metadata,
    required this.igdb,
    required this.checkpoint,
    required this.fullCatalogSync,
    required this.gameOnlyCatalogSync,
    required this.remainingCandidates,
    required this.gameCatalogCount,
    required this.gameCount,
    required this.nonGameCount,
    required this.unclassifiedCount,
  });

  final int total, active, unavailable, removed;
  final GameFinderEnrichmentCounts metadata, igdb;
  final GameFinderCatalogCheckpoint checkpoint;
  final GameFinderFullCatalogSyncStatus fullCatalogSync;
  final GameFinderFullCatalogSyncStatus gameOnlyCatalogSync;
  final int remainingCandidates;
  final int gameCatalogCount;
  final int gameCount, nonGameCount, unclassifiedCount;

  factory GameFinderAdminStatus.fromJson(Map<String, dynamic> json) =>
      GameFinderAdminStatus(
        total: (json['total'] as num?)?.toInt() ?? 0,
        active: (json['active'] as num?)?.toInt() ?? 0,
        unavailable: (json['unavailable'] as num?)?.toInt() ?? 0,
        removed: (json['removed'] as num?)?.toInt() ?? 0,
        metadata: GameFinderEnrichmentCounts.fromJson(
            json['metadata'] as Map<String, dynamic>? ?? const {}),
        igdb: GameFinderEnrichmentCounts.fromJson(
            json['igdb'] as Map<String, dynamic>? ?? const {}),
        checkpoint: GameFinderCatalogCheckpoint.fromJson(
            json['checkpoint'] as Map<String, dynamic>? ?? const {}),
        fullCatalogSync: GameFinderFullCatalogSyncStatus.fromJson(
            json['fullCatalogSync'] as Map<String, dynamic>? ?? const {}),
        gameOnlyCatalogSync: GameFinderFullCatalogSyncStatus.fromJson(
            json['gameOnlyCatalogSync'] as Map<String, dynamic>? ?? const {}),
        remainingCandidates:
            (json['remainingCandidates'] as num?)?.toInt() ?? 0,
        gameCatalogCount: (json['gameCatalogCount'] as num?)?.toInt() ?? 0,
        gameCount: (json['gameCount'] as num?)?.toInt() ?? 0,
        nonGameCount: (json['nonGameCount'] as num?)?.toInt() ?? 0,
        unclassifiedCount: (json['unclassifiedCount'] as num?)?.toInt() ?? 0,
      );
}

class GameFinderFullCatalogSyncStatus {
  const GameFinderFullCatalogSyncStatus({
    required this.status,
    required this.lastAppId,
    required this.discoveredCount,
    required this.lastSuccessfulRunAt,
    required this.completed,
    required this.hasFailure,
  });

  final String status;
  final int lastAppId, discoveredCount;
  final DateTime? lastSuccessfulRunAt;
  final bool completed, hasFailure;

  factory GameFinderFullCatalogSyncStatus.fromJson(Map<String, dynamic> json) =>
      GameFinderFullCatalogSyncStatus(
        status: json['status'] as String? ?? 'NEW',
        lastAppId: (json['lastAppId'] as num?)?.toInt() ?? 0,
        discoveredCount: (json['discoveredCount'] as num?)?.toInt() ?? 0,
        lastSuccessfulRunAt: DateTime.tryParse(
            json['lastSuccessfulRunAt'] as String? ?? ''),
        completed: json['completed'] as bool? ?? false,
        hasFailure: json['hasFailure'] as bool? ?? false,
      );
}

class GameFinderAdminFullCatalogSyncResult {
  const GameFinderAdminFullCatalogSyncResult({
    required this.fetched,
    required this.newlySaved,
    required this.currentCatalogTotal,
    required this.lastAppId,
    required this.discoveredCount,
    required this.completed,
    required this.durationMs,
  });

  final int fetched, newlySaved, currentCatalogTotal;
  final int lastAppId, discoveredCount, durationMs;
  final bool completed;

  factory GameFinderAdminFullCatalogSyncResult.fromJson(
          Map<String, dynamic> json) =>
      GameFinderAdminFullCatalogSyncResult(
        fetched: (json['fetched'] as num?)?.toInt() ?? 0,
        newlySaved: (json['newlySaved'] as num?)?.toInt() ?? 0,
        currentCatalogTotal:
            (json['currentCatalogTotal'] as num?)?.toInt() ?? 0,
        lastAppId: (json['lastAppId'] as num?)?.toInt() ?? 0,
        discoveredCount: (json['discoveredCount'] as num?)?.toInt() ?? 0,
        completed: json['completed'] as bool? ?? false,
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
      );
}

class GameFinderCatalogCheckpoint {
  const GameFinderCatalogCheckpoint({
    required this.lastAppId,
    required this.lastSuccessfulSyncAt,
    required this.status,
    required this.hasFailure,
  });

  final int lastAppId;
  final DateTime? lastSuccessfulSyncAt;
  final String status;
  final bool hasFailure;

  factory GameFinderCatalogCheckpoint.fromJson(Map<String, dynamic> json) =>
      GameFinderCatalogCheckpoint(
        lastAppId: (json['lastAppId'] as num?)?.toInt() ?? 0,
        lastSuccessfulSyncAt: DateTime.tryParse(
            json['lastSuccessfulSyncAt'] as String? ?? ''),
        status: json['status'] as String? ?? 'NEW',
        hasFailure: json['hasFailure'] as bool? ?? false,
      );
}

class GameFinderAdminCatalogExpandResult {
  const GameFinderAdminCatalogExpandResult({
    required this.fetched,
    required this.upserted,
    required this.newlySaved,
    required this.currentTotal,
    required this.targetTotal,
    required this.targetReached,
    required this.durationMs,
  });

  final int fetched, upserted, newlySaved, currentTotal, targetTotal, durationMs;
  final bool targetReached;

  factory GameFinderAdminCatalogExpandResult.fromJson(
          Map<String, dynamic> json) =>
      GameFinderAdminCatalogExpandResult(
        fetched: (json['fetched'] as num?)?.toInt() ?? 0,
        upserted: (json['upserted'] as num?)?.toInt() ?? 0,
        newlySaved: (json['newlySaved'] as num?)?.toInt() ?? 0,
        currentTotal: (json['currentTotal'] as num?)?.toInt() ?? 0,
        targetTotal: (json['targetTotal'] as num?)?.toInt() ?? 0,
        targetReached: json['targetReached'] as bool? ?? false,
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
      );
}

class GameFinderEnrichmentCounts {
  const GameFinderEnrichmentCounts({
    required this.pending,
    required this.success,
    required this.notFound,
    required this.retryableFailure,
    required this.permanentFailure,
  });

  final int pending, success, notFound, retryableFailure, permanentFailure;

  int get failures => retryableFailure + permanentFailure;

  factory GameFinderEnrichmentCounts.fromJson(Map<String, dynamic> json) =>
      GameFinderEnrichmentCounts(
        pending: (json['pending'] as num?)?.toInt() ?? 0,
        success: (json['success'] as num?)?.toInt() ?? 0,
        notFound: (json['notFound'] as num?)?.toInt() ?? 0,
        retryableFailure:
            (json['retryableFailure'] as num?)?.toInt() ?? 0,
        permanentFailure:
            (json['permanentFailure'] as num?)?.toInt() ?? 0,
      );
}

class GameFinderAdminEnrichResult {
  const GameFinderAdminEnrichResult({
    required this.requestedBatchSize,
    required this.processed,
    required this.metadataSuccess,
    required this.metadataNotFound,
    required this.metadataRetryableFailure,
    required this.metadataPermanentFailure,
    required this.igdbSuccess,
    required this.igdbNotFound,
    required this.igdbRetryableFailure,
    required this.igdbPermanentFailure,
    required this.hasMoreCandidates,
    required this.durationMs,
  });

  final int requestedBatchSize, processed, durationMs;
  final int metadataSuccess, metadataNotFound;
  final int metadataRetryableFailure, metadataPermanentFailure;
  final int igdbSuccess, igdbNotFound;
  final int igdbRetryableFailure, igdbPermanentFailure;
  final bool hasMoreCandidates;

  int get failures => metadataRetryableFailure +
      metadataPermanentFailure +
      igdbRetryableFailure +
      igdbPermanentFailure;

  factory GameFinderAdminEnrichResult.fromJson(Map<String, dynamic> json) =>
      GameFinderAdminEnrichResult(
        requestedBatchSize:
            (json['requestedBatchSize'] as num?)?.toInt() ?? 0,
        processed: (json['processed'] as num?)?.toInt() ?? 0,
        metadataSuccess: (json['metadataSuccess'] as num?)?.toInt() ?? 0,
        metadataNotFound: (json['metadataNotFound'] as num?)?.toInt() ?? 0,
        metadataRetryableFailure:
            (json['metadataRetryableFailure'] as num?)?.toInt() ?? 0,
        metadataPermanentFailure:
            (json['metadataPermanentFailure'] as num?)?.toInt() ?? 0,
        igdbSuccess: (json['igdbSuccess'] as num?)?.toInt() ?? 0,
        igdbNotFound: (json['igdbNotFound'] as num?)?.toInt() ?? 0,
        igdbRetryableFailure:
            (json['igdbRetryableFailure'] as num?)?.toInt() ?? 0,
        igdbPermanentFailure:
            (json['igdbPermanentFailure'] as num?)?.toInt() ?? 0,
        hasMoreCandidates: json['hasMoreCandidates'] as bool? ?? false,
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
      );
}
