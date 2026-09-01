import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/game_profile.dart';
import '../models/game_profile_summary.dart';
import '../models/user_profile.dart';
import '../services/api_client.dart';
import '../services/auth_service.dart';
import '../services/game_profile_summary_repository.dart';
import '../services/game_repository.dart';
import '../services/public_profile_repository.dart';
import '../services/user_profile_repository.dart';
import '../theme/app_theme_controller.dart';
import '../widgets/add_game_dialog.dart';
import '../widgets/game_card.dart';
import '../widgets/stat_card.dart';
import 'game_identity_page.dart';
import 'public_pages.dart';

enum DashboardPage {
  dashboard,
  tools,
  gameIdentity,
}

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final List<GameProfile> _games = [];
  GameProfileSummary? _gameProfileSummary;
  UserProfile? _userProfile;

  bool _isLoadingGameProfile = true;
  int? _refreshingGameId;
  bool _isLoadingGames = true;
  bool _loadGamesTakingLong = false;
  bool _dashboardMenuExpanded = true;
  bool _deleteMode = false;
  bool _sidebarCollapsed = false;
  final Set<int> _selectedGameIds = {};
  String? _loadGamesError;
  void _toggleSidebar() {
    setState(() {
      _sidebarCollapsed = !_sidebarCollapsed;
    });
  }

  void _openGameIdentity() {
    setState(() {
      _currentPage = DashboardPage.gameIdentity;

      // 게임 카드 삭제 모드가 켜져 있다면 해제
      _deleteMode = false;
      _selectedGameIds.clear();
    });
  }

  DashboardPage _currentPage = DashboardPage.dashboard;

  @override
  void initState() {
    super.initState();

    _loadGames();
    _loadGameProfileSummary();
    _loadUserProfile();
  }

  void _openDashboard() {
    setState(() {
      if (_currentPage == DashboardPage.dashboard) {
        _dashboardMenuExpanded = !_dashboardMenuExpanded;
      } else {
        _currentPage = DashboardPage.dashboard;
        _dashboardMenuExpanded = true;
      }

      _deleteMode = false;
      _selectedGameIds.clear();
    });
  }

  void _openDeleteMode() {
    setState(() {
      _currentPage = DashboardPage.dashboard;
      _deleteMode = true;
      _selectedGameIds.clear();
    });
  }

  void _cancelDeleteMode() {
    setState(() {
      _deleteMode = false;
      _selectedGameIds.clear();
    });
  }

  void _toggleGameSelection(GameProfile game) {
    setState(() {
      if (_selectedGameIds.contains(game.id)) {
        _selectedGameIds.remove(game.id);
      } else {
        _selectedGameIds.add(game.id);
      }
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

  Future<void> _deleteSelectedGames() async {
    if (_selectedGameIds.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('삭제할 게임 카드를 선택해주세요.'),
        ),
      );
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        return AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0C1624) : Colors.white,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(
              color: isDark ? const Color(0xFF28364A) : const Color(0xFFFFC9D0),
            ),
          ),
          title: Text(
            '게임 카드 삭제',
            style: TextStyle(
              fontWeight: FontWeight.w800,
              color: isDark ? Colors.white : const Color(0xFF3B2830),
            ),
          ),
          content: Text(
            '선택한 ${_selectedGameIds.length}개의 게임 카드를 삭제하시겠습니까?',
            style: TextStyle(
              color: isDark ? const Color(0xFFAEB9C8) : const Color(0xFF66545B),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('취소'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('삭제'),
            ),
          ],
        );
      },
    );

    if (confirmed != true) return;

    try {
      final ids = _selectedGameIds.toList();

      for (final id in ids) {
        await GameRepository.instance.deleteGame(id);
      }

      if (!mounted) return;

      setState(() {
        _games.removeWhere(
          (game) => _selectedGameIds.contains(game.id),
        );

        _selectedGameIds.clear();
        _deleteMode = false;
      });
    } catch (error) {
      if (!mounted) return;
      await _showApiError();
    }
  }

  Future<void> _loadGameProfileSummary() async {
    try {
      debugPrint('===== GAME PROFILE LOAD START =====');

      final profile = await GameProfileSummaryRepository.instance.getProfile();

      debugPrint('GAME PROFILE LOAD SUCCESS');
      debugPrint('profile = $profile');

      if (!mounted) return;

      setState(() {
        _gameProfileSummary = profile;
        _isLoadingGameProfile = false;
      });
    } on ApiException catch (error, stackTrace) {
      debugPrint('===== GAME PROFILE API ERROR =====');
      debugPrint('statusCode = ${error.statusCode}');
      debugPrint('message = ${error.message}');
      debugPrint('stackTrace = $stackTrace');

      if (!mounted) return;

      setState(() {
        _isLoadingGameProfile = false;
      });

      rethrow;
    } catch (error, stackTrace) {
      debugPrint('===== GAME PROFILE LOAD ERROR =====');
      debugPrint('error = $error');
      debugPrint('stackTrace = $stackTrace');

      if (!mounted) return;

      setState(() {
        _isLoadingGameProfile = false;
      });

      rethrow;
    }
  }

  Future<void> _loadUserProfile() async {
    try {
      final profile = await UserProfileRepository.instance.getProfile();

      if (!mounted) return;

      setState(() {
        _userProfile = profile;
      });
    } catch (error, stackTrace) {
      debugPrint('USER PROFILE LOAD ERROR: $error');
      debugPrint('$stackTrace');
    }
  }

  void _openGamePowerAnalysis() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => const GamePowerAnalysisPage()),
    );
  }

  Future<void> _openPublicProfileSettings() async {
    try {
      var settings = await PublicProfileRepository.instance.getSettings();
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('공개 게임 프로필'),
            content: SizedBox(
              width: 420,
              child: SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('프로필 공개'),
                subtitle: const Text(
                  '닉네임, 소개, 게임력 분석과 최근 게임 신분증을 공개합니다.',
                ),
                value: settings.isPublic,
                onChanged: (value) async {
                  try {
                    final updated = await PublicProfileRepository.instance
                        .updateSettings(value);
                    setDialogState(() => settings = updated);
                  } catch (_) {
                    if (dialogContext.mounted) Navigator.pop(dialogContext);
                    if (mounted) await _showApiError();
                  }
                },
              ),
            ),
            actions: [
              if (settings.isPublic && settings.publicId != null)
                TextButton.icon(
                  onPressed: () async {
                    await copyPublicLink('/profile/${settings.publicId!}');
                    if (dialogContext.mounted) Navigator.pop(dialogContext);
                  },
                  icon: const Icon(Icons.link_rounded),
                  label: const Text('링크 복사'),
                ),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('닫기'),
              ),
            ],
          ),
        ),
      );
    } catch (_) {
      if (mounted) await _showApiError();
    }
  }

  Future<void> _editUserProfile() async {
    final isGuest = _user?.isAnonymous == true;
    final defaultNickname = isGuest
        ? '게스트'
        : (_user?.displayName?.trim().isNotEmpty == true
            ? _user!.displayName!.trim()
            : '게이머');
    final nicknameController = TextEditingController(
      text: _userProfile?.nickname ?? defaultNickname,
    );
    final introductionController = TextEditingController(
      text: _userProfile?.introduction ?? '게임을 사랑하는 게이머',
    );

    final values = await showDialog<(String, String)>(
      context: context,
      builder: (dialogContext) {
        return Dialog(
          backgroundColor: Colors.transparent,
          insetPadding: const EdgeInsets.symmetric(horizontal: 20),
          child: Container(
            width: 440,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: const Color(0xFF0A1626),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFF263A55)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x66000000),
                  blurRadius: 36,
                  offset: Offset(0, 18),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 38,
                      height: 38,
                      decoration: BoxDecoration(
                        color: const Color(0xFF282657),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.edit_rounded,
                        size: 18,
                        color: Color(0xFFB8AEFF),
                      ),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '프로필 수정',
                            style: TextStyle(
                              fontSize: 19,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            '대시보드에서 보여줄 나만의 프로필이에요.',
                            style: TextStyle(
                              color: Color(0xFF8291A6),
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => Navigator.pop(dialogContext),
                      icon: const Icon(Icons.close_rounded),
                      color: const Color(0xFF7F8CA0),
                      tooltip: '닫기',
                    ),
                  ],
                ),
                const SizedBox(height: 22),
                _ProfileEditField(
                  controller: nicknameController,
                  label: '닉네임',
                  hintText: '대시보드에 표시할 닉네임',
                  icon: Icons.person_outline_rounded,
                  maxLength: 50,
                ),
                const SizedBox(height: 14),
                _ProfileEditField(
                  controller: introductionController,
                  label: '소개 문구',
                  hintText: '나를 표현하는 한마디',
                  icon: Icons.notes_rounded,
                  maxLength: 120,
                  maxLines: 3,
                ),
                const SizedBox(height: 22),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    TextButton(
                      onPressed: () => Navigator.pop(dialogContext),
                      style: TextButton.styleFrom(
                        foregroundColor: const Color(0xFF9AA7B9),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 18,
                          vertical: 12,
                        ),
                      ),
                      child: const Text('취소'),
                    ),
                    const SizedBox(width: 8),
                    FilledButton.icon(
                      onPressed: () {
                        final nickname = nicknameController.text.trim();

                        if (nickname.isEmpty) return;

                        Navigator.pop(
                          dialogContext,
                          (nickname, introductionController.text.trim()),
                        );
                      },
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFF7565E8),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 20,
                          vertical: 12,
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      icon: const Icon(Icons.check_rounded, size: 17),
                      label: const Text('저장'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );

    nicknameController.dispose();
    introductionController.dispose();

    if (values == null) return;

    try {
      final saved = await UserProfileRepository.instance.updateProfile(
        nickname: values.$1,
        introduction: values.$2,
      );

      if (!mounted) return;

      setState(() {
        _userProfile = saved;
      });
    } on ApiException {
      if (!mounted) return;
      await _showProfileEditError();
    } catch (_) {
      if (!mounted) return;
      await _showProfileEditError();
    }
  }

  Future<void> _showProfileEditError() {
    return showDialog<void>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          backgroundColor: const Color(0xFF0A1626),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: const BorderSide(color: Color(0xFF51344A)),
          ),
          icon: const Icon(
            Icons.error_outline_rounded,
            color: Color(0xFFFF7E91),
          ),
          title: const Text('저장 실패'),
          content: const Text(
            '에러가 발생했습니다.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Color(0xFFB8C2D1)),
          ),
          actionsAlignment: MainAxisAlignment.center,
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('확인'),
            ),
          ],
        );
      },
    );
  }

  Widget _buildGameLoadingState() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 24,
        vertical: 42,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF091322) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isDark ? const Color(0xFF1A293C) : const Color(0xFFDDE3EC),
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
            style: TextStyle(
              color: isDark ? const Color(0xFF7C899D) : const Color(0xFF596579),
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
            style: TextStyle(
              color: isDark ? const Color(0xFF7C899D) : const Color(0xFF748094),
              fontSize: 12,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGameLoadErrorState() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 24,
        vertical: 36,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF091322) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isDark ? const Color(0xFF1A293C) : const Color(0xFFDDE3EC),
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
          Text(
            '서버 연결에 시간이 걸리고 있을 수 있습니다.\n'
            '잠시 후 다시 시도하거나 새로고침해주세요.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: isDark ? const Color(0xFF7C899D) : const Color(0xFF687386),
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
    if (_games.length >= 20) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            '게임 카드는 최대 20개까지 등록할 수 있습니다.',
          ),
        ),
      );

      return;
    }

    final result = await showDialog<AddGameResult>(
      context: context,
      builder: (_) => const AddGameDialog(),
    );

    if (result == null || !mounted) return;

    try {
      final profile = await GameRepository.instance.registerGame(
        type: result.type,
        accountName: result.accountName,
        serverId: result.serverId,
        platformId: result.platformId,
      );

      if (!mounted) return;

      setState(() {
        _games.add(profile);
      });
    } catch (error) {
      if (!mounted) return;

      if (error is ApiException) {
        await _showApiError(error.message);
      } else {
        await _showApiError();
      }
    }
    return;
  }

  Future<GameProfile?> _addGameForIdentity() async {
    if (_games.length >= 20) {
      throw const ApiException(
        '게임 카드는 최대 20개까지 등록할 수 있습니다.',
      );
    }

    final result = await showDialog<AddGameResult>(
      context: context,
      builder: (_) => const AddGameDialog(),
    );

    if (result == null || !mounted) {
      return null;
    }

    final profile = await GameRepository.instance.registerGame(
      type: result.type,
      accountName: result.accountName,
      serverId: result.serverId,
      platformId: result.platformId,
    );

    if (!mounted) return null;

    setState(() {
      _games.add(profile);
    });

    return profile;
  }

  Future<void> _signOut() async {
    await AuthService.instance.signOut();
  }

  Future<void> _showApiError([String? message]) async {
    if (!mounted) return;

    await showDialog<void>(
      context: context,
      builder: (context) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        return AlertDialog(
          backgroundColor: isDark ? const Color(0xFF111C2B) : Colors.white,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(
              color: isDark ? const Color(0xFF293A51) : const Color(0xFFD8DEE8),
            ),
          ),
          title: Row(
            children: [
              const Icon(
                Icons.error_outline_rounded,
                color: Colors.redAccent,
                size: 22,
              ),
              const SizedBox(width: 8),
              Text(
                'API 연결 오류',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: isDark ? Colors.white : const Color(0xFF202636),
                ),
              ),
            ],
          ),
          content: Text(
            message ?? '잠시 후 다시 시도해주세요.',
            style: TextStyle(
              color: isDark ? const Color(0xFFAEB9C8) : const Color(0xFF596579),
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
    required int mapleStoryCount,
    required int dungeonFighterCount,
    required int battlegroundsCount,
    required int valorantCount,
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
                    const _TftConnectionNotice(),
                    const SizedBox(height: 10),
                    _MobileHeader(
                      user: _user,
                      onSignOut: _confirmSignOut,
                    ),
                    const SizedBox(height: 16),
                    _MobileHeroProfile(
                      user: _user,
                      profile: _userProfile,
                      onEdit: _editUserProfile,
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
                        mapleStoryCount: mapleStoryCount,
                        dungeonFighterCount: dungeonFighterCount,
                        battlegroundsCount: battlegroundsCount,
                        valorantCount: valorantCount,
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
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

    final mapleStoryCount =
        _games.where((game) => game.type == GameType.mapleStory).length;

    final dungeonFighterCount =
        _games.where((game) => game.type == GameType.dungeonFighter).length;

    final battlegroundsCount = _games
        .where(
          (game) => game.type == GameType.battlegrounds,
        )
        .length;
    final valorantCount =
        _games.where((game) => game.type == GameType.valorant).length;

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
        mapleStoryCount: mapleStoryCount,
        dungeonFighterCount: dungeonFighterCount,
        battlegroundsCount: battlegroundsCount,
        valorantCount: valorantCount,
        lastSyncText: lastSyncText,
      );
    }
    return Scaffold(
      backgroundColor:
          isDark ? const Color(0xFF050C16) : const Color(0xFFF4F6FA),
      body: Row(
        children: [
          _Sidebar(
            user: _user,
            currentPage: _currentPage,
            dashboardMenuExpanded: _dashboardMenuExpanded,
            onDashboard: _openDashboard,
            onAddGame: _openAddGame,
            onDeleteGames: _openDeleteMode,
            deleteMode: _deleteMode,
            onTools: _openTools,
            onGameIdentity: _openGameIdentity,
            onSignOut: _confirmSignOut,
            collapsed: _sidebarCollapsed,
            onToggleCollapsed: _toggleSidebar,
            isDarkMode: isDark,
            onToggleTheme: () {
              appThemeMode.value = isDark ? ThemeMode.light : ThemeMode.dark;
            },
          ),
          Expanded(
            child: SafeArea(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(26),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(
                      maxWidth: 1500,
                    ),
                    child: switch (_currentPage) {
                      DashboardPage.dashboard => Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const _TftConnectionNotice(),
                            const SizedBox(height: 10),
                            _HeroProfile(
                              user: _user,
                              profile: _userProfile,
                              onEdit: _editUserProfile,
                              gameProfileSummary: _gameProfileSummary,
                              isLoadingGameProfile: _isLoadingGameProfile,
                              onOpenAnalysis: _openGamePowerAnalysis,
                              onPublicProfile: _openPublicProfileSettings,
                            ),
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
                                mapleStoryCount: mapleStoryCount,
                                dungeonFighterCount: dungeonFighterCount,
                                battlegroundsCount: battlegroundsCount,
                                valorantCount: valorantCount,
                                lastSyncText: lastSyncText,
                              ),
                              const SizedBox(height: 18),
                              if (_deleteMode) ...[
                                _DeleteModeBar(
                                  selectedCount: _selectedGameIds.length,
                                  onCancel: _cancelDeleteMode,
                                  onDelete: _deleteSelectedGames,
                                ),
                                const SizedBox(height: 18),
                              ],
                              _GameGrid(
                                games: _games,
                                refreshingGameId: _refreshingGameId,
                                onAddGame: _openAddGame,
                                onRefresh: _refreshGame,
                                onReorder: _reorderGame,
                                deleteMode: _deleteMode,
                                selectedGameIds: _selectedGameIds,
                                onToggleSelection: _toggleGameSelection,
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
                          ],
                        ),
                      DashboardPage.tools => const _ToolsPage(),
                      DashboardPage.gameIdentity => GameIdentityPage(
                          games: _games,
                          onAddGame: _addGameForIdentity,
                          onProfileApplied: (profile) {
                            if (!mounted) return;

                            setState(() {
                              _gameProfileSummary = profile;
                              _isLoadingGameProfile = false;
                            });
                          },
                        ),
                    },
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ProfileEditField extends StatelessWidget {
  const _ProfileEditField({
    required this.controller,
    required this.label,
    required this.hintText,
    required this.icon,
    required this.maxLength,
    this.maxLines = 1,
  });

  final TextEditingController controller;
  final String label;
  final String hintText;
  final IconData icon;
  final int maxLength;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 2, bottom: 7),
          child: Text(
            label,
            style: const TextStyle(
              color: Color(0xFFB7C1D0),
              fontSize: 11,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        TextField(
          controller: controller,
          maxLength: maxLength,
          minLines: 1,
          maxLines: maxLines,
          style: const TextStyle(
            color: Color(0xFFE7EBF3),
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
          decoration: InputDecoration(
            hintText: hintText,
            hintStyle: const TextStyle(color: Color(0xFF66758B)),
            prefixIcon: Icon(
              icon,
              size: 18,
              color: const Color(0xFF8F82E8),
            ),
            filled: true,
            fillColor: const Color(0xFF0E1C2E),
            counterStyle: const TextStyle(
              color: Color(0xFF69778B),
              fontSize: 10,
            ),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 14,
              vertical: 14,
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: Color(0xFF263A55)),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: Color(0xFF263A55)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(
                color: Color(0xFF8172F1),
                width: 1.4,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _TftConnectionNotice extends StatelessWidget {
  const _TftConnectionNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 10,
        vertical: 7,
      ),
      decoration: BoxDecoration(
        color: const Color(0x1AFFB74D),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: const Color(0x55FFB74D),
        ),
      ),
      child: const Row(
        children: [
          Icon(
            Icons.info_outline_rounded,
            size: 14,
            color: Color(0xFFFFC46B),
          ),
          SizedBox(width: 7),
          Expanded(
            child: Text(
              '현재 TFT API가 연결되지 않아 TFT 정보를 불러올 수 없습니다.',
              style: TextStyle(
                color: Color(0xFFD8BE94),
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Sidebar extends StatefulWidget {
  const _Sidebar({
    required this.user,
    required this.currentPage,
    required this.onDashboard,
    required this.onAddGame,
    required this.onDeleteGames,
    required this.dashboardMenuExpanded,
    required this.onTools,
    required this.onSignOut,
    required this.deleteMode,
    required this.collapsed,
    required this.onToggleCollapsed,
    required this.onGameIdentity,
    required this.isDarkMode,
    required this.onToggleTheme,
  });

  final User? user;
  final DashboardPage currentPage;

  final VoidCallback onDashboard;
  final VoidCallback onAddGame;
  final VoidCallback onDeleteGames;
  final VoidCallback onTools;
  final VoidCallback onSignOut;
  final VoidCallback onToggleCollapsed;
  final VoidCallback onGameIdentity;
  final bool isDarkMode;
  final VoidCallback onToggleTheme;

  final bool deleteMode;
  final bool dashboardMenuExpanded;
  final bool collapsed;

  @override
  State<_Sidebar> createState() => _SidebarState();
}

class _SidebarState extends State<_Sidebar> {
  bool _showContent = true;

  @override
  void initState() {
    super.initState();
    _showContent = !widget.collapsed;
  }

  @override
  void didUpdateWidget(covariant _Sidebar oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (oldWidget.collapsed == widget.collapsed) return;

    if (widget.collapsed) {
      // 접을 때는 글자를 먼저 숨긴다.
      if (_showContent) {
        setState(() {
          _showContent = false;
        });
      }
    } else {
      // 펼칠 때는 사이드바 너비 애니메이션이 끝난 뒤 표시한다.
      Future.delayed(const Duration(milliseconds: 220), () {
        if (!mounted || widget.collapsed) return;

        setState(() {
          _showContent = true;
        });
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isGuest = widget.user?.isAnonymous == true;

    final displayName = isGuest ? '게스트' : (widget.user?.displayName ?? '게이머');

    final accountText = isGuest ? '로그인 없이 이용 중' : (widget.user?.email ?? '');

    return ClipRect(
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeInOut,
        width: widget.collapsed ? 76 : 230,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: widget.isDarkMode
                ? const Color(0xFF07101C)
                : const Color(0xFFFFFFFF),
            border: Border(
              right: BorderSide(
                color: widget.isDarkMode
                    ? const Color(0xFF182334)
                    : const Color(0xFFE0E5EC),
              ),
            ),
          ),
          child: SafeArea(
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                widget.collapsed ? 10 : 16,
                18,
                widget.collapsed ? 10 : 16,
                18,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    mainAxisAlignment: _showContent
                        ? MainAxisAlignment.spaceBetween
                        : MainAxisAlignment.center,
                    children: [
                      if (_showContent)
                        Expanded(
                          child: Row(
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
                              const SizedBox(width: 10),
                              const Flexible(
                                child: Text(
                                  'MY GAME HUB',
                                  maxLines: 1,
                                  softWrap: false,
                                  overflow: TextOverflow.clip,
                                  style: TextStyle(
                                    fontWeight: FontWeight.w800,
                                    fontSize: 17,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      InkWell(
                        onTap: widget.onToggleCollapsed,
                        borderRadius: BorderRadius.circular(8),
                        hoverColor: Colors.white10,
                        splashColor: Colors.transparent,
                        highlightColor: Colors.transparent,
                        child: SizedBox(
                          width: 36,
                          height: 36,
                          child: Icon(
                            widget.collapsed
                                ? Icons.view_sidebar_outlined
                                : Icons.menu_open_rounded,
                            color: const Color(0xFF9AA8BA),
                            size: 22,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 38),
                  _SideItem(
                    icon: Icons.dashboard_rounded,
                    label: '대시보드',
                    selected: widget.currentPage == DashboardPage.dashboard,
                    onTap: widget.onDashboard,
                    collapsed: !_showContent,
                  ),
                  if (_showContent &&
                      widget.dashboardMenuExpanded &&
                      widget.currentPage == DashboardPage.dashboard) ...[
                    _DashboardSubItem(
                      icon: Icons.add_circle_outline_rounded,
                      label: '게임 카드 추가',
                      onTap: widget.onAddGame,
                    ),
                    _DashboardSubItem(
                      icon: Icons.delete_outline_rounded,
                      label: '게임 카드 삭제',
                      selected: widget.deleteMode,
                      onTap: widget.onDeleteGames,
                    ),
                  ],
                  _SideItem(
                    icon: Icons.build_circle_outlined,
                    label: '도구 모음',
                    selected: widget.currentPage == DashboardPage.tools,
                    onTap: widget.onTools,
                    collapsed: !_showContent,
                  ),
                  _SideItem(
                    icon: Icons.badge_outlined,
                    label: '게임 신분증',
                    selected: widget.currentPage == DashboardPage.gameIdentity,
                    onTap: widget.onGameIdentity,
                    collapsed: !_showContent,
                  ),
                  const Spacer(),
                  _ThemeModeButton(
                    collapsed: !_showContent,
                    isDarkMode: widget.isDarkMode,
                    onTap: widget.onToggleTheme,
                  ),
                  const SizedBox(height: 10),
                  if (!_showContent)
                    Material(
                      color: widget.isDarkMode
                          ? const Color(0xFF0B1524)
                          : const Color(0xFFF1F3F8),
                      borderRadius: BorderRadius.circular(14),
                      child: InkWell(
                        onTap: widget.onSignOut,
                        borderRadius: BorderRadius.circular(14),
                        child: SizedBox(
                          height: 52,
                          child: Icon(
                            isGuest
                                ? Icons.exit_to_app_rounded
                                : Icons.logout_rounded,
                            color: const Color(0xFFCFC6FF),
                            size: 21,
                          ),
                        ),
                      ),
                    )
                  else
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: widget.isDarkMode
                            ? const Color(0xFF0B1524)
                            : const Color(0xFFF7F8FB),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: widget.isDarkMode
                              ? const Color(0xFF1C293B)
                              : const Color(0xFFDDE2EA),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            displayName,
                            maxLines: 1,
                            softWrap: false,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontWeight: FontWeight.w700,
                              color: widget.isDarkMode
                                  ? Colors.white
                                  : const Color(0xFF202636),
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            accountText,
                            maxLines: 1,
                            softWrap: false,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: widget.isDarkMode
                                  ? const Color(0xFF77869A)
                                  : const Color(0xFF687386),
                              fontSize: 11,
                            ),
                          ),
                          const SizedBox(height: 12),
                          SizedBox(
                            width: double.infinity,
                            child: OutlinedButton.icon(
                              onPressed: widget.onSignOut,
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
                          ),
                        ],
                      ),
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

class _ThemeModeButton extends StatelessWidget {
  const _ThemeModeButton({
    required this.collapsed,
    required this.isDarkMode,
    required this.onTap,
  });

  final bool collapsed;
  final bool isDarkMode;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final foreground =
        isDarkMode ? const Color(0xFFCFC6FF) : const Color(0xFF5547B8);
    final background =
        isDarkMode ? const Color(0xFF0B1524) : const Color(0xFFF5F3FF);
    final borderColor =
        isDarkMode ? const Color(0xFF27284A) : const Color(0xFFDDD7FF);
    final targetLabel = isDarkMode ? '라이트 모드' : '다크 모드';
    final targetIcon =
        isDarkMode ? Icons.light_mode_rounded : Icons.dark_mode_rounded;

    final button = AnimatedContainer(
      duration: const Duration(milliseconds: 140),
      curve: Curves.easeOutCubic,
      height: 52,
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: borderColor),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: collapsed ? 0 : 10),
            child: Row(
              mainAxisAlignment: collapsed
                  ? MainAxisAlignment.center
                  : MainAxisAlignment.start,
              children: [
                AnimatedContainer(
                  duration: const Duration(milliseconds: 140),
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: isDarkMode
                        ? const Color(0xFF262449)
                        : const Color(0xFFE8E3FF),
                    borderRadius: BorderRadius.circular(11),
                  ),
                  child: Icon(targetIcon, size: 18, color: foreground),
                ),
                if (!collapsed) ...[
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          targetLabel,
                          style: TextStyle(
                            color: foreground,
                            fontWeight: FontWeight.w800,
                            fontSize: 12,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '화면 테마 변경',
                          style: TextStyle(
                            color: isDarkMode
                                ? const Color(0xFF737F93)
                                : const Color(0xFF7B7791),
                            fontSize: 9,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    width: 38,
                    height: 22,
                    padding: const EdgeInsets.all(3),
                    decoration: BoxDecoration(
                      color: isDarkMode
                          ? const Color(0xFF353159)
                          : const Color(0xFF7062C8),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: AnimatedAlign(
                      duration: const Duration(milliseconds: 140),
                      curve: Curves.easeOutCubic,
                      alignment: isDarkMode
                          ? Alignment.centerLeft
                          : Alignment.centerRight,
                      child: Container(
                        width: 16,
                        height: 16,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.16),
                              blurRadius: 4,
                              offset: const Offset(0, 1),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );

    return collapsed ? Tooltip(message: targetLabel, child: button) : button;
  }
}

class _SideItem extends StatelessWidget {
  const _SideItem({
    required this.icon,
    required this.label,
    required this.collapsed,
    this.selected = false,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final bool collapsed;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final item = Material(
      color: selected
          ? (isDark ? const Color(0xFF302371) : const Color(0xFFE9E5FF))
          : Colors.transparent,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: collapsed ? 0 : 14,
            vertical: 13,
          ),
          child: Row(
            mainAxisAlignment:
                collapsed ? MainAxisAlignment.center : MainAxisAlignment.start,
            children: [
              Icon(
                icon,
                size: 20,
                color: selected
                    ? (isDark
                        ? const Color(0xFFB6AAFF)
                        : const Color(0xFF6654D9))
                    : const Color(0xFF8592A6),
              ),
              if (!collapsed) ...[
                const SizedBox(width: 12),
                Flexible(
                  child: Text(
                    label,
                    maxLines: 1,
                    softWrap: false,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: selected
                          ? (isDark ? Colors.white : const Color(0xFF403493))
                          : (isDark
                              ? const Color(0xFFB0BAC8)
                              : const Color(0xFF4E596B)),
                      fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: collapsed
          ? Tooltip(
              message: label,
              waitDuration: const Duration(milliseconds: 800),
              child: item,
            )
          : item,
    );
  }
}

class _DashboardSubItem extends StatelessWidget {
  const _DashboardSubItem({
    required this.icon,
    required this.label,
    required this.onTap,
    this.selected = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool selected;
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.only(
        left: 18,
        bottom: 6,
      ),
      child: Material(
        color: selected
            ? (isDark ? const Color(0xFF302371) : const Color(0xFFE9E5FF))
            : Colors.transparent,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(10),
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 10,
            ),
            child: Row(
              children: [
                Icon(
                  icon,
                  size: 17,
                  color: selected
                      ? (isDark
                          ? const Color(0xFFB6AAFF)
                          : const Color(0xFF6654D9))
                      : const Color(0xFF7F8CA0),
                ),
                const SizedBox(width: 10),
                Text(
                  label,
                  style: TextStyle(
                    color: selected
                        ? (isDark ? Colors.white : const Color(0xFF403493))
                        : (isDark
                            ? const Color(0xFFAEB8C7)
                            : const Color(0xFF596579)),
                    fontSize: 13,
                    fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
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
  const _HeroProfile({
    required this.user,
    required this.profile,
    required this.onEdit,
    required this.gameProfileSummary,
    required this.isLoadingGameProfile,
    required this.onOpenAnalysis,
    required this.onPublicProfile,
  });

  final User? user;
  final UserProfile? profile;
  final VoidCallback onEdit;
  final GameProfileSummary? gameProfileSummary;
  final bool isLoadingGameProfile;
  final VoidCallback onOpenAnalysis;
  final VoidCallback onPublicProfile;
  @override
  Widget build(BuildContext context) {
    final isGuest = user?.isAnonymous == true;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final displayName =
        profile?.nickname ?? (isGuest ? '게스트' : (user?.displayName ?? '게이머'));

    final introduction = profile?.introduction ?? '게임을 사랑하는 게이머';

    final email = isGuest ? '로그인 없이 이용 중' : (user?.email ?? '');

    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxWidth < 850;

        final userInfo = Transform.translate(
          offset: const Offset(0, 8),
          child: Stack(
            children: [
              Padding(
                padding: const EdgeInsets.only(right: 46),
                child: Row(
                  children: [
                    Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        color: isDark
                            ? const Color(0xFF172438)
                            : const Color(0xFFECE9FF),
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: const Icon(
                        Icons.person_rounded,
                        size: 34,
                        color: Color(0xFFA495FF),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            displayName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 21,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          const SizedBox(height: 5),
                          Text(
                            introduction,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: isDark
                                  ? const Color(0xFF8996A9)
                                  : const Color(0xFF596579),
                              fontSize: 14,
                            ),
                          ),
                          const SizedBox(height: 5),
                          Text(
                            email,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: isDark
                                  ? const Color(0xFF6F7E92)
                                  : const Color(0xFF778196),
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              Positioned(
                right: 0,
                bottom: 0,
                child: IconButton(
                  onPressed: onEdit,
                  tooltip: '프로필 수정',
                  style: IconButton.styleFrom(
                    backgroundColor: isDark
                        ? const Color(0xFF171F3B)
                        : const Color(0xFFECE9FF),
                    foregroundColor: const Color(0xFFA99DFF),
                    side: const BorderSide(color: Color(0xFF393568)),
                    minimumSize: const Size(34, 34),
                    padding: EdgeInsets.zero,
                  ),
                  icon: const Icon(Icons.edit_rounded, size: 15),
                ),
              ),
            ],
          ),
        );

        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF081321) : Colors.white,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: isDark ? const Color(0xFF1C293B) : const Color(0xFFDDE3EC),
            ),
          ),
          child: compact
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    userInfo,
                    const SizedBox(height: 18),
                    _GameProfileSummaryView(
                      profile: gameProfileSummary,
                      isLoading: isLoadingGameProfile,
                      onOpenAnalysis: onOpenAnalysis,
                      onPublicProfile: onPublicProfile,
                    ),
                  ],
                )
              : Row(
                  children: [
                    Expanded(
                      flex: 5,
                      child: userInfo,
                    ),
                    const SizedBox(width: 24),
                    Container(
                      width: 1,
                      height: 80,
                      color: const Color(
                        0xFF223148,
                      ),
                    ),
                    const SizedBox(width: 24),
                    Expanded(
                      flex: 4,
                      child: _GameProfileSummaryView(
                        profile: gameProfileSummary,
                        isLoading: isLoadingGameProfile,
                        onOpenAnalysis: onOpenAnalysis,
                        onPublicProfile: onPublicProfile,
                      ),
                    ),
                  ],
                ),
        );
      },
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.lostArkCount,
    required this.lolCount,
    required this.tftCount,
    required this.eternalReturnCount,
    required this.mapleStoryCount,
    required this.dungeonFighterCount,
    required this.battlegroundsCount,
    required this.valorantCount,
    required this.lastSyncText,
  });

  final int lostArkCount;
  final int lolCount;
  final int tftCount;
  final int eternalReturnCount;
  final int mapleStoryCount;
  final int dungeonFighterCount;
  final int battlegroundsCount;
  final int valorantCount;
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
                icon: Icons.park_rounded,
                imageAsset: 'assets/game_icons/maplestory.png',
                label: 'MAPLESTORY',
                value: '$mapleStoryCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.sports_martial_arts_rounded,
                imageAsset: 'assets/game_icons/dungeon_fighter.png',
                label: 'DUNGEON & FIGHTER',
                value: '$dungeonFighterCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.sports_esports_rounded,
                imageAsset: 'assets/game_icons/pubg.png',
                label: 'BATTLEGROUNDS',
                value: '$battlegroundsCount개',
                caption: '등록 계정',
              ),
            ),
            SizedBox(
              width: cardWidth,
              child: StatCard(
                icon: Icons.local_fire_department_rounded,
                imageAsset: 'assets/game_icons/valorant.png',
                label: 'VALORANT',
                value: '$valorantCount개',
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

class _DeleteModeBar extends StatelessWidget {
  const _DeleteModeBar({
    required this.selectedCount,
    required this.onCancel,
    required this.onDelete,
  });

  final int selectedCount;
  final VoidCallback onCancel;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 18,
        vertical: 14,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF131A27) : const Color(0xFFFFF5F6),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? const Color(0xFF49313A) : const Color(0xFFFFC9D0),
        ),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.delete_outline_rounded,
            color: Colors.redAccent,
            size: 22,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              '삭제할 게임 카드를 선택해주세요.',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : const Color(0xFF3B2830),
              ),
            ),
          ),
          Text(
            '$selectedCount개 선택',
            style: TextStyle(
              color: isDark ? const Color(0xFFAEB9C8) : const Color(0xFF7C5962),
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(width: 14),
          OutlinedButton(
            onPressed: onCancel,
            child: const Text('취소'),
          ),
          const SizedBox(width: 8),
          FilledButton.icon(
            onPressed: selectedCount == 0 ? null : onDelete,
            style: FilledButton.styleFrom(
              backgroundColor: Colors.redAccent,
            ),
            icon: const Icon(
              Icons.delete_rounded,
              size: 18,
            ),
            label: Text(
              selectedCount == 0 ? '삭제' : '$selectedCount개 삭제',
            ),
          ),
        ],
      ),
    );
  }
}

class _GameProfileSummaryView extends StatelessWidget {
  const _GameProfileSummaryView({
    required this.profile,
    required this.isLoading,
    required this.onOpenAnalysis,
    required this.onPublicProfile,
  });

  final GameProfileSummary? profile;
  final bool isLoading;
  final VoidCallback onOpenAnalysis;
  final VoidCallback onPublicProfile;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    if (isLoading) {
      return const SizedBox(
        height: 80,
        child: Center(
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(
              strokeWidth: 2,
            ),
          ),
        ),
      );
    }

    if (profile == null) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF0D1928) : const Color(0xFFF5F6FA),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: isDark ? const Color(0xFF223249) : const Color(0xFFDDE3EC),
          ),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.badge_outlined,
              size: 22,
              color: Color(0xFF7D8BA0),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                '게임 신분증을 만들어\n'
                '나의 게임 프로필을 등록해보세요.',
                style: TextStyle(
                  color: isDark
                      ? const Color(0xFF8794A8)
                      : const Color(0xFF687386),
                  fontSize: 12,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'GAME PROFILE',
          style: TextStyle(
            color: Color(0xFF8C98AB),
            fontSize: 9,
            fontWeight: FontWeight.w900,
            letterSpacing: 1.3,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          profile!.identityNickname,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _GameProfileStat(
                label: '게임력',
                value: _gamePowerText(profile!),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _GameProfileStat(
                label: '등록 게임',
                value: '${profile!.reflectedGameCount}개',
              ),
            ),
          ],
        ),
        if (profile!.evaluationMessage?.trim().isNotEmpty == true) ...[
          const SizedBox(height: 18),
          Text(
            profile!.evaluationMessage!,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: isDark ? const Color(0xFFB8B0F5) : const Color(0xFF6556B8),
              fontSize: 11,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: onOpenAnalysis,
              icon: const Icon(Icons.analytics_outlined, size: 16),
              label: const Text('상세 분석'),
            ),
            TextButton.icon(
              onPressed: onPublicProfile,
              icon: const Icon(Icons.public_rounded, size: 16),
              label: const Text('프로필 공개'),
            ),
          ],
        ),
      ],
    );
  }

  String _gamePowerText(
    GameProfileSummary profile,
  ) {
    final percent = profile.gamePowerPercent;

    if (percent == null) {
      return 'RPG';
    }

    if (percent == percent.roundToDouble()) {
      return '상위 ${percent.toStringAsFixed(0)}%';
    }

    return '상위 ${percent.toStringAsFixed(1)}%';
  }
}

class _GameProfileStat extends StatelessWidget {
  const _GameProfileStat({
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 12,
        vertical: 9,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0E1B2B) : const Color(0xFFF7F8FB),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? const Color(0xFF25344A) : const Color(0xFFD8DEE8),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              color: isDark ? const Color(0xFF7E8B9F) : const Color(0xFF687386),
              fontSize: 9,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontWeight: FontWeight.w800,
              fontSize: 13,
              color: isDark ? Colors.white : const Color(0xFF202636),
            ),
          ),
        ],
      ),
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
    required this.deleteMode,
    required this.selectedGameIds,
    required this.onToggleSelection,
  });

  final List<GameProfile> games;
  final VoidCallback onAddGame;
  final ValueChanged<GameProfile> onRemove;
  final ValueChanged<GameProfile> onRefresh;
  final int? refreshingGameId;
  final bool deleteMode;
  final Set<int> selectedGameIds;
  final ValueChanged<GameProfile> onToggleSelection;
  final Future<void> Function(
    GameProfile draggedGame,
    GameProfile targetGame,
  ) onReorder;
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
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

        final children = <Widget>[];

        for (final game in games) {
          final selected = selectedGameIds.contains(game.id);

          // ============================
          // 삭제 선택 모드
          // ============================
          if (deleteMode) {
            children.add(
              SizedBox(
                width: cardWidth,
                child: InkWell(
                  onTap: () => onToggleSelection(game),
                  borderRadius: BorderRadius.circular(18),
                  child: Stack(
                    children: [
                      // 기존 게임 카드
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 150),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(18),
                          border: Border.all(
                            color: selected
                                ? Colors.redAccent
                                : (isDark
                                    ? const Color(0xFF27364A)
                                    : const Color(0xFFD5DCE7)),
                            width: selected ? 2.5 : 1,
                          ),
                        ),
                        child: IgnorePointer(
                          child: Opacity(
                            opacity: selected ? 0.55 : 0.75,
                            child: GameCard(
                              profile: game,
                              isRefreshing: false,
                              onRefresh: () {},
                              onRemove: () {},
                            ),
                          ),
                        ),
                      ),

                      // 삭제 모드 오버레이
                      Positioned.fill(
                        child: Container(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(18),
                            color: selected
                                ? const Color(0x22000000)
                                : (isDark
                                    ? const Color(0x11000000)
                                    : const Color(0x08FFFFFF)),
                          ),
                          child: Center(
                            child: AnimatedContainer(
                              duration: const Duration(milliseconds: 150),
                              padding: EdgeInsets.symmetric(
                                horizontal: selected ? 18 : 0,
                                vertical: selected ? 10 : 0,
                              ),
                              width: selected ? null : 46,
                              height: selected ? null : 46,
                              decoration: BoxDecoration(
                                color: selected
                                    ? Colors.redAccent
                                    : (isDark
                                        ? const Color(0xDD101B2B)
                                        : const Color(0xF2FFFFFF)),
                                borderRadius: BorderRadius.circular(
                                  selected ? 24 : 23,
                                ),
                                border: Border.all(
                                  color: selected
                                      ? Colors.redAccent
                                      : (isDark
                                          ? const Color(0xFFAAB5C5)
                                          : const Color(0xFF7B8798)),
                                  width: 2,
                                ),
                                boxShadow: const [
                                  BoxShadow(
                                    color: Color(0x66000000),
                                    blurRadius: 12,
                                  ),
                                ],
                              ),
                              child: selected
                                  ? const Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        Icon(
                                          Icons.check_rounded,
                                          color: Colors.white,
                                          size: 20,
                                        ),
                                        SizedBox(width: 6),
                                        Text(
                                          '선택됨',
                                          style: TextStyle(
                                            color: Colors.white,
                                            fontWeight: FontWeight.w800,
                                            fontSize: 13,
                                          ),
                                        ),
                                      ],
                                    )
                                  : Icon(
                                      Icons.check_rounded,
                                      color: isDark
                                          ? const Color(0xFFD4DCE8)
                                          : const Color(0xFF596579),
                                      size: 24,
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

            continue;
          }

          // ============================
          // 일반 모드
          // ============================
          children.add(
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
                    duration: const Duration(
                      milliseconds: 150,
                    ),
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
          );
        }

        // 삭제 모드가 아닐 때만 게임 카드 추가 표시
        if (!deleteMode && games.length < 20) {
          children.add(
            SizedBox(
              width: cardWidth,
              child: _AddGameCard(
                onTap: onAddGame,
              ),
            ),
          );
        }

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
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: Container(
        height: 310,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          color: isDark ? const Color(0xFF091322) : Colors.white,
          border: Border.all(
            color: isDark ? const Color(0xFF273957) : const Color(0xFFD5DCE7),
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
            const SizedBox(height: 16),
            Text(
              '게임 카드 추가',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : const Color(0xFF202636),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '캐릭터 또는 계정을 등록하세요.',
              style: TextStyle(
                color:
                    isDark ? const Color(0xFF77869B) : const Color(0xFF687386),
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
              '공식홈페이지',
              'https://lostark.game.onstove.com',
            ),
            _ToolData(
              '로스트아크 인벤',
              'https://lostark.inven.co.kr',
            ),
            _ToolData(
              'KLOA',
              'https://kloa.gg',
            ),
            _ToolData(
              'iloa',
              'https://iloa.gg/',
            ),
            _ToolData(
              '로아와',
              'https://loawa.com',
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
              '로아 아이스펭',
              'https://loa.icepeng.com',
            ),
            _ToolData(
              '로스트빌드',
              'https://lostbuilds.com/',
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
            _ToolData(
              'deeplol',
              'https://www.deeplol.gg/',
            ),
            _ToolData(
              'poro.gg',
              'https://poro.gg/?hl=ko-KR',
            ),
            _ToolData(
              'lol_Analytics',
              'https://lolalytics.com/ko/',
            ),
            _ToolData(
              'dpm_lol',
              'https://dpm.lol/',
            ),
            _ToolData(
              'rft.gg',
              'https://rft.gg/',
            ),
            _ToolData(
              'noobhours',
              'https://noobhours.com/',
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
              '공식홈페이지',
              'https://playeternalreturn.com/main?hl=ko-KR',
            ),
            _ToolData(
              'DAK.GG',
              'https://dak.gg/er',
            ),
            _ToolData(
              '이리와지지',
              'https://erwagg.com/',
            ),
            _ToolData(
              '루미랩',
              'https://lumilab.gg/',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: '메이플스토리',
          tools: const [
            _ToolData(
              '공식홈페이지',
              'https://maplestory.nexon.com',
            ),
            _ToolData(
              'Maple.GG',
              'https://maple.gg',
            ),
            _ToolData(
              '환산 주스탯',
              'https://maplescouter.com/ko',
            ),
            _ToolData(
              '츄츄지지',
              'https://chuchu.gg/',
            ),
            _ToolData(
              '메이플도구(주간 결정석 계산)',
              'https://maple.ygh.kr/crystal',
            ),
            _ToolData(
              'MAPLEUTILTY(유니온배치)',
              'https://maple-util.web.app/union-resolver',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: '던전앤파이터',
          tools: const [
            _ToolData(
              '공식홈페이지',
              'https://df.nexon.com/',
            ),
            _ToolData(
              '던담',
              'https://dundam.xyz/',
            ),
            _ToolData(
              '던파맥스',
              'http://dfmax.xyz/',
            ),
            _ToolData(
              '던파나우(경매장)',
              'http://dnfnow.xyz/',
            ),
            _ToolData(
              '던파파워(통계)',
              'https://dnf-power.com/',
            ),
            _ToolData(
              '던파타임(캐릭조회)',
              'https://dftime.co.kr/',
            ),
            _ToolData(
              '던파일럿(스펙업)',
              'https://www.dunpilot.com/',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: '배틀그라운드',
          tools: const [
            _ToolData(
              'Dak.gg',
              'https://dak.gg/pubg?hl=ko-KR',
            ),
            _ToolData(
              'op.gg',
              'https://op.gg/ko/pubg',
            ),
            _ToolData(
              '배틀그라운드 인벤',
              'https://pubg.inven.co.kr/',
            ),
          ],
          onOpen: _open,
        ),
        const SizedBox(height: 20),
        _ToolSection(
          title: '발로란트',
          tools: const [
            _ToolData(
              'Dak.gg',
              'https://dak.gg/valorant?hl=ko',
            ),
            _ToolData(
              'op.gg',
              'https://op.gg/ko/valorant',
            ),
            _ToolData(
              'blitz.gg',
              'https://blitz.gg/valorant',
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF091322) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isDark ? const Color(0xFF1A293C) : const Color(0xFFDDE3EC),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w800,
              color: isDark ? Colors.white : const Color(0xFF202636),
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
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
          color: isDark
              ? (_hovering ? const Color(0xFF152238) : const Color(0xFF0E1929))
              : (_hovering ? const Color(0xFFF0EDFF) : const Color(0xFFF8F9FC)),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: _hovering
                ? const Color(0xFF6959C8)
                : (isDark ? const Color(0xFF293A51) : const Color(0xFFD9DFE9)),
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
                      style: TextStyle(
                        color: isDark
                            ? const Color(0xFFD7DEE9)
                            : const Color(0xFF283142),
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
    required this.mapleStoryCount,
    required this.dungeonFighterCount,
    required this.battlegroundsCount,
    required this.valorantCount,
    required this.lastSyncText,
  });

  final int lostArkCount;
  final int lolCount;
  final int tftCount;
  final int eternalReturnCount;
  final int mapleStoryCount;
  final int dungeonFighterCount;
  final int battlegroundsCount;
  final int valorantCount;
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
          imageAsset: 'assets/game_icons/lostark.png',
          label: 'LOST ARK',
          value: '$lostArkCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.shield_rounded,
          imageAsset: 'assets/game_icons/lol.png',
          label: 'RIOT',
          value: 'LoL $lolCount개 \nTFT $tftCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.diamond_rounded,
          imageAsset: 'assets/game_icons/eternal_return.png',
          label: 'ETERNAL RETURN',
          value: '$eternalReturnCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.park_rounded,
          imageAsset: 'assets/game_icons/maplestory.png',
          label: 'MAPLESTORY',
          value: '$mapleStoryCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.sports_martial_arts_rounded,
          imageAsset: 'assets/game_icons/dungeon_fighter.png',
          label: 'D&F',
          value: '$dungeonFighterCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.sports_esports_rounded,
          imageAsset: 'assets/game_icons/pubg.png',
          label: 'PUBG',
          value: '$battlegroundsCount개',
          caption: '등록 계정',
        ),
        StatCard(
          icon: Icons.sports_esports_rounded,
          imageAsset: 'assets/game_icons/valorant.png',
          label: 'Valorant',
          value: '$valorantCount개',
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
    required this.profile,
    required this.onEdit,
  });

  final User? user;
  final UserProfile? profile;
  final VoidCallback onEdit;
  @override
  Widget build(BuildContext context) {
    final isGuest = user?.isAnonymous == true;

    final displayName =
        profile?.nickname ?? (isGuest ? '게스트' : (user?.displayName ?? '게이머'));

    final introduction = profile?.introduction ?? '게임을 사랑하는 게이머';

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
                  introduction,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF9AA7B9),
                    fontSize: 13,
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
                const SizedBox(height: 7),
                Align(
                  alignment: Alignment.centerRight,
                  child: IconButton(
                    onPressed: onEdit,
                    tooltip: '프로필 수정',
                    style: IconButton.styleFrom(
                      backgroundColor: const Color(0xFF24234C),
                      foregroundColor: const Color(0xFFB8AEFF),
                      minimumSize: const Size(32, 32),
                      padding: EdgeInsets.zero,
                    ),
                    icon: const Icon(Icons.edit_rounded, size: 14),
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
