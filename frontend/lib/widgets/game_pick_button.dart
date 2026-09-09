import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../services/my_game_picks_controller.dart';

class GamePickButton extends StatelessWidget {
  const GamePickButton({super.key, required this.steamAppId});

  final int steamAppId;

  @override
  Widget build(BuildContext context) {
    final controller = MyGamePicksController.instance;
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final picked = controller.isPicked(steamAppId);
        final busy = controller.loading || controller.isBusy(steamAppId);
        final message = FirebaseAuth.instance.currentUser?.isAnonymous == true
            ? '로그인하면 게임 저장 기능을 사용할 수 있습니다.'
            : picked
                ? '저장됨'
                : '저장';
        return Tooltip(
          message: message,
          child: IconButton(
            constraints: const BoxConstraints(minWidth: 44, minHeight: 44),
            onPressed: busy
                ? null
                : () async {
                    final user = FirebaseAuth.instance.currentUser;
                    if (user == null || user.isAnonymous) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                            content: Text('로그인하면 게임 저장 기능을 사용할 수 있습니다.')),
                      );
                      return;
                    }
                    try {
                      await controller.syncUser(user);
                      await controller.toggle(steamAppId);
                    } catch (_) {
                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('게임 저장 상태를 변경하지 못했습니다.')),
                      );
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
          ),
        );
      },
    );
  }
}
