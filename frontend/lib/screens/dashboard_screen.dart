import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/game_profile.dart';
import '../services/auth_service.dart';
import '../services/game_repository.dart';
import '../widgets/add_game_dialog.dart';
import '../widgets/game_card.dart';
import '../widgets/stat_card.dart';

enum DashboardPage {
  dashboard,
  tools,
}

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final List<GameProfile> _games = [];
  int? _refreshingGameId;
  DashboardPage _currentPage = DashboardPage.dashboard;
  @override
  void initState() {
    super.initState();
    _loadGames();
  }

  void _openDashboard() {
    setState(() {
      _currentPage = DashboardPage.dashboard;
    });
  }

  void _openTools() {
    setState(() {
      _currentPage = DashboardPage.tools;
    });
  }

  User? get _user => FirebaseAuth.instance.currentUser;
  Future<void> _loadGames() async {
    try {
      final games = await GameRepository.instance.getMyGames();

      if (!mounted) return;

      setState(() {
        _games
          ..clear()
          ..addAll(games);
      });
    } catch (error) {
      if (!mounted) return;
      await _showApiError();
    }
  }

  Future<void> _refreshGame(GameProfile game) async {
    // 이미 새로고침 중이면 중복 호출 방지
    if (_refreshingGameId != null) return;

    setState(() {
      _refreshingGameId = game.id;
    });

    try {
      final refreshed = await GameRepository.instance.refreshGame(game.id);

      if (!mounted) return;

      final index = _games.indexWhere((g) => g.id == game.id);

      if (index != -1) {
        setState(() {
          _games[index] = refreshed;
        });
      }
    } catch (e) {
      if (!mounted) return;

      await showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            content: const Text(
              'API 연결 오류',
              textAlign: TextAlign.center,
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('확인'),
              ),
            ],
          );
        },
      );
    } finally {
      if (mounted) {
        setState(() {
          _refreshingGameId = null;
        });
      }
    }
  }

  Future<void> _reorderGame(
    GameProfile draggedGame,
    GameProfile targetGame,
  ) async {
    if (draggedGame.id == targetGame.id) {
      return;
    }

    final oldGames = List<GameProfile>.from(_games);

    final oldIndex = _games.indexWhere(
      (game) => game.id == draggedGame.id,
    );

    final targetIndex = _games.indexWhere(
      (game) => game.id == targetGame.id,
    );

    if (oldIndex == -1 || targetIndex == -1) {
      return;
    }

    setState(() {
      final movedGame = _games.removeAt(oldIndex);

      var newIndex = targetIndex;

      if (oldIndex < targetIndex) {
        newIndex--;
      }

      _games.insert(newIndex, movedGame);
    });

    try {
      debugPrint(
        'REORDER IDs: ${_games.map((game) => game.id).toList()}',
      );

      await GameRepository.instance.reorderGames(_games);

      debugPrint('REORDER 저장 성공');
    } catch (error, stackTrace) {
      debugPrint('===== REORDER ERROR =====');
      debugPrint('error: $error');
      debugPrint('stackTrace: $stackTrace');
      debugPrint('=========================');

      if (!mounted) return;

      setState(() {
        _games
          ..clear()
          ..addAll(oldGames);
      });

      await _showApiError();
    }
  }

  Future<void> _openAddGame() async {
    final result = await showDialog<AddGameResult>(
      context: context,
      builder: (_) => const AddGameDialog(),
    );

    if (result == null || !mounted) return;

    try {
      final profile = await GameRepository.instance.registerGame(
        type: result.type,
        accountName: result.accountName,
      );

      if (!mounted) return;

      setState(() {
        _games.add(profile);
      });
    } catch (error) {
      if (!mounted) return;
      await _showApiError();
    }
  }

  Future<void> _signOut() async {
    await AuthService.instance.signOut();
  }

  Future<void> _showApiError() async {
    if (!mounted) return;

    await showDialog<void>(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: const Color(0xFF111C2B),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          title: const Row(
            children: [
              Icon(
                Icons.error_outline_rounded,
                color: Colors.redAccent,
                size: 22,
              ),
              SizedBox(width: 8),
              Text(
                'API 연결 오류',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          content: const Text(
            '잠시 후 다시 시도해주세요.',
            style: TextStyle(
              color: Color(0xFFAEB9C8),
              fontSize: 13,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('확인'),
            ),
          ],
        );
      },
    );
  }

  String _formatRelativeTime(DateTime? dateTime) {
    if (dateTime == null) {
      return '-';
    }

    final now = DateTime.now();
    final localTime = dateTime.toLocal();

    var difference = now.difference(localTime);

    // 서버/클라이언트 시간 오차 방지
    if (difference.isNegative) {
      difference = Duration.zero;
    }

    if (difference.inSeconds < 60) {
      return '방금 전';
    }

    if (difference.inMinutes < 60) {
      return '${difference.inMinutes}분 전';
    }

    if (difference.inHours < 24) {
      return '${difference.inHours}시간 전';
    }

    return '${difference.inDays}일 전';
  }

  @override
  Widget build(BuildContext context) {
    // ============================
    // 대시보드 요약 데이터 계산
    // ============================

    final lostArkCount =
        _games.where((game) => game.type == GameType.lostArk).length;

    final lolCount =
        _games.where((game) => game.type == GameType.leagueOfLegends).length;

    final tftCount = _games.where((game) => game.type == GameType.tft).length;

    final eternalReturnCount =
        _games.where((game) => game.type == GameType.eternalReturn).length;
// 가장 최근에 갱신된 게임
    GameProfile? latestGame;

// 가장 최근에 갱신된 랭크 게임
    GameProfile? latestRankedGame;

    for (final game in _games) {
      if (game.updatedAt == null) {
        continue;
      }

      if (latestGame == null ||
          latestGame.updatedAt == null ||
          game.updatedAt!.isAfter(latestGame.updatedAt!)) {
        latestGame = game;
      }

      final isRankedGame = game.type == GameType.leagueOfLegends ||
          game.type == GameType.tft ||
          game.type == GameType.eternalReturn;

      if (isRankedGame &&
          (latestRankedGame == null ||
              latestRankedGame.updatedAt == null ||
              game.updatedAt!.isAfter(latestRankedGame.updatedAt!))) {
        latestRankedGame = game;
      }
    }

    final lastSyncText = _formatRelativeTime(
      latestGame?.updatedAt,
    );
    return Scaffold(
      body: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: SizedBox(
          width: 1600,
          child: Row(
            children: [
              _Sidebar(
                user: _user,
                currentPage: _currentPage,
                onDashboard: _openDashboard,
                onAddGame: _openAddGame,
                onTools: _openTools,
                onSignOut: _signOut,
              ),
              Expanded(
                child: SafeArea(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(26),
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 1500),
                        child: _currentPage == DashboardPage.dashboard
                            ? Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  _HeroProfile(user: _user),
                                  const SizedBox(height: 18),
                                  _SummaryRow(
                                    lostArkCount: lostArkCount,
                                    lolCount: lolCount,
                                    tftCount: tftCount,
                                    eternalReturnCount: eternalReturnCount,
                                    lastSyncText: lastSyncText,
                                  ),
                                  const SizedBox(height: 18),
                                  _GameGrid(
                                    games: _games,
                                    refreshingGameId: _refreshingGameId,
                                    onAddGame: _openAddGame,
                                    onRefresh: _refreshGame,
                                    onReorder: _reorderGame,
                                    onRemove: (game) async {
                                      try {
                                        await GameRepository.instance
                                            .deleteGame(game.id);

                                        if (!mounted) return;

                                        setState(() {
                                          _games.remove(game);
                                        });
                                      } catch (error) {
                                        if (!mounted) return;
                                        await _showApiError();
                                      }
                                    },
                                  ),
                                ],
                              )
                            : const _ToolsPage(),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Sidebar extends StatelessWidget {
  const _Sidebar({
    required this.user,
    required this.currentPage,
    required this.onDashboard,
    required this.onAddGame,
    required this.onTools,
    required this.onSignOut,
  });

  final User? user;
  final DashboardPage currentPage;
  final VoidCallback onDashboard;
  final VoidCallback onAddGame;
  final VoidCallback onTools;
  final VoidCallback onSignOut;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 230,
      decoration: const BoxDecoration(
        color: Color(0xFF07101C),
        border: Border(
          right: BorderSide(color: Color(0xFF182334)),
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 18, 16, 18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Row(
                children: [
                  Icon(
                    Icons.sports_esports_rounded,
                    color: Color(0xFF8067FF),
                    size: 32,
                  ),
                  SizedBox(width: 10),
                  Text(
                    'MY GAME HUB',
                    style: TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 17,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 38),
              _SideItem(
                icon: Icons.dashboard_rounded,
                label: '대시보드',
                selected: currentPage == DashboardPage.dashboard,
                onTap: onDashboard,
              ),
              _SideItem(
                icon: Icons.add_circle_outline_rounded,
                label: '게임 추가',
                onTap: onAddGame,
              ),
              _SideItem(
                icon: Icons.build_circle_outlined,
                label: '도구 모음',
                selected: currentPage == DashboardPage.tools,
                onTap: onTools,
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFF0B1524),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: const Color(0xFF1C293B)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user?.displayName ?? '게이머',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      user?.email ?? '',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Color(0xFF77869A),
                        fontSize: 11,
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: onSignOut,
                        icon: const Icon(Icons.logout_rounded, size: 16),
                        label: const Text('로그아웃'),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SideItem extends StatelessWidget {
  const _SideItem({
    required this.icon,
    required this.label,
    this.selected = false,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: selected ? const Color(0xFF302371) : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
            child: Row(
              children: [
                Icon(
                  icon,
                  size: 20,
                  color: selected
                      ? const Color(0xFFB6AAFF)
                      : const Color(0xFF8592A6),
                ),
                const SizedBox(width: 12),
                Text(
                  label,
                  style: TextStyle(
                    color: selected ? Colors.white : const Color(0xFFB0BAC8),
                    fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _HeroProfile extends StatelessWidget {
  const _HeroProfile({required this.user});

  final User? user;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 170),
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFF263348)),
        gradient: const LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [
            Color(0xFF101B32),
            Color(0xFF17233D),
            Color(0xFF0C1423),
          ],
        ),
      ),
      child: Wrap(
        spacing: 22,
        runSpacing: 18,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          CircleAvatar(
            radius: 54,
            backgroundColor: const Color(0xFF6E56E9),
            backgroundImage:
                user?.photoURL == null ? null : NetworkImage(user!.photoURL!),
            child: user?.photoURL == null
                ? const Icon(Icons.person_rounded, size: 48)
                : null,
          ),
          SizedBox(
            width: 540,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  user?.displayName ?? '게이머',
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 9),
                const Text(
                  '게임을 사랑하는 게이머',
                  style: TextStyle(
                    color: Color(0xFFB2BDCC),
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  user?.email ?? '',
                  style: const TextStyle(
                    color: Color(0xFF7C899D),
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.lostArkCount,
    required this.lolCount,
    required this.tftCount,
    required this.eternalReturnCount,
    required this.lastSyncText,
  });

  final int lostArkCount;
  final int lolCount;
  final int tftCount;
  final int eternalReturnCount;
  final String lastSyncText;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth > 1150
            ? 4
            : constraints.maxWidth > 620
                ? 2
                : 1;

        const spacing = 14.0;

        final cardWidth =
            (constraints.maxWidth - spacing * (columns - 1)) / columns;

        return Wrap(
          spacing: spacing,
          runSpacing: spacing,
          children: [
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.auto_awesome_rounded,
                label: 'LOST ARK',
                value: '$lostArkCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.shield_rounded,
                label: 'RIOT GAMES',
                value: 'LoL $lolCount개 · TFT $tftCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.diamond_rounded,
                label: 'ETERNAL RETURN',
                value: '$eternalReturnCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.bolt_rounded,
                label: '데이터 동기화',
                value: lastSyncText,
                caption: '마지막 API 갱신',
              ),
            ),
          ],
        );
      },
    );
  }
}

class _GameGrid extends StatelessWidget {
  const _GameGrid({
    required this.games,
    required this.refreshingGameId,
    required this.onAddGame,
    required this.onRefresh,
    required this.onRemove,
    required this.onReorder,
  });

  final List<GameProfile> games;
  final VoidCallback onAddGame;
  final ValueChanged<GameProfile> onRemove;
  final ValueChanged<GameProfile> onRefresh;
  final int? refreshingGameId;
  final Future<void> Function(
    GameProfile draggedGame,
    GameProfile targetGame,
  ) onReorder;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 1200
            ? 4
            : constraints.maxWidth >= 820
                ? 2
                : 1;
        const gap = 14.0;
        final cardWidth =
            (constraints.maxWidth - gap * (columns - 1)) / columns;

        final children = <Widget>[
          for (final game in games)
            SizedBox(
              width: cardWidth,
              child: DragTarget<GameProfile>(
                onWillAcceptWithDetails: (details) {
                  return details.data.id != game.id;
                },
                onAcceptWithDetails: (details) {
                  onReorder(
                    details.data,
                    game,
                  );
                },
                builder: (
                  context,
                  candidateData,
                  rejectedData,
                ) {
                  final isTarget = candidateData.isNotEmpty;

                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(18),
                      border: isTarget
                          ? Border.all(
                              color: const Color(0xFF8067FF),
                              width: 2,
                            )
                          : null,
                    ),
                    child: Draggable<GameProfile>(
                      data: game,
                      feedback: Material(
                        color: Colors.transparent,
                        child: SizedBox(
                          width: cardWidth,
                          child: Opacity(
                            opacity: 0.88,
                            child: GameCard(
                              profile: game,
                              isRefreshing: false,
                              onRefresh: () {},
                              onRemove: () {},
                            ),
                          ),
                        ),
                      ),
                      childWhenDragging: Opacity(
                        opacity: 0.25,
                        child: GameCard(
                          profile: game,
                          isRefreshing: false,
                          onRefresh: () {},
                          onRemove: () {},
                        ),
                      ),
                      child: GameCard(
                        profile: game,
                        isRefreshing: refreshingGameId == game.id,
                        onRefresh: () => onRefresh(game),
                        onRemove: () => onRemove(game),
                      ),
                    ),
                  );
                },
              ),
            ),
          SizedBox(
            width: cardWidth,
            child: _AddGameCard(
              onTap: onAddGame,
            ),
          ),
        ];

        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: children,
        );
      },
    );
  }
}

class _AddGameCard extends StatelessWidget {
  const _AddGameCard({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: Container(
        height: 310,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          color: const Color(0xFF091322),
          border: Border.all(
            color: const Color(0xFF273957),
            style: BorderStyle.solid,
          ),
        ),
        child: const Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircleAvatar(
              radius: 30,
              backgroundColor: Color(0xFF151E34),
              child: Icon(
                Icons.add_rounded,
                size: 36,
                color: Color(0xFF9B91D8),
              ),
            ),
            SizedBox(height: 16),
            Text(
              '게임 추가하기',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
              ),
            ),
            SizedBox(height: 6),
            Text(
              '캐릭터 또는 계정을 등록하세요.',
              style: TextStyle(
                color: Color(0xFF77869B),
                fontSize: 12,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ToolsPage extends StatelessWidget {
  const _ToolsPage();

  Future<void> _open(String url) async {
    final uri = Uri.parse(url);

    final opened = await launchUrl(
      uri,
      mode: LaunchMode.externalApplication,
      webOnlyWindowName: '_blank',
    );

    if (!opened) {
      throw Exception('사이트를 열 수 없습니다.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '도구 모음',
          style: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          '게임별 유용한 전적 검색 및 도구 사이트',
          style: TextStyle(
            color: Color(0xFF7C899D),
            fontSize: 13,
          ),
        ),
        const SizedBox(height: 28),
        _ToolSection(
          title: '로스트아크',
          tools: const [
            _ToolData(
              '로스트아크',
              'https://lostark.game.onstove.com',
            ),
            _ToolData(
              'KLOA',
              'https://kloa.gg',
            ),
            _ToolData(
              'LOPEC',
              'https://lopec.kr',
            ),
            _ToolData(
              '로아업',
              'https://loaup.com',
            ),
            _ToolData(
              '로아도구',
              'https://loatool.taeu.kr',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: '리그 오브 레전드',
          tools: const [
            _ToolData(
              'OP.GG',
              'https://op.gg',
            ),
            _ToolData(
              'LOL.PS',
              'https://lol.ps',
            ),
            _ToolData(
              'YOUR.GG',
              'https://your.gg',
            ),
            _ToolData(
              'FOW.KR',
              'https://fow.kr',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: 'TFT',
          tools: const [
            _ToolData(
              'LOLCHESS.GG',
              'https://lolchess.gg',
            ),
            _ToolData(
              'METATFT',
              'https://www.metatft.com',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: '이터널 리턴',
          tools: const [
            _ToolData(
              '이터널 리턴',
              'https://playeternalreturn.com/main?hl=ko-KR',
            ),
            _ToolData(
              'DAK.GG',
              'https://dak.gg/er',
            ),
          ],
          onOpen: _open,
        ),
      ],
    );
  }
}

class _ToolData {
  const _ToolData(
    this.name,
    this.url,
  );

  final String name;
  final String url;
}

class _ToolSection extends StatelessWidget {
  const _ToolSection({
    required this.title,
    required this.tools,
    required this.onOpen,
  });

  final String title;
  final List<_ToolData> tools;
  final ValueChanged<String> onOpen;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: const Color(0xFF091322),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: const Color(0xFF1A293C),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 18),
          LayoutBuilder(
            builder: (context, constraints) {
              final cardWidth = constraints.maxWidth >= 1000
                  ? 190.0
                  : constraints.maxWidth >= 600
                      ? 175.0
                      : constraints.maxWidth;

              return Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  for (final tool in tools)
                    SizedBox(
                      width: cardWidth,
                      child: _ToolCard(
                        tool: tool,
                        onTap: () => onOpen(tool.url),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ToolCard extends StatefulWidget {
  const _ToolCard({
    required this.tool,
    required this.onTap,
  });

  final _ToolData tool;
  final VoidCallback onTap;

  @override
  State<_ToolCard> createState() => _ToolCardState();
}

class _ToolCardState extends State<_ToolCard> {
  bool _hovering = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) {
        setState(() {
          _hovering = true;
        });
      },
      onExit: (_) {
        setState(() {
          _hovering = false;
        });
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        decoration: BoxDecoration(
          color: _hovering ? const Color(0xFF152238) : const Color(0xFF0E1929),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color:
                _hovering ? const Color(0xFF6959C8) : const Color(0xFF293A51),
          ),
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: widget.onTap,
            borderRadius: BorderRadius.circular(14),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 16,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      widget.tool.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Color(0xFFD7DEE9),
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Icon(
                    Icons.open_in_new_rounded,
                    size: 16,
                    color: Color(0xFF7D8A9E),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
