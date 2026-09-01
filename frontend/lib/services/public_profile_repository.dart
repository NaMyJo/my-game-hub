import '../models/game_identity_preview.dart';
import '../models/public_profile.dart';
import 'api_client.dart';

class PublicProfileRepository {
  PublicProfileRepository._();
  static final instance = PublicProfileRepository._();

  Future<GameIdentityPreviewResult> getAnalysis() async {
    final json = await ApiClient.instance.get('/api/me/game-power-analysis');
    return GameIdentityPreviewResult.fromJson(json as Map<String, dynamic>);
  }

  Future<PublicProfileSettings> getSettings() async {
    final json = await ApiClient.instance.get('/api/me/public-profile');
    return PublicProfileSettings.fromJson(json as Map<String, dynamic>);
  }

  Future<PublicProfileSettings> updateSettings(bool isPublic) async {
    final json = await ApiClient.instance.put(
      '/api/me/public-profile',
      body: {'isPublic': isPublic},
    );
    return PublicProfileSettings.fromJson(json as Map<String, dynamic>);
  }

  Future<PublicProfileData> getPublicProfile(String publicId) async {
    final json = await ApiClient.instance.publicGet(
      '/api/public/profiles/${Uri.encodeComponent(publicId)}',
    );
    return PublicProfileData.fromJson(json as Map<String, dynamic>);
  }

  Future<PublicIdentityData> enableIdentityShare() async {
    final json = await ApiClient.instance.put('/api/me/game-identities/share');
    return PublicIdentityData.fromJson(json as Map<String, dynamic>);
  }

  Future<PublicIdentityData> disableIdentityShare() async {
    final json = await ApiClient.instance.deleteWithResponse(
      '/api/me/game-identities/share',
    );
    return PublicIdentityData.fromJson(json as Map<String, dynamic>);
  }

  Future<PublicIdentityData> getSharedIdentity(String shareId) async {
    final json = await ApiClient.instance.publicGet(
      '/api/public/identities/${Uri.encodeComponent(shareId)}',
    );
    return PublicIdentityData.fromJson(json as Map<String, dynamic>);
  }
}
