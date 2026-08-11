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

  Future<Map<String, dynamic>?> getLatest() async {
    try {
      final json = await ApiClient.instance.get(
        '/api/me/game-identities/latest',
      );

      if (json == null) {
        return null;
      }

      if (json is! Map<String, dynamic>) {
        throw const ApiException(
          '최근 게임 신분증 응답 형식이 올바르지 않습니다.',
        );
      }

      return json;
    } on ApiException catch (error) {
      // 최근 신분증이 아직 없어서 404라면 정상 상태
      if (error.statusCode == 404) {
        return null;
      }

      rethrow;
    }
  }

  Future<Map<String, dynamic>> saveLatest({
    required String identityNumber,
    required String displayName,
    required String issuedDate,
    required double? gamePowerPercent,
    required String evaluationMessage,
    required String snapshotJson,
  }) async {
    final json = await ApiClient.instance.put(
      '/api/me/game-identities/latest',
      body: {
        'identityNumber': identityNumber,
        'displayName': displayName,
        'issuedDate': issuedDate,
        'gamePowerPercent': gamePowerPercent,
        'evaluationMessage': evaluationMessage,
        'snapshotJson': snapshotJson,
      },
    );

    if (json is! Map<String, dynamic>) {
      throw const ApiException(
        '최근 게임 신분증 저장 응답 형식이 올바르지 않습니다.',
      );
    }

    return json;
  }
}
