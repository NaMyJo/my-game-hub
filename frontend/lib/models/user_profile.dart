class UserProfile {
  const UserProfile({
    required this.nickname,
    required this.introduction,
  });

  final String nickname;
  final String introduction;

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      nickname: json['nickname'] as String? ?? '',
      introduction: json['introduction'] as String? ?? '',
    );
  }
}
