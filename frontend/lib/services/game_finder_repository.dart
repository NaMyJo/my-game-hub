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
      required List<String> preferredTags,
      required int priceMin,
      required int priceMax,
      required bool includeAdult,
      required int playerMin,
      required int playerMax,
      required GameFinderPlayMode playMode,
      required GameFinderPriceMode priceMode,
      required GameFinderReleasePreference releasePreference,
      required Set<int> excluded}) async {
    final json =
        await ApiClient.instance.post('/api/game-finder/recommend', body: {
      'likedSteamAppIds': likedIds,
      'preferredTags': preferredTags,
      'priceMin': priceMin,
      'priceMax': priceMax,
      'includeAdult': includeAdult,
      'playerMin': playerMin,
      'playerMax': playerMax,
      'playMode': playMode.apiValue,
      'priceMode': priceMode.apiValue,
      'releasePreference': releasePreference.apiValue,
      'excludeAppIds': excluded.toList()
    }) as List<dynamic>;
    return json
        .map(
            (v) => GameFinderRecommendation.fromJson(v as Map<String, dynamic>))
        .toList();
  }

  Future<List<GameFinderTag>> tags({String query = ''}) async {
    final json = await ApiClient.instance.get(
            '/api/game-finder/v1/tags?q=${Uri.encodeQueryComponent(query)}&size=100')
        as Map<String, dynamic>;
    return (json['items'] as List<dynamic>? ?? const [])
        .map((v) => GameFinderTag.fromJson(v as Map<String, dynamic>))
        .toList();
  }

  Future<GameFinderPreferences> preferences() async =>
      GameFinderPreferences.fromJson(await ApiClient.instance
          .get('/api/game-finder/v1/me/preferences') as Map<String, dynamic>);

  Future<GameFinderPreferences> savePreferences(
          {required List<int> selectedIds,
          required List<String> preferredTags,
          required int priceMin,
          required int priceMax,
          required bool includeAdult,
          required int playerMin,
          required int playerMax,
          required GameFinderReleasePreference releasePreference}) async =>
      GameFinderPreferences.fromJson(await ApiClient.instance
          .put('/api/game-finder/v1/me/preferences', body: {
        'selectedSteamAppIds': selectedIds,
        'preferredTags': preferredTags,
        'priceMin': priceMin,
        'priceMax': priceMax,
        'includeAdult': includeAdult,
        'playerMin': playerMin,
        'playerMax': playerMax,
        'releasePreference': releasePreference.apiValue
      }) as Map<String, dynamic>);

  Future<void> removeRecentGame(int appId) =>
      ApiClient.instance.delete('/api/game-finder/v1/me/recent-games/$appId');

  Future<List<GameFinderTagSearchResult>> searchByTags({
    String query = '',
    List<String> tags = const [],
    required int priceMin,
    required int priceMax,
    required bool includeAdult,
    required int playerMin,
    required int playerMax,
    required GameFinderPlayMode playMode,
    required GameFinderPriceMode priceMode,
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
      'playMode': playMode.apiValue,
      'priceMode': priceMode.apiValue,
      'page': page,
      'size': size,
    }) as List<dynamic>;
    return json
        .map((value) =>
            GameFinderTagSearchResult.fromJson(value as Map<String, dynamic>))
        .toList();
  }
}
