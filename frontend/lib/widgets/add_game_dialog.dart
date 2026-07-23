import 'package:flutter/material.dart';

import '../models/game_profile.dart';

class AddGameResult {
  const AddGameResult({
    required this.type,
    required this.accountName,
  });

  final GameType type;
  final String accountName;
}

class AddGameDialog extends StatefulWidget {
  const AddGameDialog({super.key});

  @override
  State<AddGameDialog> createState() => _AddGameDialogState();
}

class _AddGameDialogState extends State<AddGameDialog> {
  GameType _type = GameType.lostArk;
  final _controller = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;

    Navigator.of(context).pop(
      AddGameResult(
        type: _type,
        accountName: _controller.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF0B1524),
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
      ),
      title: const Text(
        '게임 추가',
        style: TextStyle(fontWeight: FontWeight.w800),
      ),
      content: SizedBox(
        width: 460,
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                '게임',
                style: TextStyle(
                  color: Color(0xFF8F9CB0),
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<GameType>(
                initialValue: _type,
                decoration: const InputDecoration(
                  filled: true,
                  fillColor: Color(0xFF101C2C),
                  border: OutlineInputBorder(),
                ),
                items: GameType.values
                    .map(
                      (type) => DropdownMenuItem(
                        value: type,
                        child: Text(type.displayName),
                      ),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value == null) return;
                  setState(() {
                    _type = value;
                    _controller.clear();
                  });
                },
              ),
              const SizedBox(height: 18),
              Text(
                _type.accountLabel,
                style: const TextStyle(
                  color: Color(0xFF8F9CB0),
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _controller,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: _hint(_type),
                  filled: true,
                  fillColor: const Color(0xFF101C2C),
                  border: const OutlineInputBorder(),
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return '${_type.accountLabel}을 입력해주세요.';
                  }

                  if ((_type == GameType.leagueOfLegends ||
                          _type == GameType.tft) &&
                      !value.contains('#')) {
                    return 'Riot ID는 게임이름#태그 형식으로 입력해주세요.';
                  }

                  return null;
                },
                onFieldSubmitted: (_) => _submit(),
              ),
              const SizedBox(height: 13),
              const Text(
                '현재 버전은 Mock 데이터를 표시합니다. 이후 백엔드에서 공식 게임 API를 조회하도록 연결합니다.',
                style: TextStyle(
                  color: Color(0xFF65748A),
                  fontSize: 11,
                  height: 1.5,
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('취소'),
        ),
        FilledButton.icon(
          onPressed: _submit,
          icon: const Icon(Icons.add_rounded),
          label: const Text('등록'),
        ),
      ],
    );
  }

  String _hint(GameType type) {
    switch (type) {
      case GameType.lostArk:
        return '예: 명종';
      case GameType.leagueOfLegends:
      case GameType.tft:
        return '예: Hide on bush#KR1';
      case GameType.eternalReturn:
        return '예: playerNickname';
    }
  }
}
