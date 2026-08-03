import '../models/game_identity_preview.dart';
import 'api_client.dart';

class GameIdentityRepository {
  GameIdentityRepository._();

  static final GameIdentityRepository instance = GameIdentityRepository._();

  Future<GameIdentityPreviewResult> preview({
    required String displayName,
    required List<int> gameAccountIds,
  }) async {
    final json = await ApiClient.instance.post(
      '/api/me/game-identities/preview',
      body: {
        'displayName': displayName,
        'gameAccountIds': gameAccountIds,
      },
    );

    if (json is! Map<String, dynamic>) {
      throw const ApiException(
        '게임 신분증 미리보기 응답 형식이 올바르지 않습니다.',
      );
    }

    return GameIdentityPreviewResult.fromJson(json);
  }
}
