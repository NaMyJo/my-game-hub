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
    required this.remainingMetadataCandidates,
    required this.remainingIgdbCandidates,
    required this.gameCatalogCount,
    required this.metadataTerminalCount,
    required this.storeUnavailableCount,
    required this.igdbTargetCount,
    required this.igdbTerminalCount,
    required this.finderEligibleCount,
    required this.gameCount,
    required this.nonGameCount,
    required this.unclassifiedCount,
    this.metadataConcurrency = 1,
    this.metadataRequestDelayMs = 500,
  });

  final int total, active, unavailable, removed;
  final GameFinderEnrichmentCounts metadata, igdb;
  final GameFinderCatalogCheckpoint checkpoint;
  final GameFinderFullCatalogSyncStatus fullCatalogSync;
  final GameFinderFullCatalogSyncStatus gameOnlyCatalogSync;
  final int remainingCandidates;
  final int remainingMetadataCandidates, remainingIgdbCandidates;
  final int gameCatalogCount;
  final int metadataTerminalCount, storeUnavailableCount;
  final int igdbTargetCount, igdbTerminalCount, finderEligibleCount;
  final int gameCount, nonGameCount, unclassifiedCount;
  final int metadataConcurrency, metadataRequestDelayMs;

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
        remainingMetadataCandidates:
            (json['remainingMetadataCandidates'] as num?)?.toInt() ?? 0,
        remainingIgdbCandidates:
            (json['remainingIgdbCandidates'] as num?)?.toInt() ?? 0,
        gameCatalogCount: (json['gameCatalogCount'] as num?)?.toInt() ?? 0,
        metadataTerminalCount:
            (json['metadataTerminalCount'] as num?)?.toInt() ?? 0,
        storeUnavailableCount:
            (json['storeUnavailableCount'] as num?)?.toInt() ?? 0,
        igdbTargetCount: (json['igdbTargetCount'] as num?)?.toInt() ?? 0,
        igdbTerminalCount:
            (json['igdbTerminalCount'] as num?)?.toInt() ?? 0,
        finderEligibleCount:
            (json['finderEligibleCount'] as num?)?.toInt() ?? 0,
        gameCount: (json['gameCount'] as num?)?.toInt() ?? 0,
        nonGameCount: (json['nonGameCount'] as num?)?.toInt() ?? 0,
        unclassifiedCount: (json['unclassifiedCount'] as num?)?.toInt() ?? 0,
        metadataConcurrency:
            ((json['metadataRuntimeConfig']
                        as Map<String, dynamic>?)?['concurrency'] as num?)
                    ?.toInt() ??
                1,
        metadataRequestDelayMs:
            ((json['metadataRuntimeConfig']
                        as Map<String, dynamic>?)?['requestDelayMs'] as num?)
                    ?.toInt() ??
                500,
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

class GameFinderAdminStageEnrichResult {
  const GameFinderAdminStageEnrichResult({
    required this.stage,
    required this.requestedBatchSize,
    required this.processed,
    required this.success,
    required this.notFound,
    required this.retryableFailure,
    required this.permanentFailure,
    required this.hasMoreCandidates,
    required this.rateLimited,
    required this.durationMs,
  });

  final String stage;
  final int requestedBatchSize, processed, success, notFound;
  final int retryableFailure, permanentFailure, durationMs;
  final bool hasMoreCandidates;
  final bool rateLimited;

  double get itemsPerSecond =>
      durationMs <= 0 ? 0 : processed * 1000 / durationMs;

  factory GameFinderAdminStageEnrichResult.fromJson(Map<String, dynamic> json) =>
      GameFinderAdminStageEnrichResult(
        stage: json['stage'] as String? ?? '',
        requestedBatchSize:
            (json['requestedBatchSize'] as num?)?.toInt() ?? 0,
        processed: (json['processed'] as num?)?.toInt() ?? 0,
        success: (json['success'] as num?)?.toInt() ?? 0,
        notFound: (json['notFound'] as num?)?.toInt() ?? 0,
        retryableFailure:
            (json['retryableFailure'] as num?)?.toInt() ?? 0,
        permanentFailure:
            (json['permanentFailure'] as num?)?.toInt() ?? 0,
        hasMoreCandidates: json['hasMoreCandidates'] as bool? ?? false,
        rateLimited: json['rateLimited'] as bool? ?? false,
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
      );
}

class GameFinderAdminMetadataVerifyResult {
  const GameFinderAdminMetadataVerifyResult({
    required this.sampled,
    required this.matched,
    required this.changed,
    required this.criticalMismatch,
    required this.storeUnavailable,
    required this.verificationError,
    required this.durationMs,
    required this.criticalDetails,
  });

  final int sampled, matched, changed, criticalMismatch;
  final int storeUnavailable, verificationError, durationMs;
  final List<GameFinderMetadataCriticalDetail> criticalDetails;

  factory GameFinderAdminMetadataVerifyResult.fromJson(
          Map<String, dynamic> json) =>
      GameFinderAdminMetadataVerifyResult(
        sampled: (json['sampled'] as num?)?.toInt() ?? 0,
        matched: (json['matched'] as num?)?.toInt() ?? 0,
        changed: (json['changed'] as num?)?.toInt() ?? 0,
        criticalMismatch:
            (json['criticalMismatch'] as num?)?.toInt() ?? 0,
        storeUnavailable:
            (json['storeUnavailable'] as num?)?.toInt() ?? 0,
        verificationError:
            (json['verificationError'] as num?)?.toInt() ?? 0,
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
        criticalDetails: (json['criticalDetails'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(GameFinderMetadataCriticalDetail.fromJson)
            .toList(growable: false),
      );
}

class GameFinderMetadataRunnerStatus {
  const GameFinderMetadataRunnerStatus({
    required this.status,
    required this.processedCount,
    required this.successCount,
    required this.notFoundCount,
    required this.retryableFailureCount,
    required this.permanentFailureCount,
    required this.consecutiveRateLimitCount,
    required this.remainingMetadataCandidates,
    required this.cooldownRetryableCount,
    required this.initialPopulationComplete,
    this.startedAt,
    this.updatedAt,
    this.nextRunAt,
    this.lastBatchDurationMs,
    this.lastError,
  });

  final String status;
  final int processedCount, successCount, notFoundCount;
  final int retryableFailureCount, permanentFailureCount;
  final int consecutiveRateLimitCount, remainingMetadataCandidates;
  final int cooldownRetryableCount;
  final bool initialPopulationComplete;
  final DateTime? startedAt, updatedAt, nextRunAt;
  final int? lastBatchDurationMs;
  final String? lastError;

  bool get active => const {
        'RUNNING', 'WAITING_RATE_LIMIT', 'WAITING_RETRY', 'STOP_REQUESTED'
      }.contains(status);

  factory GameFinderMetadataRunnerStatus.fromJson(Map<String, dynamic> json) =>
      GameFinderMetadataRunnerStatus(
        status: json['status'] as String? ?? 'IDLE',
        processedCount: (json['processedCount'] as num?)?.toInt() ?? 0,
        successCount: (json['successCount'] as num?)?.toInt() ?? 0,
        notFoundCount: (json['notFoundCount'] as num?)?.toInt() ?? 0,
        retryableFailureCount:
            (json['retryableFailureCount'] as num?)?.toInt() ?? 0,
        permanentFailureCount:
            (json['permanentFailureCount'] as num?)?.toInt() ?? 0,
        consecutiveRateLimitCount:
            (json['consecutiveRateLimitCount'] as num?)?.toInt() ?? 0,
        remainingMetadataCandidates:
            (json['remainingMetadataCandidates'] as num?)?.toInt() ?? 0,
        cooldownRetryableCount:
            (json['cooldownRetryableCount'] as num?)?.toInt() ?? 0,
        initialPopulationComplete:
            json['initialPopulationComplete'] as bool? ?? false,
        startedAt: DateTime.tryParse(json['startedAt'] as String? ?? ''),
        updatedAt: DateTime.tryParse(json['updatedAt'] as String? ?? ''),
        nextRunAt: DateTime.tryParse(json['nextRunAt'] as String? ?? ''),
        lastBatchDurationMs: (json['lastBatchDurationMs'] as num?)?.toInt(),
        lastError: json['lastError'] as String?,
      );
}

class GameFinderMetadataCriticalDetail {
  const GameFinderMetadataCriticalDetail({
    required this.steamAppId,
    required this.dbName,
    required this.responseAppId,
    required this.responseName,
    required this.mismatchedFields,
  });

  final int steamAppId;
  final String dbName;
  final int? responseAppId;
  final String? responseName;
  final List<String> mismatchedFields;

  factory GameFinderMetadataCriticalDetail.fromJson(
          Map<String, dynamic> json) =>
      GameFinderMetadataCriticalDetail(
        steamAppId: (json['steamAppId'] as num?)?.toInt() ?? 0,
        dbName: json['dbName'] as String? ?? '',
        responseAppId: (json['responseAppId'] as num?)?.toInt(),
        responseName: json['responseName'] as String?,
        mismatchedFields: (json['mismatchedFields'] as List<dynamic>? ?? const [])
            .whereType<String>()
            .toList(growable: false),
      );
}
