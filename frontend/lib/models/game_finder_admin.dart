class GameFinderAdminStatus {
  const GameFinderAdminStatus({
    required this.total,
    required this.active,
    required this.unavailable,
    required this.removed,
    required this.metadata,
    required this.igdb,
  });

  final int total, active, unavailable, removed;
  final GameFinderEnrichmentCounts metadata, igdb;

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
    required this.durationMs,
  });

  final int requestedBatchSize, processed, durationMs;
  final int metadataSuccess, metadataNotFound;
  final int metadataRetryableFailure, metadataPermanentFailure;
  final int igdbSuccess, igdbNotFound;
  final int igdbRetryableFailure, igdbPermanentFailure;

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
        durationMs: (json['durationMs'] as num?)?.toInt() ?? 0,
      );
}
