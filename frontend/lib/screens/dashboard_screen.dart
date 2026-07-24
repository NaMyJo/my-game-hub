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
  bool _isLoadingGames = true;
  bool _loadGamesTakingLong = false;
  String? _loadGamesError;

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
    if (!mounted) return;

    setState(() {
      _isLoadingGames = true;
      _loadGamesTakingLong = false;
      _loadGamesError = null;
    });

    // 평소보다 오래 걸릴 때만 안내
    Future.delayed(const Duration(seconds: 5), () {
      if (!mounted || !_isLoadingGames) return;

      setState(() {
        _loadGamesTakingLong = true;
      });
    });

    const maxRetries = 2;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        debugPrint('게임 목록 불러오기 시도: $attempt/$maxRetries');

        final games = await GameRepository.instance
            .getMyGames()
            .timeout(const Duration(seconds: 15));

        if (!mounted) return;

        setState(() {
          _games
            ..clear()
            ..addAll(games);

          _isLoadingGames = false;
          _loadGamesTakingLong = false;
          _loadGamesError = null;
        });

        debugPrint('게임 목록 로딩 성공: ${games.length}개');
        return;
      } catch (e) {
        debugPrint('게임 목록 로딩 실패 ($attempt/$maxRetries): $e');

        if (attempt < maxRetries) {
          await Future.delayed(const Duration(seconds: 2));
        }
      }
    }

    if (!mounted) return;

    setState(() {
      _isLoadingGames = false;
      _loadGamesTakingLong = false;
      _loadGamesError = '게임 정보를 불러오지 못했습니다.';
    });
  }

  Widget _buildGameLoadingState() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 24,
        vertical: 42,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFF091322),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: const Color(0xFF1A293C),
        ),
      ),
      child: Column(
        children: [
          const SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(
              strokeWidth: 3,
            ),
          ),
          const SizedBox(height: 18),
          Text(
            _loadGamesTakingLong
                ? '게임 정보를 불러오는 데 시간이 조금 걸리고 있습니다.\n잠시만 기다려주세요.'
                : '게임 정보를 불러오는 중입니다...',
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Color(0xFF7C899D),
              fontSize: 13,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _loadGamesTakingLong
                ? '오랜만에 접속한 경우 최대 1분 정도 걸릴 수 있습니다.\n1분 이상 표시될 경우 새로고침해주세요.'
                : '잠시만 기다려주세요.',
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Color(0xFF7C899D),
              fontSize: 12,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGameLoadErrorState() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 24,
        vertical: 36,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFF091322),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: const Color(0xFF1A293C),
        ),
      ),
      child: Column(
        children: [
          const Icon(
            Icons.cloud_off_rounded,
            size: 34,
            color: Color(0xFF7C899D),
          ),
          const SizedBox(height: 14),
          Text(
            _loadGamesError ?? '게임 정보를 불러오지 못했습니다.',
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            '서버 연결에 시간이 걸리고 있을 수 있습니다.\n'
            '잠시 후 다시 시도하거나 새로고침해주세요.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Color(0xFF7C899D),
              fontSize: 12,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _loadGames,
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('다시 시도'),
          ),
        ],
      ),
    );
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

  Future<void> _confirmSignOut() async {
    final isGuest = _user?.isAnonymous == true;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: const Color(0xFF0C1624),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          title: Text(
            isGuest ? '게스트 이용 종료' : '로그아웃',
            style: const TextStyle(
              fontWeight: FontWeight.w800,
            ),
          ),
          content: Text(
            isGuest
                ? '게스트 이용을 종료하시겠습니까?\n\n'
                    '종료하면 현재 게스트 계정의 게임 데이터를 '
                    '다시 불러오지 못할 수 있습니다.'
                : '정말 로그아웃하시겠습니까?',
            style: const TextStyle(
              color: Color(0xFFAEB9C8),
              height: 1.5,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('취소'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(
                isGuest ? '게스트 종료' : '로그아웃',
              ),
            ),
          ],
        );
      },
    );

    if (confirmed == true) {
      await _signOut();
    }
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

  Widget _buildMobileLayout({
    required int lostArkCount,
    required int lolCount,
    required int tftCount,
    required int eternalReturnCount,
    required String lastSyncText,
  }) {
    return Scaffold(
      backgroundColor: const Color(0xFF050C16),

      // 가운데 원형 게임 추가 버튼
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddGame,
        backgroundColor: const Color(0xFF745CFF),
        foregroundColor: Colors.white,
        elevation: 8,
        shape: const CircleBorder(),
        child: const Icon(
          Icons.add_rounded,
          size: 32,
        ),
      ),

      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,

      // 모바일 하단 내비게이션
      bottomNavigationBar: _MobileBottomBar(
        currentPage: _currentPage,
        onDashboard: _openDashboard,
        onTools: _openTools,
      ),

      body: SafeArea(
        bottom: false,
        child: _currentPage == DashboardPage.dashboard
            ? SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  14,
                  16,
                  14,
                  100,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _MobileHeader(
                      user: _user,
                      onSignOut: _confirmSignOut,
                    ),
                    const SizedBox(height: 16),
                    _MobileHeroProfile(
                      user: _user,
                    ),
                    const SizedBox(height: 14),
                    if (_isLoadingGames)
                      _buildGameLoadingState()
                    else if (_loadGamesError != null)
                      _buildGameLoadErrorState()
                    else ...[
                      _MobileSummaryGrid(
                        lostArkCount: lostArkCount,
                        lolCount: lolCount,
                        tftCount: tftCount,
                        eternalReturnCount: eternalReturnCount,
                        lastSyncText: lastSyncText,
                      ),
                      const SizedBox(height: 14),
                      _MobileGameGrid(
                        games: _games,
                        refreshingGameId: _refreshingGameId,
                        onRefresh: _refreshGame,
                        onRemove: (game) async {
                          try {
                            await GameRepository.instance.deleteGame(game.id);

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
                  ],
                ),
              )
            : const SingleChildScrollView(
                padding: EdgeInsets.fromLTRB(
                  14,
                  20,
                  14,
                  100,
                ),
                child: _ToolsPage(),
              ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final isMobile = width < 700;
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

    if (isMobile) {
      return _buildMobileLayout(
        lostArkCount: lostArkCount,
        lolCount: lolCount,
        tftCount: tftCount,
        eternalReturnCount: eternalReturnCount,
        lastSyncText: lastSyncText,
      );
    }
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
                onSignOut: _confirmSignOut,
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
                                    if (_isLoadingGames)
                                      _buildGameLoadingState()
                                    else if (_loadGamesError != null)
                                      _buildGameLoadErrorState()
                                    else ...[
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
                                  ])
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
    final isGuest = user?.isAnonymous == true;

    final displayName = isGuest ? '게스트' : (user?.displayName ?? '게이머');

    final accountText = isGuest ? '로그인 없이 이용 중' : (user?.email ?? '');
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
              Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: Image.asset(
                      'assets/app_icon/favicon.png',
                      width: 24,
                      height: 24,
                      fit: BoxFit.cover,
                    ),
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
                      displayName,
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      accountText,
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
                        icon: Icon(
                          isGuest
                              ? Icons.exit_to_app_rounded
                              : Icons.logout_rounded,
                          size: 16,
                        ),
                        label: Text(
                          isGuest ? '게스트 종료' : '로그아웃',
                        ),
                      ),
                    )
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
    final isGuest = user?.isAnonymous == true;

    final displayName = isGuest ? '게스트' : (user?.displayName ?? '게이머');

    final accountText = isGuest ? '로그인 없이 이용 중' : (user?.email ?? '');
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
          ClipRRect(
            borderRadius: BorderRadius.circular(24),
            child: Image.asset(
              'assets/app_icon/favicon.png',
              width: 104,
              height: 104,
              fit: BoxFit.cover,
            ),
          ),
          SizedBox(
            width: 540,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  displayName,
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
                  accountText,
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
                imageAsset: 'assets/game_icons/lostark.png',
                label: 'LOST ARK',
                value: '$lostArkCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.shield_rounded,
                imageAsset: 'assets/game_icons/lol.png',
                label: 'RIOT GAMES',
                value: 'LoL $lolCount개 · TFT $tftCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.diamond_rounded,
                imageAsset: 'assets/game_icons/eternal_return.png',
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
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(24),
              child: Image.asset(
                'assets/app_icon/favicon.png',
                width: 104,
                height: 104,
                fit: BoxFit.cover,
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

class _MobileBottomBar extends StatelessWidget {
  const _MobileBottomBar({
    required this.currentPage,
    required this.onDashboard,
    required this.onTools,
  });

  final DashboardPage currentPage;
  final VoidCallback onDashboard;
  final VoidCallback onTools;

  @override
  Widget build(BuildContext context) {
    return BottomAppBar(
      height: 72,
      color: const Color(0xFF07101C),
      shape: const CircularNotchedRectangle(),
      notchMargin: 9,
      child: Row(
        children: [
          Expanded(
            child: _MobileNavItem(
              icon: Icons.dashboard_rounded,
              label: '대시보드',
              selected: currentPage == DashboardPage.dashboard,
              onTap: onDashboard,
            ),
          ),

          // 가운데 FAB 공간
          const SizedBox(width: 72),

          Expanded(
            child: _MobileNavItem(
              icon: Icons.build_circle_outlined,
              label: '도구 모음',
              selected: currentPage == DashboardPage.tools,
              onTap: onTools,
            ),
          ),
        ],
      ),
    );
  }
}

class _MobileNavItem extends StatelessWidget {
  const _MobileNavItem({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? const Color(0xFF9B8CFF) : const Color(0xFF718096);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            color: color,
            size: 23,
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 11,
              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

class _MobileGameGrid extends StatelessWidget {
  const _MobileGameGrid({
    required this.games,
    required this.refreshingGameId,
    required this.onRefresh,
    required this.onRemove,
  });

  final List<GameProfile> games;
  final int? refreshingGameId;
  final ValueChanged<GameProfile> onRefresh;
  final ValueChanged<GameProfile> onRemove;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,

        // 모바일 게임 카드 높이 확보
        childAspectRatio: 0.72,
      ),
      itemCount: games.length,
      itemBuilder: (context, index) {
        final game = games[index];
        return GameCard(
          profile: game,
          isRefreshing: refreshingGameId == game.id,
          onRefresh: () => onRefresh(game),
          onRemove: () => onRemove(game),
          mobile: true,
        );
      },
    );
  }
}

class _MobileSummaryGrid extends StatelessWidget {
  const _MobileSummaryGrid({
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
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisSpacing: 10,
      mainAxisSpacing: 10,
      childAspectRatio: 1.35,
      children: [
        StatCard(
          icon: Icons.auto_awesome_rounded,
          label: 'LOST ARK',
          value: '$lostArkCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.shield_rounded,
          label: 'RIOT',
          value: 'LoL $lolCount · TFT $tftCount',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.diamond_rounded,
          label: 'ETERNAL RETURN',
          value: '$eternalReturnCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.bolt_rounded,
          label: '동기화',
          value: lastSyncText,
          caption: '마지막 갱신',
        ),
      ],
    );
  }
}

class _MobileHeader extends StatelessWidget {
  const _MobileHeader({
    required this.user,
    required this.onSignOut,
  });

  final User? user;
  final VoidCallback onSignOut;

  @override
  Widget build(BuildContext context) {
    final isGuest = user?.isAnonymous == true;
    return Row(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: Image.asset(
            'assets/app_icon/favicon.png',
            width: 24,
            height: 24,
            fit: BoxFit.cover,
          ),
        ),
        const SizedBox(width: 9),
        const Expanded(
          child: Text(
            'MY GAME HUB',
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        IconButton(
          tooltip: isGuest ? '게스트 종료' : '로그아웃',
          onPressed: onSignOut,
          icon: Icon(
            isGuest ? Icons.exit_to_app_rounded : Icons.logout_rounded,
            color: const Color(0xFF8794A8),
          ),
        ),
      ],
    );
  }
}

class _MobileHeroProfile extends StatelessWidget {
  const _MobileHeroProfile({
    required this.user,
  });

  final User? user;
  @override
  Widget build(BuildContext context) {
    final isGuest = user?.isAnonymous == true;

    final displayName = isGuest ? '게스트' : (user?.displayName ?? '게이머');

    final accountText = isGuest ? '로그인 없이 이용 중' : (user?.email ?? '');

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: const Color(0xFF263348),
        ),
        gradient: const LinearGradient(
          colors: [
            Color(0xFF101B32),
            Color(0xFF17233D),
          ],
        ),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 30,
            backgroundColor: const Color(0xFF6E56E9),
            backgroundImage:
                user?.photoURL == null ? null : NetworkImage(user!.photoURL!),
            child: user?.photoURL == null
                ? const Icon(Icons.person_rounded)
                : null,
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  displayName,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  accountText,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF8290A4),
                    fontSize: 11,
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
