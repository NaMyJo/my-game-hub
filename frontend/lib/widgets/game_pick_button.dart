import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../services/my_game_picks_controller.dart';

class GamePickButton extends StatefulWidget {
  const GamePickButton({super.key, required this.steamAppId});

  final int steamAppId;

  @override
  State<GamePickButton> createState() => _GamePickButtonState();
}

class _GamePickButtonState extends State<GamePickButton> {
  OverlayEntry? _messageOverlay;
  Timer? _messageTimer;

  @override
  void dispose() {
    _messageTimer?.cancel();
    _messageOverlay?.remove();
    super.dispose();
  }

  void _showWarning(String message) {
    _messageTimer?.cancel();
    _messageOverlay?.remove();

    final box = context.findRenderObject() as RenderBox?;
    final overlay = Overlay.of(context);
    if (box == null || !box.hasSize) return;

    final offset = box.localToGlobal(Offset.zero);
    final screenWidth = MediaQuery.sizeOf(context).width;
    final messageWidth = (screenWidth - 24).clamp(180.0, 340.0);
    final preferredLeft = offset.dx + box.size.width - messageWidth;
    final maxLeft = screenWidth - messageWidth - 12;
    final left = preferredLeft.clamp(12.0, maxLeft);
    final dark = Theme.of(context).brightness == Brightness.dark;

    _messageOverlay = OverlayEntry(
      builder: (_) => Positioned(
        left: left,
        top: offset.dy + box.size.height + 6,
        width: messageWidth,
        child: Material(
          color: Colors.transparent,
          child: IgnorePointer(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: dark ? const Color(0xFF182235) : const Color(0xFFFFF8E7),
                borderRadius: BorderRadius.circular(11),
                border: Border.all(
                  color:
                      dark ? const Color(0xFF8D79FF) : const Color(0xFFD59A20),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x44000000),
                    blurRadius: 12,
                    offset: Offset(0, 5),
                  ),
                ],
              ),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(
                  Icons.warning_amber_rounded,
                  size: 19,
                  color:
                      dark ? const Color(0xFFC4B5FF) : const Color(0xFFA66C00),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    message,
                    style: TextStyle(
                      color: dark
                          ? const Color(0xFFF4F1FF)
                          : const Color(0xFF4A3500),
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ]),
            ),
          ),
        ),
      ),
    );
    overlay.insert(_messageOverlay!);
    _messageTimer = Timer(const Duration(seconds: 3), () {
      _messageOverlay?.remove();
      _messageOverlay = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final controller = MyGamePicksController.instance;
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final picked = controller.isPicked(widget.steamAppId);
        final busy = controller.loading || controller.isBusy(widget.steamAppId);
        return IconButton(
          constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
          onPressed: busy
              ? null
              : () async {
                  final user = FirebaseAuth.instance.currentUser;
                  if (user == null || user.isAnonymous) {
                    _showWarning('로그인하면 게임 저장 기능을 사용할 수 있습니다.');
                    return;
                  }
                  try {
                    await controller.syncUser(user);
                    await controller.toggle(widget.steamAppId);
                  } catch (_) {
                    if (!mounted) return;
                    _showWarning('게임 저장 상태를 변경하지 못했습니다.');
                  }
                },
          icon: busy
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Icon(
                  picked
                      ? Icons.bookmark_rounded
                      : Icons.bookmark_border_rounded,
                  color: picked
                      ? const Color(0xFF9B8CFF)
                      : const Color(0xFF8C9AAF),
                ),
        );
      },
    );
  }
}
