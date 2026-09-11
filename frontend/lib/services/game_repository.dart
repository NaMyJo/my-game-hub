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
    String? serverId,
    String? platformId,
  }) async {
    dynamic json;
    try {
      json = await ApiClient.instance.post(
        '/api/me/games',
        body: {
          'gameType': type.apiValue,
          'accountName': accountName,
          if (serverId != null && serverId.isNotEmpty) 'serverId': serverId,
          if (platformId != null) 'platformId': platformId,
        },
      );
    } on ApiException catch (error) {
      if (_isMissingUser(error.message)) {
        throw ApiException(
          _missingUserMessage(type),
          statusCode: error.statusCode,
        );
      }
      rethrow;
    }

    return GameProfile.fromJson(
      json as Map<String, dynamic>,
    );
  }

  bool _isMissingUser(String message) {
    final indicatesMissing =
        message.contains('찾을 수 없') || message.contains('존재하지 않는');
    final identifiesUser = message.contains('유저') ||
        message.contains('계정') ||
        message.contains('캐릭터') ||
        message.contains('플레이어') ||
        message.contains('프로필') ||
        message.contains('Riot ID');
    final describesGameData = message.contains('랭크') ||
        message.contains('시즌') ||
        message.contains('스탯') ||
        message.contains('UID');
    return indicatesMissing && identifiesUser && !describesGameData;
  }

  String _missingUserMessage(GameType type) => switch (type) {
        GameType.lostArk => '로스트아크 유저를 찾을 수 없습니다.',
        GameType.leagueOfLegends => '리그 오브 레전드 유저를 찾을 수 없습니다.',
        GameType.tft => 'TFT 유저를 찾을 수 없습니다.',
        GameType.eternalReturn => '이터널 리턴 유저를 찾을 수 없습니다.',
        GameType.mapleStory => '메이플스토리 유저를 찾을 수 없습니다.',
        GameType.dungeonFighter => '던전앤파이터 유저를 찾을 수 없습니다.',
        GameType.battlegrounds => '배틀그라운드 유저를 찾을 수 없습니다.',
        GameType.valorant => '발로란트 유저를 찾을 수 없습니다.',
      };

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
