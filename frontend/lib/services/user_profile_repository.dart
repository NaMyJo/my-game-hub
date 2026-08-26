import '../models/user_profile.dart';
import 'api_client.dart';

class UserProfileRepository {
  UserProfileRepository._();

  static final UserProfileRepository instance = UserProfileRepository._();

  Future<UserProfile> getProfile() async {
    final json = await ApiClient.instance.get('/api/me/profile');
    return UserProfile.fromJson(json as Map<String, dynamic>);
  }

  Future<UserProfile> updateProfile({
    required String nickname,
    required String introduction,
  }) async {
    final json = await ApiClient.instance.put(
      '/api/me/profile',
      body: {
        'nickname': nickname,
        'introduction': introduction,
      },
    );

    return UserProfile.fromJson(json as Map<String, dynamic>);
  }
}
