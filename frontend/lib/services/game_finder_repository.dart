import '../models/game_finder.dart';
import 'api_client.dart';

class GameFinderRepository {
  GameFinderRepository._();
  static final instance = GameFinderRepository._();
  Future<List<SteamGameSearchItem>> search(String query) async {
    final json = await ApiClient.instance
            .get('/api/game-finder/search?q=${Uri.encodeQueryComponent(query)}')
        as List<dynamic>;
    return json
        .map((v) => SteamGameSearchItem.fromJson(v as Map<String, dynamic>))
        .toList();
  }

  Future<List<GameFinderRecommendation>> recommend(
      {required List<int> likedIds,
      required int priceMin,
      required int priceMax,
      required bool includeAdult,
      required int playerMin,
      required int playerMax,
      required Set<int> excluded}) async {
    final json =
        await ApiClient.instance.post('/api/game-finder/recommend', body: {
      'likedSteamAppIds': likedIds,
      'priceMin': priceMin,
      'priceMax': priceMax,
      'includeAdult': includeAdult,
      'playerMin': playerMin,
      'playerMax': playerMax,
      'excludeAppIds': excluded.toList()
    }) as List<dynamic>;
    return json
        .map(
            (v) => GameFinderRecommendation.fromJson(v as Map<String, dynamic>))
        .toList();
  }

  Future<List<GameFinderTagSearchResult>> searchByTags({
    String query = '',
    List<String> tags = const [],
    required int priceMin,
    required int priceMax,
    required bool includeAdult,
    required int playerMin,
    required int playerMax,
    int page = 0,
    int size = 20,
  }) async {
    final json =
        await ApiClient.instance.post('/api/game-finder/search', body: {
      'query': query,
      'tags': tags,
      'priceMin': priceMin,
      'priceMax': priceMax,
      'includeAdult': includeAdult,
      'playerMin': playerMin,
      'playerMax': playerMax,
      'page': page,
      'size': size,
    }) as List<dynamic>;
    return json
        .map((value) => GameFinderTagSearchResult.fromJson(
            value as Map<String, dynamic>))
        .toList();
  }
}
