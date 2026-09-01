import 'game_identity_preview.dart';

class PublicProfileSettings {
  const PublicProfileSettings({required this.publicId, required this.isPublic});
  final String? publicId;
  final bool isPublic;

  factory PublicProfileSettings.fromJson(Map<String, dynamic> json) =>
      PublicProfileSettings(
        publicId: json['publicId'] as String?,
        isPublic: json['isPublic'] as bool? ?? false,
      );
}

class PublicIdentityData {
  const PublicIdentityData({
    required this.shareId,
    required this.identityNumber,
    required this.displayName,
    required this.issuedDate,
    required this.gamePowerPercent,
    required this.evaluationMessage,
    required this.snapshotJson,
    this.enabled = true,
  });
  final String? shareId;
  final String identityNumber;
  final String displayName;
  final String issuedDate;
  final double? gamePowerPercent;
  final String evaluationMessage;
  final String snapshotJson;
  final bool enabled;

  factory PublicIdentityData.fromJson(Map<String, dynamic> json) =>
      PublicIdentityData(
        shareId: json['shareId'] as String?,
        identityNumber: json['identityNumber'] as String? ?? '',
        displayName: json['displayName'] as String? ?? '',
        issuedDate: json['issuedDate'] as String? ?? '',
        gamePowerPercent: (json['gamePowerPercent'] as num?)?.toDouble(),
        evaluationMessage: json['evaluationMessage'] as String? ?? '',
        snapshotJson: json['snapshotJson'] as String? ?? '{}',
        enabled: json['enabled'] as bool? ?? true,
      );
}

class PublicProfileData {
  const PublicProfileData({
    required this.publicId,
    required this.nickname,
    required this.introduction,
    required this.gamePower,
    required this.latestIdentity,
  });
  final String publicId;
  final String nickname;
  final String introduction;
  final GameIdentityPreviewResult gamePower;
  final PublicIdentityData? latestIdentity;

  factory PublicProfileData.fromJson(Map<String, dynamic> json) =>
      PublicProfileData(
        publicId: json['publicId'] as String? ?? '',
        nickname: json['nickname'] as String? ?? '게이머',
        introduction: json['introduction'] as String? ?? '',
        gamePower: GameIdentityPreviewResult.fromJson(
          json['gamePower'] as Map<String, dynamic>? ?? const {},
        ),
        latestIdentity: json['latestIdentity'] is Map<String, dynamic>
            ? PublicIdentityData.fromJson(
                json['latestIdentity'] as Map<String, dynamic>,
              )
            : null,
      );
}
