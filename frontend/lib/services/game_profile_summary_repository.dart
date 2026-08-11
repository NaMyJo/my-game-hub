import '../models/game_profile_summary.dart';
import 'api_client.dart';

class GameProfileSummaryRepository {
  GameProfileSummaryRepository._();

  static final GameProfileSummaryRepository instance =
      GameProfileSummaryRepository._();

  Future<GameProfileSummary?> getProfile() async {
    try {
      final json = await ApiClient.instance.get(
        '/api/me/game-profile',
      );

      if (json == null) {
        return null;
      }

      return GameProfileSummary.fromJson(
        json as Map<String, dynamic>,
      );
    } on ApiException catch (error) {
      /*
       * 아직 게임 신분증을 프로필에 반영하지 않은 사용자.
       * 오류가 아니라 정상 상태.
       */
      if (error.statusCode == 404) {
        return null;
      }

      rethrow;
    }
  }

  Future<GameProfileSummary> saveProfile({
    required String identityNickname,
    required double? gamePowerPercent,
    required int reflectedGameCount,
    required String? evaluationMessage,
  }) async {
    final json = await ApiClient.instance.put(
      '/api/me/game-profile',
      body: {
        'identityNickname': identityNickname,
        'gamePowerPercent': gamePowerPercent,
        'reflectedGameCount': reflectedGameCount,
        'evaluationMessage': evaluationMessage,
      },
    );

    return GameProfileSummary.fromJson(
      json as Map<String, dynamic>,
    );
  }
}
