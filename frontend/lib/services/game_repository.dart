import 'package:flutter/foundation.dart';

import '../models/game_profile.dart';
import 'api_client.dart';

class GameRepository {
  GameRepository._();

  static final GameRepository instance = GameRepository._();

  Future<void> deleteGame(int id) async {
    await ApiClient.instance.delete(
      '/api/me/games/$id',
    );
  }

  Future<List<GameProfile>> getMyGames() async {
    final json = await ApiClient.instance.get(
      '/api/me/games',
    );

    if (json is! List) {
      throw const ApiException(
        '게임 목록 응답 형식이 올바르지 않습니다.',
      );
    }

    return json
        .map(
          (item) => GameProfile.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .toList();
  }

  Future<GameProfile> registerGame({
    required GameType type,
    required String accountName,
  }) async {
    final json = await ApiClient.instance.post(
      '/api/me/games',
      body: {
        'gameType': type.apiValue,
        'accountName': accountName,
      },
    );

    return GameProfile.fromJson(
      json as Map<String, dynamic>,
    );
  }

  Future<GameProfile> refreshGame(int id) async {
    final json = await ApiClient.instance.post(
      '/api/me/games/$id/refresh',
    );

    return GameProfile.fromJson(
      json as Map<String, dynamic>,
    );
  }

  // 게임 카드 순서 저장
  Future<void> reorderGames(
    List<GameProfile> games,
  ) async {
    final ids = games.map((game) => game.id).toList();

    debugPrint('Repository reorderGames 호출: $ids');

    await ApiClient.instance.put(
      '/api/me/games/reorder',
      body: {
        'gameIds': ids,
      },
    );
  }
}
