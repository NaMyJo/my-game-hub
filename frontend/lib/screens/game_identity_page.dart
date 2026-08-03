import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../models/game_identity_preview.dart';
import '../models/game_profile.dart';
import '../services/api_client.dart';
import '../utils/image_download.dart';

class GameIdentityRepository {
  GameIdentityRepository._();

  static final GameIdentityRepository instance = GameIdentityRepository._();

  Future<GameIdentityPreviewResult> preview({
    required String displayName,
    required List<int> gameAccountIds,
  }) async {
    final json = await ApiClient.instance.post(
      '/api/me/game-identities/preview',
      body: {
        'displayName': displayName,
        'gameAccountIds': gameAccountIds,
      },
    );

    if (json is! Map<String, dynamic>) {
      throw const ApiException(
        '게임 신분증 미리보기 응답 형식이 올바르지 않습니다.',
      );
    }

    return GameIdentityPreviewResult.fromJson(json);
  }
}

class GameIdentityPage extends StatefulWidget {
  const GameIdentityPage({
    super.key,
    required this.games,
    required this.onAddGame,
  });

  final List<GameProfile> games;

  final Future<GameProfile?> Function() onAddGame;

  @override
  State<GameIdentityPage> createState() => _GameIdentityPageState();
}

class _GameIdentityPageState extends State<GameIdentityPage> {
  final TextEditingController _displayNameController = TextEditingController();

  final Set<int> _selectedGameIds = {};
  final GlobalKey _identityCardKey = GlobalKey();

  bool _isGeneratingImage = false;
  int _currentStep = 0;
  late final String _identityNumber;
  List<GameProfile> get _selectedGames {
    return widget.games
        .where((game) => _selectedGameIds.contains(game.id))
        .toList();
  }

  GameIdentityPreviewResult? _previewResult;

  bool _isLoadingPreview = false;

  String? _previewError;
  bool get _hasSelectedGames => _selectedGameIds.isNotEmpty;
  bool _isAddingGame = false;
  bool get _hasCompetitiveGame {
    return _selectedGames.any(
      (game) =>
          game.type == GameType.leagueOfLegends ||
          game.type == GameType.tft ||
          game.type == GameType.eternalReturn ||
          game.type == GameType.battlegrounds ||
          game.type == GameType.valorant,
    );
  }

  bool get _hasRpgGame {
    return _selectedGames.any(
      (game) =>
          game.type == GameType.lostArk ||
          game.type == GameType.mapleStory ||
          game.type == GameType.dungeonFighter,
    );
  }

  bool get _hasAnyGame =>
      _selectedGameIds.isNotEmpty || _customGames.isNotEmpty;

  final List<CustomGameEntry> _customGames = [];

  final TextEditingController _customGameNameController =
      TextEditingController();

  final TextEditingController _customGameInfoController =
      TextEditingController();
  Future<Uint8List> _captureIdentityCard() async {
    final context = _identityCardKey.currentContext;

    if (context == null) {
      throw StateError(
        '게임 신분증 미리보기를 찾을 수 없습니다.',
      );
    }

    final renderObject = context.findRenderObject();

    if (renderObject is! RenderRepaintBoundary) {
      throw StateError(
        '게임 신분증 이미지 영역을 찾을 수 없습니다.',
      );
    }

    // 렌더링이 완전히 끝날 때까지 잠시 대기
    await WidgetsBinding.instance.endOfFrame;

    final image = await renderObject.toImage(
      pixelRatio: 3,
    );

    final byteData = await image.toByteData(
      format: ui.ImageByteFormat.png,
    );

    if (byteData == null) {
      throw StateError(
        '게임 신분증 이미지를 생성하지 못했습니다.',
      );
    }

    return byteData.buffer.asUint8List();
  }

  Future<void> _generateIdentityCardImage() async {
    if (_isGeneratingImage) return;

    final displayName = _displayNameController.text.trim();

    if (displayName.isEmpty) {
      _showMessageBubble(
        '신분증에서 사용할 닉네임을 입력해주세요.',
      );
      return;
    }

    if (!_hasAnyGame) {
      _showMessageBubble(
        '게임 신분증에 사용할 게임을 하나 이상 추가해주세요.',
      );
      return;
    }

    if (_isLoadingPreview) {
      _showMessageBubble(
        '게임력 계산이 끝난 뒤 다시 시도해주세요.',
      );
      return;
    }

    setState(() {
      _isGeneratingImage = true;
    });

    try {
      /*
     * 아직 Preview API를 요청한 적이 없는 경우에만 호출한다.
     *
     * 이전에 이미 실패해서 _previewError가 존재한다면
     * 동일한 API를 다시 호출하지 않고 기본 정보로 이미지를 만든다.
     */
      if (_selectedGameIds.isNotEmpty &&
          _previewResult == null &&
          _previewError == null) {
        await _loadIdentityPreview();

        if (!mounted) return;

        await WidgetsBinding.instance.endOfFrame;
      }

      /*
     * Preview 성공 여부와 관계없이
     * 현재 화면의 신분증을 캡처한다.
     */
      final bytes = await _captureIdentityCard();

      final safeDisplayName = displayName.replaceAll(
        RegExp(r'[\\/:*?"<>|]'),
        '_',
      );

      await downloadPng(
        bytes: bytes,
        fileName: '게임신분증_$safeDisplayName.png',
      );

      if (!mounted) return;

      _showMessageBubble(
        _previewResult == null && _previewError != null
            ? '게임력 계산을 제외하고 신분증 이미지를 생성했습니다.'
            : '게임 신분증 이미지가 생성되었습니다.',
      );
    } catch (error, stackTrace) {
      debugPrint(
        '===== GAME IDENTITY IMAGE ERROR =====',
      );
      debugPrint('error: $error');
      debugPrint('stackTrace: $stackTrace');
      debugPrint(
        '=====================================',
      );

      if (!mounted) return;

      _showMessageBubble(
        '게임 신분증 이미지를 생성하지 못했습니다.',
      );
    } finally {
      if (mounted) {
        setState(() {
          _isGeneratingImage = false;
        });
      }
    }
  }

  Future<void> _addNewGameAccount() async {
    if (_isAddingGame) return;

    setState(() {
      _isAddingGame = true;
    });

    try {
      final profile = await widget.onAddGame();

      if (!mounted || profile == null) {
        return;
      }

      setState(() {
        _selectedGameIds.add(profile.id);
      });

      // 기존 등록 완료 다이얼로그
    } on ApiException catch (error) {
      if (!mounted) return;

      final isExpectedError = error.message.contains('이미 등록된') ||
          error.message.contains('최대 20개') ||
          error.message.contains('입력해주세요') ||
          error.message.contains('찾을 수 없습니다');

      if (isExpectedError) {
        _showMessageBubble(error.message);
      } else {
        _showSearchErrorBubble();
      }
    } catch (_) {
      if (!mounted) return;
      _showSearchErrorBubble();
    } finally {
      if (mounted) {
        setState(() {
          _isAddingGame = false;
        });
      }
    }
  }

  Future<void> _loadIdentityPreview() async {
    final displayName = _displayNameController.text.trim();

    if (displayName.isEmpty) {
      _showMessageBubble(
        '신분증에서 사용할 닉네임을 입력해주세요.',
      );
      return;
    }

    /*
   * 기타 게임만 등록한 경우에는
   * 백엔드 상위 퍼센트 계산이 필요 없다.
   */
    if (_selectedGameIds.isEmpty) {
      setState(() {
        _previewResult = null;
        _previewError = null;
        _isLoadingPreview = false;
      });

      return;
    }

    if (_isLoadingPreview) return;

    setState(() {
      _isLoadingPreview = true;
      _previewError = null;
    });

    try {
      final result = await GameIdentityRepository.instance.preview(
        displayName: displayName,
        gameAccountIds: _selectedGameIds.toList(),
      );

      if (!mounted) return;

      setState(() {
        _previewResult = result;
        _previewError = null;
      });
    } on ApiException {
      if (!mounted) return;

      setState(() {
        _previewResult = null;
        _previewError = '게임 정보를 계산하지 못했습니다.';
      });

      _showSearchErrorBubble();
    } catch (error, stackTrace) {
      debugPrint(
        '===== GAME IDENTITY PREVIEW ERROR =====',
      );
      debugPrint('error: $error');
      debugPrint('stackTrace: $stackTrace');
      debugPrint('======================================');

      if (!mounted) return;

      setState(() {
        _previewResult = null;
        _previewError = '게임 정보를 계산하지 못했습니다.';
      });

      _showSearchErrorBubble();
    } finally {
      if (mounted) {
        setState(() {
          _isLoadingPreview = false;
        });
      }
    }
  }

  String get _previewDisplayName {
    final value = _displayNameController.text.trim();

    if (value.isEmpty) {
      return '게임 신분증 닉네임';
    }

    return value;
  }

  String _formatTopPercent(double value) {
    if (value == value.roundToDouble()) {
      return value.toStringAsFixed(0);
    }

    return value.toStringAsFixed(1);
  }

  @override
  void initState() {
    super.initState();

    final now = DateTime.now();

    _identityNumber = '${now.year}'
        '${now.month.toString().padLeft(2, '0')}'
        '${now.day.toString().padLeft(2, '0')}-'
        '${now.microsecondsSinceEpoch.toString().substring(8)}';
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _customGameNameController.dispose();
    _customGameInfoController.dispose();
    super.dispose();
  }

  void _toggleGame(GameProfile game) {
    setState(() {
      if (_selectedGameIds.contains(game.id)) {
        _selectedGameIds.remove(game.id);
      } else {
        _selectedGameIds.add(game.id);
      }

      _previewResult = null;
      _previewError = null;
    });
  }

  String get _issuedDateText {
    final now = DateTime.now();

    return '${now.year}.'
        '${now.month.toString().padLeft(2, '0')}.'
        '${now.day.toString().padLeft(2, '0')}';
  }

  Future<void> _moveToStep(int step) async {
    setState(() {
      _currentStep = step;
    });

    if (step == 3) {
      await _loadIdentityPreview();
    }
  }

  void _showMessageBubble(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          behavior: SnackBarBehavior.floating,
          backgroundColor: const Color(0xFF172338),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: const BorderSide(
              color: Color(0xFF344765),
            ),
          ),
          content: Text(
            message,
            style: const TextStyle(
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      );
  }

  Future<void> _createIdentityCard() async {
    await _generateIdentityCardImage();
  }

  void _addCustomGame() {
    final gameName = _customGameNameController.text.trim();

    final playInfo = _customGameInfoController.text.trim();

    if (gameName.isEmpty) {
      _showMessageBubble('게임명을 입력해주세요.');
      return;
    }

    if (playInfo.isEmpty) {
      _showMessageBubble(
        '플레이 시간 또는 티어를 입력해주세요.',
      );
      return;
    }

    setState(() {
      _customGames.add(
        CustomGameEntry(
          id: DateTime.now().microsecondsSinceEpoch.toString(),
          gameName: gameName,
          playInfo: playInfo,
        ),
      );

      _customGameNameController.clear();
      _customGameInfoController.clear();
    });
  }

  void _removeCustomGame(String id) {
    setState(() {
      _customGames.removeWhere(
        (game) => game.id == id,
      );
    });
  }

  @override
  void didUpdateWidget(
    covariant GameIdentityPage oldWidget,
  ) {
    super.didUpdateWidget(oldWidget);

    final availableIds = widget.games.map((game) => game.id).toSet();

    final invalidIds =
        _selectedGameIds.where((id) => !availableIds.contains(id)).toList();

    if (invalidIds.isEmpty) return;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;

      setState(() {
        _selectedGameIds.removeAll(invalidIds);
      });
    });
  }

  void _showSearchErrorBubble() {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          behavior: SnackBarBehavior.floating,
          margin: const EdgeInsets.only(
            left: 24,
            right: 24,
            bottom: 24,
          ),
          backgroundColor: const Color(0xFF2A1720),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: const BorderSide(
              color: Color(0xFF7A3547),
            ),
          ),
          duration: const Duration(seconds: 4),
          content: const Row(
            children: [
              Icon(
                Icons.error_outline_rounded,
                color: Colors.redAccent,
                size: 21,
              ),
              SizedBox(width: 10),
              Expanded(
                child: Text(
                  'API 오류가 발생하였습니다. 잠시 후 다시 시도해주시길 바랍니다.',
                  style: TextStyle(
                    color: Color(0xFFFFD8DF),
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wideLayout = constraints.maxWidth >= 1100;

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _GameIdentityHeader(),
            const SizedBox(height: 24),
            if (wideLayout)
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 6,
                    child: _buildWizard(),
                  ),
                  const SizedBox(width: 24),
                  Expanded(
                    flex: 4,
                    child: _buildPreview(),
                  ),
                ],
              )
            else
              Column(
                children: [
                  _buildWizard(),
                  const SizedBox(height: 24),
                  _buildPreview(),
                ],
              ),
          ],
        );
      },
    );
  }

  Widget _buildWizard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: const Color(0xFF081321),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: const Color(0xFF1D2A3D),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _GameIdentitySteps(
            currentStep: _currentStep,
            onStepTap: _moveToStep,
          ),
          const SizedBox(height: 28),
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 180),
            child: switch (_currentStep) {
              0 => _buildNicknameStep(),
              1 => _buildGameSelectionStep(),
              2 => _buildNewGameStep(),
              _ => _buildFinalStep(),
            },
          ),
        ],
      ),
    );
  }

  Widget _buildNicknameStep() {
    return Column(
      key: const ValueKey('nickname-step'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '신분증 닉네임',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          '게임 신분증에 표시할 이름을 입력해주세요.',
          style: TextStyle(
            color: Color(0xFF8290A4),
            fontSize: 13,
          ),
        ),
        const SizedBox(height: 20),
        TextField(
          controller: _displayNameController,
          maxLength: 12,
          decoration: const InputDecoration(
            labelText: '표시할 닉네임',
            hintText: '예: 남명종',
            filled: true,
            fillColor: Color(0xFF0E1A2A),
            border: OutlineInputBorder(),
          ),
          onChanged: (_) {
            setState(() {
              _previewResult = null;
              _previewError = null;
            });
          },
          onSubmitted: (_) {
            _moveToStep(1);
          },
        ),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            onPressed: _displayNameController.text.trim().isEmpty
                ? null
                : () => _moveToStep(1),
            icon: const Icon(
              Icons.arrow_forward_rounded,
              size: 18,
            ),
            label: const Text('다음'),
          ),
        ),
      ],
    );
  }

  Widget _buildGameSelectionStep() {
    return Column(
      key: const ValueKey('selection-step'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '등록된 게임 카드 선택',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          '선택된 게임 ${_selectedGameIds.length}개',
          style: const TextStyle(
            color: Color(0xFF8D9AAF),
            fontSize: 13,
          ),
        ),
        const SizedBox(height: 20),
        if (widget.games.isEmpty)
          const _EmptyGameAccountNotice()
        else
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: widget.games.map((game) {
              final selected = _selectedGameIds.contains(game.id);

              return SizedBox(
                width: 250,
                child: _SelectableGameAccountCard(
                  game: game,
                  selected: selected,
                  onTap: () => _toggleGame(game),
                ),
              );
            }).toList(),
          ),
        const SizedBox(height: 24),
        Row(
          children: [
            OutlinedButton(
              onPressed: () => _moveToStep(0),
              child: const Text('이전'),
            ),
            const Spacer(),
            FilledButton.icon(
              onPressed: () => _moveToStep(2),
              icon: const Icon(
                Icons.arrow_forward_rounded,
                size: 18,
              ),
              label: Text(
                _hasSelectedGames ? '다음' : '기타 게임 추가',
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildNewGameStep() {
    return Column(
      key: const ValueKey('new-game-step'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '추가 게임 계정',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          '대시보드에 없는 게임 계정을 검색해 등록할 수 있습니다.',
          style: TextStyle(
            color: Color(0xFF8290A4),
            fontSize: 13,
            height: 1.5,
          ),
        ),
        const SizedBox(height: 20),
        InkWell(
          onTap: _isAddingGame ? null : _addNewGameAccount,
          borderRadius: BorderRadius.circular(18),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(
              horizontal: 20,
              vertical: 28,
            ),
            decoration: BoxDecoration(
              color: const Color(0xFF0E1A2A),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: const Color(0xFF5746A8),
              ),
            ),
            child: _isAddingGame
                ? const Column(
                    children: [
                      SizedBox(
                        width: 28,
                        height: 28,
                        child: CircularProgressIndicator(
                          strokeWidth: 3,
                        ),
                      ),
                      SizedBox(height: 14),
                      Text(
                        '게임 계정을 등록하고 있습니다...',
                        style: TextStyle(
                          color: Color(0xFFAEB9C8),
                        ),
                      ),
                    ],
                  )
                : const Column(
                    children: [
                      Icon(
                        Icons.add_card_rounded,
                        size: 38,
                        color: Color(0xFF9886FF),
                      ),
                      SizedBox(height: 12),
                      Text(
                        '새 게임 계정 등록',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      SizedBox(height: 7),
                      Text(
                        '게임과 닉네임을 입력하면 전적을 검색한 뒤\n'
                        '대시보드와 게임 신분증에 함께 추가합니다.',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Color(0xFF8290A4),
                          fontSize: 12,
                          height: 1.5,
                        ),
                      ),
                    ],
                  ),
          ),
        ),
        const SizedBox(height: 22),
        const Row(
          children: [
            Expanded(
              child: Divider(
                color: Color(0xFF27364D),
              ),
            ),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 12),
              child: Text(
                '또는',
                style: TextStyle(
                  color: Color(0xFF77869B),
                  fontSize: 12,
                ),
              ),
            ),
            Expanded(
              child: Divider(
                color: Color(0xFF27364D),
              ),
            ),
          ],
        ),
        const SizedBox(height: 22),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: const Color(0xFF0E1A2A),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: const Color(0xFF2B3A50),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(
                    Icons.extension_rounded,
                    size: 20,
                    color: Color(0xFF9B8BFF),
                  ),
                  SizedBox(width: 8),
                  Text(
                    '기타 게임',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 7),
              const Text(
                '지원 목록에 없는 게임을 직접 추가할 수 있습니다.',
                style: TextStyle(
                  color: Color(0xFF8290A4),
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _customGameNameController,
                maxLength: 30,
                decoration: const InputDecoration(
                  labelText: '게임명',
                  hintText: '예: Minecraft',
                  filled: true,
                  fillColor: Color(0xFF101D2D),
                  border: OutlineInputBorder(),
                ),
              ),
              if (_customGames.isNotEmpty) ...[
                const SizedBox(height: 16),
                ..._customGames.map(
                  (game) => Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFF111F30),
                      borderRadius: BorderRadius.circular(13),
                      border: Border.all(
                        color: const Color(0xFF2B3A50),
                      ),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.videogame_asset_rounded,
                          size: 19,
                          color: Color(0xFF8F80E8),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                game.gameName,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                game.playInfo,
                                style: const TextStyle(
                                  color: Color(0xFF8996A9),
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          onPressed: () => _removeCustomGame(game.id),
                          icon: const Icon(
                            Icons.close_rounded,
                            size: 18,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 10),
              TextField(
                controller: _customGameInfoController,
                maxLength: 50,
                decoration: const InputDecoration(
                  labelText: '플레이 시간 및 티어',
                  hintText: '예: 1,240시간 · 다이아몬드',
                  filled: true,
                  fillColor: Color(0xFF101D2D),
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton.icon(
                  onPressed: _addCustomGame,
                  icon: const Icon(
                    Icons.add_rounded,
                    size: 18,
                  ),
                  label: const Text('기타 게임 추가'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (_selectedGames.isNotEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF101A2B),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: const Color(0xFF27364D),
              ),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.check_circle_rounded,
                  color: Color(0xFF7ECA9C),
                  size: 20,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    '현재 신분증에 ${_selectedGames.length}개의 '
                    '게임 계정이 선택되어 있습니다.',
                    style: const TextStyle(
                      color: Color(0xFFAEB9C8),
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
          ),
        const SizedBox(height: 24),
        Row(
          children: [
            OutlinedButton(
              onPressed: _isAddingGame ? null : () => _moveToStep(1),
              child: const Text('이전'),
            ),
            const Spacer(),
            TextButton(
              onPressed: _isAddingGame ? null : () => _moveToStep(3),
              child: const Text('건너뛰기'),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _isAddingGame ? null : () => _moveToStep(3),
              child: const Text('최종 확인'),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildFinalStep() {
    return Column(
      key: const ValueKey('final-step'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '최종 확인',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          '닉네임과 선택한 게임 계정을 확인해주세요.',
          style: TextStyle(
            color: Color(0xFF8290A4),
            fontSize: 13,
          ),
        ),
        const SizedBox(height: 20),
        _FinalInformationRow(
          label: '신분증 닉네임',
          value: _previewDisplayName,
        ),
        _FinalInformationRow(
          label: '선택한 게임',
          value: '${_selectedGames.length + _customGames.length}개',
        ),
        _FinalInformationRow(
          label: '정식 지원 게임',
          value: '${_selectedGames.length}개',
        ),
        _FinalInformationRow(
          label: '기타 게임',
          value: '${_customGames.length}개',
        ),
        _FinalInformationRow(
          label: '이미지 크기',
          value: '1290 × 2070 PNG',
        ),
        _FinalInformationRow(
          label: '평균 게임력',
          value: _isLoadingPreview
              ? '계산 중'
              : _previewResult?.averageTopPercent != null
                  ? '상위 ${_formatTopPercent(
                      _previewResult!.averageTopPercent!,
                    )}%'
                  : _previewError != null
                      ? 'API 계산 불가'
                      : '계산 제외',
        ),
        _FinalInformationRow(
          label: '평균 반영 게임',
          value: '${_previewResult?.includedGameCount ?? 0}개',
        ),
        const SizedBox(height: 24),
        Row(
          children: [
            OutlinedButton(
              onPressed: () => _moveToStep(2),
              child: const Text('이전'),
            ),
            const Spacer(),
            FilledButton.icon(
              onPressed: _hasAnyGame && !_isGeneratingImage
                  ? _createIdentityCard
                  : null,
              icon: _isGeneratingImage
                  ? const SizedBox(
                      width: 17,
                      height: 17,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(
                      Icons.download_rounded,
                      size: 18,
                    ),
              label: Text(
                _isGeneratingImage ? '이미지 생성 중' : '게임 신분증 생성',
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildPreview() {
    return Center(
      child: RepaintBoundary(
        key: _identityCardKey,
        child: SizedBox(
          width: 430,
          child: _GameIdentityPreview(
            displayName: _previewDisplayName,
            identityNumber: _identityNumber,
            issuedDate: _issuedDateText,
            selectedGames: _selectedGames,
            customGames: _customGames,
            hasCompetitiveGame: _hasCompetitiveGame,
            hasRpgGame: _hasRpgGame,
            previewResult: _previewResult,
            isLoadingPreview: _isLoadingPreview,
            previewError: _previewError,
          ),
        ),
      ),
    );
  }
}

class _GameIdentityHeader extends StatelessWidget {
  const _GameIdentityHeader();

  @override
  Widget build(BuildContext context) {
    return const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.badge_outlined,
              color: Color(0xFF8B72FF),
              size: 28,
            ),
            SizedBox(width: 10),
            Text(
              '게임 신분증 생성',
              style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
        SizedBox(height: 8),
        Text(
          '나의 게임 기록을 한 장의 게임 신분증으로 만들어보세요.',
          style: TextStyle(
            color: Color(0xFF8290A4),
            fontSize: 13,
          ),
        ),
      ],
    );
  }
}

class _GameIdentitySteps extends StatelessWidget {
  const _GameIdentitySteps({
    required this.currentStep,
    required this.onStepTap,
  });

  final int currentStep;
  final ValueChanged<int> onStepTap;

  static const _labels = [
    '닉네임',
    '게임 선택',
    '계정 추가',
    '최종 확인',
  ];

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: List.generate(
        _labels.length,
        (index) {
          final selected = currentStep == index;
          final completed = currentStep > index;

          return InkWell(
            onTap: () => onStepTap(index),
            borderRadius: BorderRadius.circular(30),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 160),
              padding: const EdgeInsets.symmetric(
                horizontal: 14,
                vertical: 10,
              ),
              decoration: BoxDecoration(
                color: selected
                    ? const Color(0xFF362A74)
                    : const Color(0xFF0E1A2A),
                borderRadius: BorderRadius.circular(30),
                border: Border.all(
                  color: selected
                      ? const Color(0xFF826DFF)
                      : const Color(0xFF24344A),
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircleAvatar(
                    radius: 10,
                    backgroundColor: selected || completed
                        ? const Color(0xFF8069FF)
                        : const Color(0xFF26354A),
                    child: completed
                        ? const Icon(
                            Icons.check_rounded,
                            size: 13,
                            color: Colors.white,
                          )
                        : Text(
                            '${index + 1}',
                            style: const TextStyle(
                              fontSize: 10,
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                  ),
                  const SizedBox(width: 7),
                  Text(
                    _labels[index],
                    style: TextStyle(
                      color: selected ? Colors.white : const Color(0xFFA5B0C0),
                      fontSize: 12,
                      fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _SelectableGameAccountCard extends StatelessWidget {
  const _SelectableGameAccountCard({
    required this.game,
    required this.selected,
    required this.onTap,
  });

  final GameProfile game;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? const Color(0xFF241D4B) : const Color(0xFF0E1A2A),
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color:
                  selected ? const Color(0xFF8069FF) : const Color(0xFF24344A),
              width: selected ? 2 : 1,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: const Color(0xFF172438),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  selected ? Icons.check_rounded : Icons.sports_esports_rounded,
                  color: selected
                      ? const Color(0xFF9D8CFF)
                      : const Color(0xFF78879B),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      game.type.displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11,
                        color: Color(0xFF8290A4),
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      game.accountName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${game.primaryLabel} · ${game.primaryValue}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Color(0xFFA5B0C0),
                        fontSize: 11,
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

class _EmptyGameAccountNotice extends StatelessWidget {
  const _EmptyGameAccountNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 20,
        vertical: 32,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFF0E1A2A),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: const Color(0xFF24344A),
        ),
      ),
      child: const Column(
        children: [
          Icon(
            Icons.sports_esports_outlined,
            size: 34,
            color: Color(0xFF748399),
          ),
          SizedBox(height: 12),
          Text(
            '대시보드에 등록된 게임 계정이 없습니다.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontWeight: FontWeight.w700,
            ),
          ),
          SizedBox(height: 6),
          Text(
            '등록된 게임 카드는 없지만,\n다음 단계에서 기타 게임을 직접 추가할 수 있습니다.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Color(0xFF8290A4),
              fontSize: 12,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }
}

class _GameIdentityPreview extends StatelessWidget {
  const _GameIdentityPreview({
    required this.displayName,
    required this.identityNumber,
    required this.issuedDate,
    required this.selectedGames,
    required this.hasCompetitiveGame,
    required this.hasRpgGame,
    required this.customGames,
    required this.previewResult,
    required this.isLoadingPreview,
    required this.previewError,
  });

  final String displayName;
  final String identityNumber;
  final String issuedDate;

  final List<GameProfile> selectedGames;
  final List<CustomGameEntry> customGames;

  final bool hasCompetitiveGame;
  final bool hasRpgGame;

  final GameIdentityPreviewResult? previewResult;
  final bool isLoadingPreview;
  final String? previewError;
  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      height: 690,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: const Color(0xFF6E5AE6),
          width: 1.4,
        ),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xFF121D35),
            Color(0xFF18143A),
            Color(0xFF081321),
          ],
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x55000000),
            blurRadius: 28,
            offset: Offset(0, 14),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildCardHeader(),
          const SizedBox(height: 20),
          _buildOwnerInformation(),
          const SizedBox(height: 18),
          const Divider(
            height: 1,
            color: Color(0xFF33405A),
          ),
          const SizedBox(height: 16),
          Expanded(
            child: _buildGameInformation(),
          ),
          if (previewResult?.averageTopPercent != null) ...[
            const SizedBox(height: 10),
            _buildGamePowerArea(),
          ],
          const SizedBox(height: 10),
          _buildEvaluationArea(),
        ],
      ),
    );
  }

  String _previewMessage() {
    if (isLoadingPreview) {
      return '게임력을 계산하고 있습니다...';
    }

    /*
   * API 계산에 성공한 경우 가장 먼저 평가 문구를 사용한다.
   */
    final result = previewResult;

    if (result != null && result.evaluationMessage.trim().isNotEmpty) {
      return result.evaluationMessage;
    }

    /*
   * API 계산에 실패했을 때만 대체 문구를 표시한다.
   */
    if (previewError != null) {
      if (hasCompetitiveGame && hasRpgGame) {
        return '$displayName 님은\n'
            '모험과 경쟁을 함께 즐기는 게이머시군여!';
      }

      if (hasRpgGame) {
        return '$displayName 님은\n'
            '세상을 지키는 모험가시군여!';
      }

      if (hasCompetitiveGame) {
        return '$displayName 님은\n'
            '승부를 즐기는 도전자시군여!';
      }

      if (customGames.isNotEmpty) {
        return '$displayName 님만의\n'
            '게임 세계가 가득하군여!';
      }
    }

    /*
   * 아직 API를 호출하지 않은 초기 상태의 문구
   */
    if (!hasCompetitiveGame && hasRpgGame) {
      return '$displayName 님은\n'
          '세상을 지키는 모험가시군여!';
    }

    if (!hasCompetitiveGame && !hasRpgGame && customGames.isNotEmpty) {
      return '$displayName 님만의\n'
          '게임 세계가 가득하군여!';
    }

    if (hasCompetitiveGame) {
      return '$displayName 님의 평균 게임력은\n'
          '최종 확인 단계에서 계산됩니다.';
    }

    return '게임 계정을 선택해주세요.';
  }

  String get _gamePowerText {
    final percent = previewResult?.averageTopPercent;

    if (percent == null) {
      return '-';
    }

    final formatted = _formatPercent(percent);
    final estimated = previewResult!.games.any(
      (game) => game.includedInAverage && game.estimated,
    );

    return estimated ? '상위 약 $formatted%' : '상위 $formatted%';
  }

  String get _includedGameCountText {
    final count = previewResult?.includedGameCount ?? 0;

    return '평균 반영 게임 $count개';
  }

  String _formatPercent(double value) {
    if (value == value.roundToDouble()) {
      return value.toStringAsFixed(0);
    }

    return value.toStringAsFixed(1);
  }

  Widget _buildCardHeader() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 38,
          height: 38,
          decoration: BoxDecoration(
            color: const Color(0xFF7965F2),
            borderRadius: BorderRadius.circular(11),
          ),
          child: const Icon(
            Icons.badge_rounded,
            color: Colors.white,
            size: 22,
          ),
        ),
        const SizedBox(width: 11),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'GAME ID CARD',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1.7,
                ),
              ),
              SizedBox(height: 2),
              Text(
                '게임 신분증',
                style: TextStyle(
                  color: Color(0xFF9AA6B8),
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            const Text(
              'MY GAME HUB',
              style: TextStyle(
                fontSize: 9,
                fontWeight: FontWeight.w800,
                color: Color(0xFFA59BCF),
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 5),
            Text(
              'NO. $identityNumber',
              style: const TextStyle(
                fontSize: 8,
                color: Color(0xFF758298),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildOwnerInformation() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0x55101B2E),
        borderRadius: BorderRadius.circular(17),
        border: Border.all(
          color: const Color(0xFF303C57),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              color: const Color(0xFF20294A),
              borderRadius: BorderRadius.circular(17),
            ),
            child: const Icon(
              Icons.person_rounded,
              size: 31,
              color: Color(0xFFA99DFF),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'GAMER NAME',
                  style: TextStyle(
                    color: Color(0xFF7F8CA1),
                    fontSize: 8,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  displayName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 23,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              const Text(
                'ISSUED',
                style: TextStyle(
                  color: Color(0xFF7F8CA1),
                  fontSize: 8,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                issuedDate,
                style: const TextStyle(
                  color: Color(0xFFC1C9D6),
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildGameInformation() {
    if (selectedGames.isEmpty && customGames.isEmpty) {
      return const Center(
        child: Text(
          '게임을 선택하거나 추가하면\n이곳에 신분증 정보가 표시됩니다.',
          textAlign: TextAlign.center,
          style: TextStyle(
            color: Color(0xFF77869B),
            fontSize: 12,
            height: 1.6,
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'GAME RECORD',
          style: TextStyle(
            color: Color(0xFF8996AA),
            fontSize: 9,
            fontWeight: FontWeight.w900,
            letterSpacing: 1.3,
          ),
        ),
        const SizedBox(height: 10),
        ...selectedGames.take(4).map(
          (game) {
            GameIdentityPreviewEntry? calculatedEntry;

            final result = previewResult;

            if (result != null) {
              for (final entry in result.games) {
                if (entry.gameAccountId == game.id) {
                  calculatedEntry = entry;
                  break;
                }
              }
            }

            return _PreviewGameRow(
              game: game,
              calculatedEntry: calculatedEntry,
            );
          },
        ),
        if (selectedGames.length > 4)
          Padding(
            padding: const EdgeInsets.only(
              left: 4,
              top: 1,
            ),
            child: Text(
              '외 ${selectedGames.length - 4}개 게임',
              style: const TextStyle(
                color: Color(0xFF78859A),
                fontSize: 9,
              ),
            ),
          ),
        if (customGames.isNotEmpty) ...[
          const SizedBox(height: 7),
          const Divider(
            height: 1,
            color: Color(0xFF2A3650),
          ),
          const SizedBox(height: 8),
          const Text(
            'OTHER GAMES',
            style: TextStyle(
              color: Color(0xFF7D899C),
              fontSize: 8,
              fontWeight: FontWeight.w900,
              letterSpacing: 1.1,
            ),
          ),
          const SizedBox(height: 6),
          ...customGames.take(3).map(
                (game) => Padding(
                  padding: const EdgeInsets.only(bottom: 5),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.videogame_asset_rounded,
                        size: 12,
                        color: Color(0xFF8D7DDF),
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          game.gameName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          game.playInfo,
                          textAlign: TextAlign.right,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFF8C98AA),
                            fontSize: 8,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          if (customGames.length > 3)
            Text(
              '외 ${customGames.length - 3}개',
              style: const TextStyle(
                color: Color(0xFF718096),
                fontSize: 8,
              ),
            ),
        ],
      ],
    );
  }

  Widget _buildGamePowerArea() {
    final hasGamePower = previewResult?.averageTopPercent != null;

    if (!hasGamePower) {
      return const SizedBox.shrink();
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 12,
      ),
      decoration: BoxDecoration(
        color: const Color(0x551B2447),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(
          color: const Color(0xFF3B4770),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: const Color(0xFF302B69),
              borderRadius: BorderRadius.circular(11),
            ),
            child: const Icon(
              Icons.auto_graph_rounded,
              color: Color(0xFFAA9DFF),
              size: 21,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'GAME POWER',
                  style: TextStyle(
                    color: Color(0xFF8794A9),
                    fontSize: 8,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1.1,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  _gamePowerText,
                  style: const TextStyle(
                    color: Color(0xFFE2DDFF),
                    fontSize: 17,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
            ),
          ),
          Text(
            _includedGameCountText,
            style: const TextStyle(
              color: Color(0xFF8895A9),
              fontSize: 9,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEvaluationArea() {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(
        minHeight: 76,
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 14,
      ),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(17),
        gradient: const LinearGradient(
          colors: [
            Color(0xAA302668),
            Color(0xAA1C294F),
          ],
        ),
        border: Border.all(
          color: const Color(0xFF514788),
        ),
      ),
      child: isLoadingPreview
          ? const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: 17,
                  height: 17,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                  ),
                ),
                SizedBox(width: 10),
                Text(
                  '게임력을 계산하고 있습니다...',
                  style: TextStyle(
                    color: Color(0xFFD9D5FF),
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            )
          : Center(
              child: Text(
                _previewMessage(),
                textAlign: TextAlign.center,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Color(0xFFE3DFFF),
                  fontSize: 13,
                  fontWeight: FontWeight.w900,
                  height: 1.4,
                ),
              ),
            ),
    );
  }
}

class _PreviewGameRow extends StatelessWidget {
  const _PreviewGameRow({
    required this.game,
    required this.calculatedEntry,
  });

  final GameProfile game;
  final GameIdentityPreviewEntry? calculatedEntry;

  @override
  Widget build(BuildContext context) {
    final rawMetricValue =
        calculatedEntry?.metricValue ?? _fallbackMetricValue();
    final rawMetricLabel =
        calculatedEntry?.metricLabel ?? _fallbackMetricLabel();

    final metricLabel = _identityMetricLabel(rawMetricLabel);
    final metricValue = _identityMetricValue(rawMetricValue);
    final topPercent = calculatedEntry?.topPercent;

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            padding: const EdgeInsets.all(7),
            decoration: BoxDecoration(
              color: const Color(0xFF18253A),
              borderRadius: BorderRadius.circular(11),
              border: Border.all(
                color: const Color(0xFF2C3950),
              ),
            ),
            child: Image.asset(
              game.type.iconAsset,
              fit: BoxFit.contain,
              errorBuilder: (
                context,
                error,
                stackTrace,
              ) {
                return const Icon(
                  Icons.sports_esports_rounded,
                  size: 18,
                  color: Color(0xFFA495FF),
                );
              },
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  game.type.identityDisplayName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF8290A4),
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  game.accountName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (topPercent != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    calculatedEntry?.estimated == true
                        ? '상위 약 ${_formatPercent(topPercent)}%'
                        : '상위 ${_formatPercent(topPercent)}%',
                    style: const TextStyle(
                      color: Color(0xFF9F91FF),
                      fontSize: 10,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 10),
          Flexible(
            child: Text(
              '$metricLabel\n$metricValue',
              textAlign: TextAlign.right,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Color(0xFFD2CCFF),
                fontSize: 11,
                fontWeight: FontWeight.w700,
                height: 1.3,
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _fallbackMetricLabel() {
    switch (game.type) {
      case GameType.lostArk:
      case GameType.mapleStory:
      case GameType.dungeonFighter:
        return '전투력';

      default:
        return game.primaryLabel;
    }
  }

  String _identityMetricLabel(String label) {
    switch (game.type) {
      case GameType.leagueOfLegends:
      case GameType.tft:
      case GameType.valorant:
      case GameType.battlegrounds:
      case GameType.eternalReturn:
        return '티어';

      case GameType.lostArk:
      case GameType.mapleStory:
      case GameType.dungeonFighter:
        return '전투력';
    }
  }

  String _identityMetricValue(String value) {
    final trimmed = value.trim();

    if (trimmed.isEmpty) {
      return '-';
    }

    switch (game.type) {
      case GameType.leagueOfLegends:
      case GameType.tft:
        // 예: EMERALD IV · 47 LP · 138승 120패
        return trimmed.split('·').first.trim();

      case GameType.valorant:
        // 예: Silver 2
        return trimmed;

      case GameType.battlegrounds:
        // 예: Survivor 1 또는 Diamond 3
        return trimmed.split('·').first.trim();

      case GameType.eternalReturn:
        // 예: 8,207 RP처럼 점수가 들어온다면 앞부분만 사용
        return trimmed.split('·').first.trim();

      case GameType.lostArk:
      case GameType.mapleStory:
      case GameType.dungeonFighter:
        return trimmed;
    }
  }

  String _fallbackMetricValue() {
    if (game.type == GameType.dungeonFighter) {
      return _findValueByLabel('전투력');
    }

    if (game.type == GameType.lostArk || game.type == GameType.mapleStory) {
      return _findValueByLabel('전투력');
    }

    return game.primaryValue;
  }

  String _findValueByLabel(String label) {
    if (game.primaryLabel == label) {
      return game.primaryValue;
    }

    if (game.secondaryLabel == label) {
      return game.secondaryValue ?? '-';
    }

    if (game.tertiaryLabel == label) {
      return game.tertiaryValue ?? '-';
    }

    return '-';
  }

  String _formatPercent(double value) {
    if (value == value.roundToDouble()) {
      return value.toStringAsFixed(0);
    }

    return value.toStringAsFixed(1);
  }
}

class _FinalInformationRow extends StatelessWidget {
  const _FinalInformationRow({
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: const TextStyle(
                color: Color(0xFF8290A4),
                fontSize: 12,
              ),
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class CustomGameEntry {
  const CustomGameEntry({
    required this.id,
    required this.gameName,
    required this.playInfo,
  });

  final String id;
  final String gameName;
  final String playInfo;
}
