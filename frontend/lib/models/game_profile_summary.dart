class GameProfileSummary {
  const GameProfileSummary({
    required this.id,
    required this.identityNickname,
    required this.gamePowerPercent,
    required this.reflectedGameCount,
    required this.evaluationMessage,
    required this.updatedAt,
  });

  final int id;
  final String identityNickname;
  final double? gamePowerPercent;
  final int reflectedGameCount;
  final String? evaluationMessage;
  final DateTime? updatedAt;

  factory GameProfileSummary.fromJson(
    Map<String, dynamic> json,
  ) {
    return GameProfileSummary(
      id: (json['id'] as num).toInt(),
      identityNickname: json['identityNickname'] as String? ?? '',
      gamePowerPercent: (json['gamePowerPercent'] as num?)?.toDouble(),
      reflectedGameCount: (json['reflectedGameCount'] as num?)?.toInt() ?? 0,
      evaluationMessage: json['evaluationMessage'] as String?,
      updatedAt: json['updatedAt'] == null
          ? null
          : DateTime.tryParse(
              json['updatedAt'].toString(),
            ),
    );
  }
}
