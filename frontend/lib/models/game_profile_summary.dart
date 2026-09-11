import 'dart:convert';
import 'dart:typed_data';

class GameProfileSummary {
  const GameProfileSummary({
    required this.id,
    required this.identityNickname,
    required this.gamePowerPercent,
    required this.reflectedGameCount,
    required this.evaluationMessage,
    required this.profileImageBytes,
    required this.updatedAt,
  });

  final int id;
  final String identityNickname;
  final double? gamePowerPercent;
  final int reflectedGameCount;
  final String? evaluationMessage;
  final Uint8List? profileImageBytes;
  final DateTime? updatedAt;

  factory GameProfileSummary.fromJson(
    Map<String, dynamic> json,
  ) {
    Uint8List? profileImageBytes;
    final profileImageBase64 = json['profileImageBase64'];
    if (profileImageBase64 is String && profileImageBase64.isNotEmpty) {
      try {
        profileImageBytes = base64Decode(profileImageBase64);
      } on FormatException {
        profileImageBytes = null;
      }
    }

    return GameProfileSummary(
      id: (json['id'] as num).toInt(),
      identityNickname: json['identityNickname'] as String? ?? '',
      gamePowerPercent: (json['gamePowerPercent'] as num?)?.toDouble(),
      reflectedGameCount: (json['reflectedGameCount'] as num?)?.toInt() ?? 0,
      evaluationMessage: json['evaluationMessage'] as String?,
      profileImageBytes: profileImageBytes,
      updatedAt: json['updatedAt'] == null
          ? null
          : DateTime.tryParse(
              json['updatedAt'].toString(),
            ),
    );
  }
}
