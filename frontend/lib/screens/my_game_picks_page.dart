import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/my_game_pick.dart';
import '../services/my_game_picks_controller.dart';
import '../widgets/game_pick_button.dart';
import '../widgets/icon_page_header.dart';

class MyGamePicksPage extends StatefulWidget {
  const MyGamePicksPage({
    super.key,
    this.showHeader = true,
    this.onOpenGameFinder,
    this.onGoogleLogin,
  });

  final bool showHeader;
  final VoidCallback? onOpenGameFinder;
  final VoidCallback? onGoogleLogin;

  @override
  State<MyGamePicksPage> createState() => _MyGamePicksPageState();
}

class _MyGamePicksPageState extends State<MyGamePicksPage> {
  final controller = MyGamePicksController.instance;

  @override
  void initState() {
    super.initState();
    controller.syncUser(FirebaseAuth.instance.currentUser);
  }

  @override
  Widget build(BuildContext context) {
    final user = FirebaseAuth.instance.currentUser;
    final guest = user == null || user.isAnonymous;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (widget.showHeader) ...[
        const IconPageHeader(
            icon: Icons.collections_bookmark_rounded, title: 'MY GAME PICKS'),
        const SizedBox(height: 20),
      ] else ...[
        const Row(children: [
          Icon(Icons.collections_bookmark_rounded, color: Color(0xFF9B8CFF)),
          SizedBox(width: 9),
          Text('My Game Picks',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        ]),
        const SizedBox(height: 14),
      ],
      if (guest)
        _message(
          icon: Icons.bookmark_border_rounded,
          title: '로그인하면 저장한 게임을\n기기와 관계없이 다시 볼 수 있습니다.',
          actionLabel: 'Google 로그인',
          onAction: widget.onGoogleLogin,
        )
      else
        AnimatedBuilder(
          animation: controller,
          builder: (context, _) {
            if (controller.loading) {
              return const Center(
                child: Padding(
                  padding: EdgeInsets.all(40),
                  child: CircularProgressIndicator(),
                ),
              );
            }
            if (controller.error != null && controller.items.isEmpty) {
              return _message(
                icon: Icons.error_outline_rounded,
                title: '저장한 게임을 불러오지 못했습니다.',
                actionLabel: '다시 시도',
                onAction: () => controller.syncUser(user, force: true),
              );
            }
            if (controller.items.isEmpty) {
              return _message(
                icon: Icons.bookmark_add_outlined,
                title: '아직 저장한 게임이 없습니다.\nSteam 게임을 둘러보고 마음에 드는 게임을 저장해보세요.',
                actionLabel: 'GAME FINDER로 이동',
                onAction: widget.onOpenGameFinder,
              );
            }
            return LayoutBuilder(builder: (context, constraints) {
              final columns = constraints.maxWidth >= 1100
                  ? 4
                  : constraints.maxWidth >= 700
                      ? 3
                      : 2;
              return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: controller.items.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: columns,
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                  childAspectRatio: columns == 2 ? .64 : .78,
                ),
                itemBuilder: (_, index) => _PickCard(controller.items[index]),
              );
            });
          },
        ),
    ]);
  }

  Widget _message({
    required IconData icon,
    required String title,
    required String actionLabel,
    required VoidCallback? onAction,
  }) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 28),
        decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark
              ? const Color(0xFF101A2A)
              : Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: const Color(0xFF263348)),
        ),
        child: Column(children: [
          Icon(icon, size: 36, color: const Color(0xFF9B8CFF)),
          const SizedBox(height: 12),
          Text(title, textAlign: TextAlign.center),
          if (onAction != null) ...[
            const SizedBox(height: 16),
            FilledButton(onPressed: onAction, child: Text(actionLabel)),
          ],
        ]),
      );
}

class _PickCard extends StatelessWidget {
  const _PickCard(this.game);
  final MyGamePick game;

  @override
  Widget build(BuildContext context) => Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: game.storeUrl.isEmpty
              ? null
              : () => launchUrl(Uri.parse(game.storeUrl)),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            AspectRatio(
              aspectRatio: 460 / 215,
              child: game.imageUrl == null || game.imageUrl!.isEmpty
                  ? const ColoredBox(
                      color: Color(0xFF151D2B),
                      child: Icon(Icons.sports_esports_outlined),
                    )
                  : Image.network(game.imageUrl!, fit: BoxFit.cover),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            child: Text(game.name,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w900)),
                          ),
                          GamePickButton(steamAppId: game.appId),
                        ]),
                    Text(_price(game),
                        style: const TextStyle(
                            color: Color(0xFF65D6B4),
                            fontWeight: FontWeight.w700)),
                    const SizedBox(height: 3),
                    Text(_release(game),
                        style: const TextStyle(color: Color(0xFF8DBDFF))),
                    if (game.playerSummary.isNotEmpty) ...[
                      const SizedBox(height: 7),
                      Text(game.playerSummary, maxLines: 2),
                    ],
                    const Spacer(),
                    Text(game.canonicalTags.take(3).join(' · '),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                            color: Color(0xFFC1B7F4), fontSize: 11)),
                    const SizedBox(height: 7),
                    const Align(
                      alignment: Alignment.centerRight,
                      child:
                          Text('Steam Store ↗', style: TextStyle(fontSize: 11)),
                    ),
                  ],
                ),
              ),
            ),
          ]),
        ),
      );

  String _price(MyGamePick game) {
    if (game.isFree) return '무료';
    if (game.currentPrice == null) return '가격 정보 없음';
    final price = game.currentPrice
        .toString()
        .replaceAllMapped(RegExp(r'(?=(\d{3})+(?!\d))'), (_) => ',');
    return '₩$price${game.discountPercent != null && game.discountPercent! > 0 ? ' (-${game.discountPercent}%)' : ''}';
  }

  String _release(MyGamePick game) {
    if (game.comingSoon) return '출시 예정';
    return game.releaseDate ?? game.releaseDateText ?? '출시일 정보 없음';
  }
}
