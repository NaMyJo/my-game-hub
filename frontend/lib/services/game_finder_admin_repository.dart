import '../models/game_finder_admin.dart';
import 'api_client.dart';

class GameFinderAdminRepository {
  GameFinderAdminRepository._();
  static final instance = GameFinderAdminRepository._();

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
}
