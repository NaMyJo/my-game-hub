import 'package:flutter/material.dart';

import '../models/game_profile.dart';

class AddGameResult {
  const AddGameResult({
    required this.type,
    required this.accountName,
    this.serverId,
  });

  final GameType type;
  final String accountName;
  final String? serverId;
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

  String _dungeonFighterServerId = 'cain';

  static const Map<String, String> _dungeonFighterServers = {
    '카인': 'cain',
    '디레지에': 'diregie',
    '시로코': 'siroco',
    '프레이': 'prey',
    '카시야스': 'casillas',
    '힐더': 'hilder',
    '안톤': 'anton',
    '바칼': 'bakal',
  };

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
        serverId:
            _type == GameType.dungeonFighter ? _dungeonFighterServerId : null,
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
        style: TextStyle(
          fontWeight: FontWeight.w800,
        ),
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

                    if (_type == GameType.dungeonFighter) {
                      _dungeonFighterServerId = 'cain';
                    }
                  });
                },
              ),

              // ============================
              // 던전앤파이터 서버
              // ============================
              if (_type == GameType.dungeonFighter) ...[
                const SizedBox(height: 18),
                const Text(
                  '서버',
                  style: TextStyle(
                    color: Color(0xFF8F9CB0),
                    fontSize: 12,
                  ),
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: _dungeonFighterServerId,
                  decoration: const InputDecoration(
                    filled: true,
                    fillColor: Color(0xFF101C2C),
                    border: OutlineInputBorder(),
                  ),
                  items: _dungeonFighterServers.entries
                      .map(
                        (entry) => DropdownMenuItem<String>(
                          value: entry.value,
                          child: Text(entry.key),
                        ),
                      )
                      .toList(),
                  onChanged: (value) {
                    if (value == null) return;

                    setState(() {
                      _dungeonFighterServerId = value;
                    });
                  },
                ),
              ],

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
        return '예: 필례';

      case GameType.leagueOfLegends:
      case GameType.tft:
        return '예: Hide on bush#KR1';

      case GameType.eternalReturn:
        return '예: 한동그라미';

      case GameType.mapleStory:
        return '예: 강은호';

      case GameType.dungeonFighter:
        return '예: 라독커';
    }
  }
}
