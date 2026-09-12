import 'package:flutter/material.dart';

import '../models/game_profile.dart';

class AddGameResult {
  const AddGameResult(
      {required this.type,
      required this.accountName,
      this.serverId,
      this.platformId});
  final GameType type;
  final String accountName;
  final String? serverId;
  final String? platformId;
}

class AddGameDialog extends StatefulWidget {
  const AddGameDialog({
    super.key,
    this.autofocusAccountName = false,
  });

  final bool autofocusAccountName;

  @override
  State<AddGameDialog> createState() => _AddGameDialogState();
}

class _AddGameDialogState extends State<AddGameDialog> {
  static const _accountNameMaxLength = 255;
  GameType _type = GameType.lostArk;
  final _controller = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  String _dungeonFighterServerId = 'cain';
  String _pubgPlatform = 'steam';

  static const _dungeonFighterServers = <String, String>{
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

  void _close() {
    FocusScope.of(context).unfocus();
    Navigator.of(context).pop();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    Navigator.of(context).pop(AddGameResult(
      type: _type,
      accountName: _controller.text.trim(),
      serverId:
          _type == GameType.dungeonFighter ? _dungeonFighterServerId : null,
      platformId: _type == GameType.battlegrounds ? _pubgPlatform : null,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final isNarrow = mediaQuery.size.width <= 360;
    final labelColor =
        isDark ? const Color(0xFF96A2B6) : const Color(0xFF5F6B7C);
    final fieldColor =
        isDark ? const Color(0xFF111D31) : const Color(0xFFF7F8FC);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOutCubic,
      padding: EdgeInsets.fromLTRB(16, 16, 16, keyboardInset + 16),
      child: SafeArea(
        child: Center(
          child: MediaQuery.removeViewInsets(
            context: context,
            removeBottom: true,
            child: Dialog(
              insetPadding: EdgeInsets.zero,
              backgroundColor: Colors.transparent,
              surfaceTintColor: Colors.transparent,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF0E182B) : Colors.white,
                    borderRadius: BorderRadius.circular(22),
                    border: Border.all(
                        color: isDark
                            ? const Color(0xFF2C3A59)
                            : const Color(0xFFD8DDF0)),
                    boxShadow: const [
                      BoxShadow(
                          color: Color(0x66000000),
                          blurRadius: 28,
                          offset: Offset(0, 12)),
                      BoxShadow(color: Color(0x247B61FF), blurRadius: 22),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(22),
                    child: SingleChildScrollView(
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
                      padding: EdgeInsets.all(isNarrow ? 18 : 26),
                      child: Form(
                        key: _formKey,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            _header(isDark, isNarrow, labelColor),
                            const SizedBox(height: 28),
                            _label('게임', labelColor),
                            const SizedBox(height: 9),
                            DropdownButtonFormField<GameType>(
                              initialValue: _type,
                              isExpanded: true,
                              icon: const Icon(
                                  Icons.keyboard_arrow_down_rounded,
                                  color: Color(0xFF8F9CB0)),
                              decoration: _fieldDecoration(
                                  isDark: isDark, fillColor: fieldColor),
                              selectedItemBuilder: (_) =>
                                  GameType.values.map(_gameOption).toList(),
                              items: GameType.values
                                  .map((type) => DropdownMenuItem(
                                      value: type, child: _gameOption(type)))
                                  .toList(),
                              onChanged: _changeGame,
                            ),
                            if (_type == GameType.dungeonFighter) ...[
                              const SizedBox(height: 18),
                              _label('서버', labelColor),
                              const SizedBox(height: 9),
                              DropdownButtonFormField<String>(
                                initialValue: _dungeonFighterServerId,
                                isExpanded: true,
                                decoration: _fieldDecoration(
                                    isDark: isDark, fillColor: fieldColor),
                                items: _dungeonFighterServers.entries
                                    .map((entry) => DropdownMenuItem(
                                        value: entry.value,
                                        child: Text(entry.key)))
                                    .toList(),
                                onChanged: (value) {
                                  if (value != null) {
                                    setState(
                                        () => _dungeonFighterServerId = value);
                                  }
                                },
                              ),
                            ],
                            if (_type == GameType.battlegrounds) ...[
                              const SizedBox(height: 18),
                              _label('플랫폼', labelColor),
                              const SizedBox(height: 9),
                              DropdownButtonFormField<String>(
                                initialValue: _pubgPlatform,
                                isExpanded: true,
                                decoration: _fieldDecoration(
                                    isDark: isDark, fillColor: fieldColor),
                                items: const [
                                  DropdownMenuItem(
                                      value: 'steam', child: Text('Steam')),
                                  DropdownMenuItem(
                                      value: 'kakao', child: Text('Kakao')),
                                ],
                                onChanged: (value) {
                                  if (value != null) {
                                    setState(() => _pubgPlatform = value);
                                  }
                                },
                              ),
                            ],
                            const SizedBox(height: 18),
                            _label(_type.accountLabel, labelColor),
                            const SizedBox(height: 9),
                            TextFormField(
                              controller: _controller,
                              autofocus: widget.autofocusAccountName,
                              maxLength: _accountNameMaxLength,
                              scrollPadding:
                                  EdgeInsets.only(bottom: keyboardInset + 96),
                              style: TextStyle(
                                color: isDark
                                    ? Colors.white
                                    : const Color(0xFF202636),
                                fontFamily: 'Malgun Gothic',
                                fontWeight: FontWeight.w600,
                              ),
                              decoration: _fieldDecoration(
                                isDark: isDark,
                                fillColor: fieldColor,
                                hintText: _hint(_type),
                                prefixIcon:
                                    const Icon(Icons.person_outline_rounded),
                              ),
                              buildCounter: (context,
                                      {required currentLength,
                                      required isFocused,
                                      required maxLength}) =>
                                  Text(
                                '$currentLength/$maxLength',
                                style:
                                    TextStyle(color: labelColor, fontSize: 11),
                              ),
                              validator: _validateAccountName,
                              onFieldSubmitted: (_) => _submit(),
                            ),
                            const SizedBox(height: 22),
                            Row(children: [
                              Expanded(child: _cancelButton(isDark)),
                              SizedBox(width: isNarrow ? 10 : 14),
                              Expanded(
                                  child: _GradientSubmitButton(
                                      onPressed: _submit)),
                            ]),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _header(bool isDark, bool isNarrow, Color labelColor) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: isNarrow ? 48 : 52,
            height: isNarrow ? 48 : 52,
            decoration: BoxDecoration(
                color: const Color(0xFF29245A),
                borderRadius: BorderRadius.circular(15)),
            child: const Icon(Icons.sports_esports_rounded,
                color: Color(0xFF9B8CFF), size: 27),
          ),
          const SizedBox(width: 14),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text('게임 추가',
                    style: TextStyle(
                        fontSize: isNarrow ? 22 : 24,
                        fontWeight: FontWeight.w900,
                        color:
                            isDark ? Colors.white : const Color(0xFF202636))),
                const SizedBox(height: 4),
                Text('추가할 게임과 캐릭터 이름을 입력해주세요.',
                    style: TextStyle(
                        color: labelColor,
                        fontSize: isNarrow ? 12 : 13,
                        height: 1.35)),
              ])),
          const SizedBox(width: 8),
          IconButton(
            key: const ValueKey('add-game-close'),
            tooltip: '닫기',
            onPressed: _close,
            style: IconButton.styleFrom(
              backgroundColor:
                  isDark ? const Color(0xFF172338) : const Color(0xFFF0F2F8),
              foregroundColor: labelColor,
              minimumSize: const Size(38, 38),
              padding: EdgeInsets.zero,
            ),
            icon: const Icon(Icons.close_rounded),
          ),
        ],
      );

  Widget _cancelButton(bool isDark) => OutlinedButton(
        key: const ValueKey('add-game-cancel'),
        onPressed: _close,
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(0, 54),
          foregroundColor:
              isDark ? const Color(0xFFD7DDEA) : const Color(0xFF3F4858),
          side: BorderSide(
              color:
                  isDark ? const Color(0xFF485672) : const Color(0xFFB8C0D0)),
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
        ),
        child: const Text('취소', maxLines: 1, softWrap: false),
      );

  Widget _label(String text, Color color) => Text(text,
      style:
          TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.w700));

  InputDecoration _fieldDecoration(
      {required bool isDark,
      required Color fillColor,
      String? hintText,
      Widget? prefixIcon}) {
    const focusedColor = Color(0xFF836BFF);
    final borderColor =
        isDark ? const Color(0xFF34425E) : const Color(0xFFC9D0DF);
    final errorColor =
        isDark ? const Color(0xFFFF7B8A) : const Color(0xFFC83D51);
    OutlineInputBorder border(Color color, {double width = 1}) =>
        OutlineInputBorder(
          borderRadius: BorderRadius.circular(13),
          borderSide: BorderSide(color: color, width: width),
        );
    return InputDecoration(
      hintText: hintText,
      hintStyle: TextStyle(
          color: isDark ? const Color(0xFF77859A) : const Color(0xFF7B8595)),
      prefixIcon: prefixIcon,
      prefixIconColor: const Color(0xFF9B8CFF),
      filled: true,
      fillColor: fillColor,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 17),
      enabledBorder: border(borderColor),
      focusedBorder: border(focusedColor, width: 1.5),
      errorBorder: border(errorColor),
      focusedErrorBorder: border(errorColor, width: 1.5),
    );
  }

  Widget _gameOption(GameType type) => Row(children: [
        Container(
          width: 32,
          height: 32,
          padding: const EdgeInsets.all(5),
          decoration: BoxDecoration(
              color: const Color(0xFF25254D),
              borderRadius: BorderRadius.circular(9)),
          child: Image.asset(
            type.iconAsset,
            fit: BoxFit.contain,
            errorBuilder: (_, __, ___) => const Icon(
                Icons.sports_esports_rounded,
                size: 18,
                color: Color(0xFF9B8CFF)),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
            child: Text(type.displayName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700))),
      ]);

  void _changeGame(GameType? value) {
    if (value == null) return;
    setState(() {
      _type = value;
      _controller.clear();
      if (_type == GameType.dungeonFighter) _dungeonFighterServerId = 'cain';
      if (_type == GameType.battlegrounds) _pubgPlatform = 'steam';
    });
  }

  String? _validateAccountName(String? value) {
    if (value == null || value.trim().isEmpty) {
      return '${_type.accountLabel}을 입력해주세요.';
    }
    if (_type == GameType.leagueOfLegends ||
        _type == GameType.tft ||
        _type == GameType.valorant) {
      final parts = value.trim().split('#');
      if (parts.length != 2 ||
          parts[0].trim().isEmpty ||
          parts[1].trim().isEmpty) {
        return 'Riot ID는 게임이름#태그 형식으로 입력해주세요.';
      }
    }
    return null;
  }

  String _hint(GameType type) => switch (type) {
        GameType.lostArk => 'LOST ARK 닉네임',
        GameType.leagueOfLegends => 'LEAGUE OF LEGENDS 닉네임#태그',
        GameType.tft => 'TEAMFIGHT TACTICS 닉네임#태그',
        GameType.eternalReturn => 'ETERNAL RETURN 닉네임',
        GameType.mapleStory => 'MAPLESTORY 닉네임',
        GameType.dungeonFighter => 'DUNGEON & FIGHTER 닉네임',
        GameType.battlegrounds => 'BATTLEGROUNDS 닉네임',
        GameType.valorant => 'VALORANT 닉네임#태그',
      };
}

class _GradientSubmitButton extends StatelessWidget {
  const _GradientSubmitButton({required this.onPressed});
  final VoidCallback onPressed;
  @override
  Widget build(BuildContext context) => Container(
        height: 54,
        decoration: BoxDecoration(
          gradient: const LinearGradient(
              colors: [Color(0xFF6848D8), Color(0xFF8A6BFF)]),
          borderRadius: BorderRadius.circular(15),
          boxShadow: const [
            BoxShadow(
                color: Color(0x387B5CEE), blurRadius: 16, offset: Offset(0, 5))
          ],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            key: const ValueKey('add-game-submit'),
            onTap: onPressed,
            borderRadius: BorderRadius.circular(15),
            child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.add_rounded, color: Colors.white, size: 20),
                  SizedBox(width: 8),
                  Text('등록',
                      maxLines: 1,
                      softWrap: false,
                      style: TextStyle(
                          color: Colors.white, fontWeight: FontWeight.w800)),
                ]),
          ),
        ),
      );
}
