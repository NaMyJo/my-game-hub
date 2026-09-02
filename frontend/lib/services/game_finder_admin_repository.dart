import '../models/game_finder_admin.dart';
import '../models/game_finder_admin_game_catalog.dart';
import 'api_client.dart';

class GameFinderAdminRepository {
  GameFinderAdminRepository();
  static final instance = GameFinderAdminRepository();

  Future<bool> isAdmin() async {
    final json = await ApiClient.instance.get('/api/admin/game-finder/me')
        as Map<String, dynamic>;
    return json['admin'] as bool? ?? false;
  }

  Future<GameFinderAdminStatus> status() async {
    final json = await ApiClient.instance.get('/api/admin/game-finder/status')
        as Map<String, dynamic>;
    return GameFinderAdminStatus.fromJson(json);
  }

  Future<GameFinderAdminEnrichResult> enrich(int batchSize) async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/enrich',
      body: {'batchSize': batchSize},
    ) as Map<String, dynamic>;
    return GameFinderAdminEnrichResult.fromJson(json);
  }

  Future<GameFinderAdminStageEnrichResult> enrichMetadata(int batchSize) async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/enrich/metadata',
      body: {'batchSize': batchSize},
    ) as Map<String, dynamic>;
    return GameFinderAdminStageEnrichResult.fromJson(json);
  }

  Future<GameFinderAdminStageEnrichResult> enrichIgdb(int batchSize) async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/enrich/igdb',
      body: {'batchSize': batchSize},
    ) as Map<String, dynamic>;
    return GameFinderAdminStageEnrichResult.fromJson(json);
  }

  Future<GameFinderAdminMetadataVerifyResult> verifyMetadata(
      int sampleSize, String mode) async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/metadata/verify',
      body: {'sampleSize': sampleSize, 'mode': mode},
    ) as Map<String, dynamic>;
    return GameFinderAdminMetadataVerifyResult.fromJson(json);
  }

  Future<GameFinderAdminCatalogExpandResult> expandCatalog(
      int targetTotal) async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/catalog/expand',
      body: {'targetTotal': targetTotal},
    ) as Map<String, dynamic>;
    return GameFinderAdminCatalogExpandResult.fromJson(json);
  }

  Future<GameFinderAdminFullCatalogSyncResult> syncNextFullCatalogPage() async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/catalog/full-sync',
    ) as Map<String, dynamic>;
    return GameFinderAdminFullCatalogSyncResult.fromJson(json);
  }

  Future<GameFinderAdminGameCatalogSyncResult> syncNextGameCatalogPage() async {
    final json = await ApiClient.instance.post(
      '/api/admin/game-finder/catalog/game-only-sync',
    ) as Map<String, dynamic>;
    return GameFinderAdminGameCatalogSyncResult.fromJson(json);
  }
}
