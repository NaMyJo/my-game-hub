import 'dart:convert';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:crop_your_image/crop_your_image.dart';
import 'package:image/image.dart' as image_lib;
import 'package:url_launcher/url_launcher.dart';

import '../models/game_identity_preview.dart';
import '../models/game_profile.dart';
import '../models/game_profile_summary.dart';
import '../services/api_client.dart';
import '../services/game_identity_repository.dart';
import '../services/game_profile_summary_repository.dart';
import '../services/public_profile_repository.dart';
import '../utils/image_download.dart';
import '../widgets/profile_image_input_overlay.dart';
import 'public_pages.dart';

Uint8List _normalizeIdentityProfileImage(Uint8List bytes) {
  final decodedImage = image_lib.decodeImage(bytes);

  if (decodedImage == null) {
    return bytes;
  }

  return Uint8List.fromList(
    image_lib.encodeJpg(
      image_lib.copyResize(
        decodedImage,
        width: 512,
        height: 512,
        interpolation: image_lib.Interpolation.linear,
      ),
      quality: 86,
    ),
  );
}

class GameIdentityPage extends StatefulWidget {
  const GameIdentityPage({
    super.key,
    required this.games,
    required this.onAddGame,
    required this.onProfileApplied,
    this.showHeader = true,
  });

  final List<GameProfile> games;
  final Future<GameProfile?> Function() onAddGame;
  final bool showHeader;

  final void Function(
    GameProfileSummary profile,
  ) onProfileApplied;

  @override
  State<GameIdentityPage> createState() => _GameIdentityPageState();
}

class _GameIdentityPageState extends State<GameIdentityPage> {
  final TextEditingController _displayNameController = TextEditingController();

  final Set<int> _selectedGameIds = {};
  final GlobalKey _identityCardKey = GlobalKey();
  final GlobalKey _pageTopKey = GlobalKey();
  Uint8List? _profileImageBytes;

  bool _isGeneratingImage = false;
  bool _showPreview = false;
  bool _showLatestIdentity = false;
  int _currentStep = 0;
  late final String _identityNumber;
  List<GameProfile> get _selectedGames {
    return widget.games
        .where((game) => _selectedGameIds.contains(game.id))
        .toList();
  }

  GameIdentityPreviewResult? _previewResult;
  GameIdentityHistory? _latestIdentity;
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

  Future<void> _processSelectedProfileImage(Uint8List imageBytes) async {
    try {
      FocusManager.instance.primaryFocus?.unfocus();
      if (!mounted) return;

      final croppedBytes = await showDialog<Uint8List>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => _SquarePhotoCropDialog(
          imageBytes: imageBytes,
        ),
      );

      if (croppedBytes == null || !mounted) {
        return;
      }

      setState(() {
        _profileImageBytes = croppedBytes;
      });
    } catch (error, stackTrace) {
      debugPrint('PROFILE IMAGE PICK ERROR: $error');
      debugPrint('$stackTrace');

      if (mounted) {
        _showMessageBubble('사진을 불러오지 못했습니다. 다시 시도해주세요.');
      }
    }
  }

  void _handleProfileImagePickerError(
    Object error,
    StackTrace stackTrace,
  ) {
    debugPrint('PROFILE IMAGE PICK ERROR: $error');
    debugPrint('$stackTrace');
    if (mounted) {
      _showMessageBubble('사진을 불러오지 못했습니다. 다시 시도해주세요.');
    }
  }

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

  Future<void> _openLatestIdentity(
    GameIdentityHistory identity,
  ) {
    return showDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierColor: Colors.black.withValues(
        alpha: 0.78,
      ),
      builder: (dialogContext) {
        return Stack(
          fit: StackFit.expand,
          children: [
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () {
                Navigator.of(dialogContext).pop();
              },
              child: Center(
                child: GestureDetector(
                  onTap: () {
                    // 카드 내부 클릭은 닫히지 않도록 막음
                  },
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(24),
                    child: SizedBox(
                      width: 430,
                      child: _GameIdentityPreview(
                        displayName: identity.displayName,
                        profileImageBytes: identity.profileImageBytes,
                        identityNumber: identity.identityNumber,
                        issuedDate: identity.issuedDate,
                        selectedGames: identity.selectedGames,
                        customGames: identity.customGames,
                        hasCompetitiveGame: identity.hasCompetitiveGame,
                        hasRpgGame: identity.hasRpgGame,
                        previewResult: identity.previewResult,
                        isLoadingPreview: false,
                        previewError: identity.previewError,
                        showDetailActions: true,
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              top: MediaQuery.paddingOf(dialogContext).top + 12,
              right: 12,
              child: Material(
                color: const Color(0xFF111B2B),
                shape: const CircleBorder(
                  side: BorderSide(color: Color(0xFF40506A)),
                ),
                child: IconButton(
                  onPressed: () => Navigator.of(dialogContext).pop(),
                  tooltip: '닫기',
                  color: const Color(0xFFF2EFFF),
                  icon: const Icon(Icons.close_rounded),
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _showGeneratedIdentityPreview({
    required Uint8List bytes,
    required String fileName,
  }) {
    return showDialog<void>(
      context: context,
      barrierDismissible: false,
      barrierColor: Colors.black.withValues(alpha: 0.86),
      builder: (dialogContext) {
        return Material(
          color: Colors.transparent,
          child: SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final availableWidth = constraints.maxWidth - 40;
                final previewWidth =
                    availableWidth < 390.0 ? availableWidth : 390.0;

                return Stack(
                  fit: StackFit.expand,
                  children: [
                    SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(20, 68, 20, 28),
                      child: Center(
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(22),
                          child: Image.memory(
                            bytes,
                            width: previewWidth,
                            fit: BoxFit.fitWidth,
                            filterQuality: FilterQuality.high,
                          ),
                        ),
                      ),
                    ),
                    Positioned(
                      top: 12,
                      left: 16,
                      child: _GeneratedPreviewActionButton(
                        tooltip: '미리보기 닫기',
                        icon: Icons.close_rounded,
                        onPressed: () => Navigator.of(dialogContext).pop(),
                      ),
                    ),
                    Positioned(
                      top: 12,
                      right: 16,
                      child: _GeneratedPreviewActionButton(
                        tooltip: '게임 신분증 저장',
                        icon: Icons.download_rounded,
                        onPressed: () async {
                          await downloadPng(bytes: bytes, fileName: fileName);
                        },
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        );
      },
    );
  }

  Future<void> _generateIdentityCardImage() async {
    if (_isGeneratingImage) return;
    final restorePreviewHidden = !_showPreview;

    final displayName = _previewDisplayName;

    if (!_hasAnyGame) {
      _showMessageBubble('게임을 하나 이상 선택해주세요.');
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
      _showPreview = true;
    });

    try {
      await WidgetsBinding.instance.endOfFrame;

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

// 현재 화면 즉시 반영
      setState(() {
        if (restorePreviewHidden) {
          _showPreview = false;
        }
        _latestIdentity = GameIdentityHistory(
          displayName: displayName,
          profileImageBytes: _profileImageBytes,
          identityNumber: _identityNumber,
          issuedDate: _issuedDateText,
          selectedGames: List<GameProfile>.from(
            _selectedGames,
          ),
          customGames: List<CustomGameEntry>.from(
            _customGames,
          ),
          previewResult: _previewResult,
          previewError: _previewError,
          hasCompetitiveGame: _hasCompetitiveGame,
          hasRpgGame: _hasRpgGame,
        );
      });

// Neon 영구 저장
      await _saveLatestIdentity();

      if (!mounted) return;

      await _showGeneratedIdentityPreview(
        bytes: bytes,
        fileName: '게임신분증_$safeDisplayName.png',
      );

      if (!mounted) return;

      final applyToProfile = await _askApplyToDashboardProfile();

      if (!mounted) return;

      if (applyToProfile == true) {
        await _applyIdentityToDashboardProfile();
      }
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
          if (restorePreviewHidden) _showPreview = false;
        });
      }
    }
  }

  Future<bool?> _askApplyToDashboardProfile() {
    return showDialog<bool>(
      context: context,
      builder: (context) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        return AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0B1524) : Colors.white,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          title: Row(
            children: [
              const Icon(
                Icons.account_circle_outlined,
                color: Color(0xFF9C8BFF),
              ),
              const SizedBox(width: 9),
              Text(
                '게임 프로필 반영',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                  color: isDark ? Colors.white : const Color(0xFF202636),
                ),
              ),
            ],
          ),
          content: Text(
            '생성한 게임 신분증의 게임력을\n'
            '대시보드 게임 프로필에 반영하시겠습니까?\n\n'
            '반영한 정보는 다음 로그인에서도 유지됩니다.',
            style: TextStyle(
              color: isDark ? const Color(0xFFAEB9C8) : const Color(0xFF596579),
              height: 1.6,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('아니요'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('반영하기'),
            ),
          ],
        );
      },
    );
  }

  Future<void> _applyIdentityToDashboardProfile() async {
    final displayName = _previewDisplayName;

    /*
   * 경쟁 게임이 있으면 계산된 평균 게임력.
   * RPG 전용이면 null.
   */
    final gamePowerPercent = _previewResult?.averageTopPercent;

    /*
   * 평균 게임력에 실제 반영된 경쟁 게임 수.
   * RPG 전용이라면 경쟁게임 반영수는 0이 되는데,
   * Dashboard에서는 전체 신분증 게임 수를 보여주는 게
   * 더 자연스러우므로 전체 게임 개수를 저장한다.
   */
    final reflectedGameCount = _selectedGames.length + _customGames.length;

    String evaluationMessage;

    final apiMessage = _previewResult?.evaluationMessage.trim();

    if (apiMessage != null && apiMessage.isNotEmpty) {
      evaluationMessage = _extractEvaluationComment(
        apiMessage,
      );
    } else if (!_hasCompetitiveGame && _hasRpgGame) {
      evaluationMessage = '세상을 지키는 모험가시군여!';
    } else if (!_hasCompetitiveGame &&
        !_hasRpgGame &&
        _customGames.isNotEmpty) {
      evaluationMessage = '나만의 게임 세계가 가득하군여!';
    } else {
      evaluationMessage = '게임을 즐기는 멋진 게이머시군여!';
    }

    try {
      final savedProfile =
          await GameProfileSummaryRepository.instance.saveProfile(
        identityNickname: displayName,
        gamePowerPercent: gamePowerPercent,
        reflectedGameCount: reflectedGameCount,
        evaluationMessage: evaluationMessage,
        profileImageBytes: _profileImageBytes,
      );

      _resetIdentityBuilder();
      widget.onProfileApplied(savedProfile);
      if (!mounted) return;

      _showMessageBubble(
        '대시보드 게임 프로필에 반영되었습니다.',
      );
    } on ApiException catch (error) {
      if (!mounted) return;

      debugPrint(
        'GAME PROFILE SAVE ERROR: '
        '${error.statusCode} / ${error.message}',
      );

      _showSearchErrorBubble();
    } catch (error, stackTrace) {
      debugPrint(
        '===== GAME PROFILE SAVE ERROR =====',
      );
      debugPrint('error: $error');
      debugPrint('stackTrace: $stackTrace');

      if (!mounted) return;

      _showSearchErrorBubble();
    }
  }

  void _resetIdentityBuilder() {
    _displayNameController.clear();
    _customGameNameController.clear();
    _customGameInfoController.clear();

    setState(() {
      _currentStep = 0;
      _selectedGameIds.clear();
      _customGames.clear();
      _profileImageBytes = null;
      _previewResult = null;
      _previewError = null;
      _isLoadingPreview = false;
      _showPreview = false;
    });
  }

  String _extractEvaluationComment(
    String message,
  ) {
    final lines = message
        .split('\n')
        .map((line) => line.trim())
        .where((line) => line.isNotEmpty)
        .toList();

    if (lines.length >= 2) {
      return lines.skip(1).join(' ');
    }

    return message;
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

  Future<void> _saveLatestIdentity() async {
    final displayName = _previewDisplayName;

    String evaluationMessage;

    final apiMessage = _previewResult?.evaluationMessage.trim();

    if (apiMessage != null && apiMessage.isNotEmpty) {
      evaluationMessage = _extractEvaluationComment(
        apiMessage,
      );
    } else if (!_hasCompetitiveGame && _hasRpgGame) {
      evaluationMessage = '세상을 지키는 모험가시군여!';
    } else if (!_hasCompetitiveGame &&
        !_hasRpgGame &&
        _customGames.isNotEmpty) {
      evaluationMessage = '나만의 게임 세계가 가득하군여!';
    } else {
      evaluationMessage = '게임을 즐기는 멋진 게이머시군여!';
    }

    final snapshotJson = _buildIdentitySnapshotJson();

    await GameIdentityRepository.instance.saveLatest(
      identityNumber: _identityNumber,
      displayName: displayName,
      issuedDate: _issuedDateText,
      gamePowerPercent: _previewResult?.averageTopPercent,
      evaluationMessage: evaluationMessage,
      snapshotJson: snapshotJson,
    );
  }

  Future<void> _loadIdentityPreview() async {
    final displayName = _previewDisplayName;

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
      return 'The Gamer';
    }

    return value;
  }

  Future<void> _editDisplayNameFromFinalStep() async {
    final editedName = await showDialog<String>(
      context: context,
      barrierColor: Colors.black.withValues(alpha: 0.7),
      builder: (dialogContext) => _IdentityNicknameEditDialog(
        initialValue: _previewDisplayName,
      ),
    );

    if (!mounted || editedName == null) return;

    final normalizedName =
        editedName.trim().isEmpty ? 'The Gamer' : editedName.trim();
    setState(() {
      _displayNameController.text = normalizedName;
      _previewResult = null;
      _previewError = null;
    });
    await _loadIdentityPreview();
  }

  String _buildIdentitySnapshotJson() {
    return jsonEncode({
      'selectedGames': _selectedGames.map((game) {
        final calculatedEntry = _findPreviewEntry(game.id);
        return {
          'id': game.id,
          'gameType': game.type.apiValue,
          'accountName': game.accountName,
          'primaryLabel': game.primaryLabel,
          'primaryValue': game.primaryValue,
          'secondaryLabel': game.secondaryLabel,
          'secondaryValue': game.secondaryValue,
          'metricLabel': calculatedEntry?.metricLabel,
          'metricValue': calculatedEntry?.metricValue,
          'topPercent': calculatedEntry?.topPercent,
          'includedInAverage': calculatedEntry?.includedInAverage ?? false,
          'estimated': calculatedEntry?.estimated,
          'exclusionReason': calculatedEntry?.exclusionReason,
        };
      }).toList(),
      'customGames': _customGames.map((game) {
        return {
          'id': game.id,
          'gameName': game.gameName,
          'playInfo': game.playInfo,
        };
      }).toList(),
      'averageTopPercent': _previewResult?.averageTopPercent,
      'profileImageBase64':
          _profileImageBytes == null ? null : base64Encode(_profileImageBytes!),
      'displayName': _previewResult?.displayName ?? _previewDisplayName,
      'evaluationType': _previewResult?.evaluationType,
      'includedGameCount': _previewResult?.includedGameCount,
      'evaluationMessage': _previewResult?.evaluationMessage,
      'hasCompetitiveGame': _hasCompetitiveGame,
      'hasRpgGame': _hasRpgGame,
    });
  }

  GameIdentityPreviewEntry? _findPreviewEntry(
    int gameId,
  ) {
    final result = _previewResult;

    if (result == null) {
      return null;
    }

    for (final entry in result.games) {
      if (entry.gameAccountId == gameId) {
        return entry;
      }
    }

    return null;
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

    _loadLatestIdentity();
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

    if (!mounted || MediaQuery.sizeOf(context).width >= 1100) return;
    await WidgetsBinding.instance.endOfFrame;
    final topContext = _pageTopKey.currentContext;
    if (topContext == null || !topContext.mounted) return;
    await Scrollable.ensureVisible(
      topContext,
      alignment: 0,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOutCubic,
    );
  }

  void _showMessageBubble(String message) {
    final overlay = Overlay.of(context);

    late OverlayEntry entry;

    entry = OverlayEntry(
      builder: (context) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        return Positioned.fill(
          child: IgnorePointer(
            child: Center(
              child: Material(
                color: Colors.transparent,
                child: Container(
                  constraints: const BoxConstraints(
                    maxWidth: 420,
                  ),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 14,
                  ),
                  decoration: BoxDecoration(
                    color: isDark
                        ? const Color(0xE61A2332)
                        : const Color(0xF2FFFFFF),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: isDark
                          ? const Color(0xFF364258)
                          : const Color(0xFFD8DEE8),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(
                          alpha: isDark ? 0.28 : 0.12,
                        ),
                        blurRadius: 18,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: Text(
                    message,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: isDark ? Colors.white : const Color(0xFF283142),
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      height: 1.4,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );

    overlay.insert(entry);

    Future.delayed(
      const Duration(seconds: 2),
      () {
        if (entry.mounted) {
          entry.remove();
        }
      },
    );
  }

  Future<void> _loadLatestIdentity() async {
    try {
      final json = await GameIdentityRepository.instance.getLatest();

      if (json == null) {
        return;
      }

      final snapshotRaw = json['snapshotJson'];

      if (snapshotRaw is! String || snapshotRaw.trim().isEmpty) {
        return;
      }

      final decoded = jsonDecode(snapshotRaw);

      if (decoded is! Map<String, dynamic>) {
        return;
      }

      // ==============================
      // 1. 정식 지원 게임 복원
      // ==============================

      final restoredGames = <GameProfile>[];

      final selectedGamesJson = decoded['selectedGames'];

      if (selectedGamesJson is List) {
        for (final item in selectedGamesJson) {
          if (item is! Map) {
            continue;
          }

          final id = item['id'];

          if (id is! int) {
            continue;
          }

          GameProfile? matchedGame;

          for (final game in widget.games) {
            if (game.id == id) {
              matchedGame = game;
              break;
            }
          }

          if (matchedGame != null) {
            restoredGames.add(
              matchedGame,
            );
          }
        }
      }

      // ==============================
      // 2. 기타 게임 복원
      // ==============================

      final restoredCustomGames = <CustomGameEntry>[];

      final customGamesJson = decoded['customGames'];

      if (customGamesJson is List) {
        for (final item in customGamesJson) {
          if (item is! Map) {
            continue;
          }

          final id = item['id']?.toString();

          final gameName = item['gameName']?.toString();

          final playInfo = item['playInfo']?.toString();

          if (id == null || gameName == null || playInfo == null) {
            continue;
          }

          restoredCustomGames.add(
            CustomGameEntry(
              id: id,
              gameName: gameName,
              playInfo: playInfo,
            ),
          );
        }
      }

      // ==============================
      // 3. Preview 결과 복원
      // ==============================

      final previewResult = _restorePreviewResult(
        decoded,
        fallbackDisplayName: json['displayName']?.toString() ?? '',
      );

      final hasCompetitiveGame =
          decoded['hasCompetitiveGame'] as bool? ?? false;

      final hasRpgGame = decoded['hasRpgGame'] as bool? ?? false;

      final displayName = json['displayName']?.toString() ?? '';

      Uint8List? profileImageBytes;
      final profileImageBase64 = decoded['profileImageBase64'];
      if (profileImageBase64 is String && profileImageBase64.isNotEmpty) {
        try {
          profileImageBytes = base64Decode(profileImageBase64);
        } on FormatException {
          debugPrint('최근 게임 신분증 프로필 사진 복원 실패');
        }
      }

      final identityNumber = json['identityNumber']?.toString() ?? '';

      final issuedDate = json['issuedDate']?.toString() ?? '';

      if (!mounted) {
        return;
      }

      setState(() {
        _latestIdentity = GameIdentityHistory(
          displayName: displayName,
          profileImageBytes: profileImageBytes,
          identityNumber: identityNumber,
          issuedDate: issuedDate,
          selectedGames: restoredGames,
          customGames: restoredCustomGames,
          previewResult: previewResult,
          previewError: null,
          hasCompetitiveGame: hasCompetitiveGame,
          hasRpgGame: hasRpgGame,
        );
      });

      debugPrint(
        '최근 게임 신분증 복원 성공: '
        '$displayName / '
        '${restoredGames.length + restoredCustomGames.length}개 게임',
      );
    } on ApiException catch (error) {
      debugPrint(
        'LATEST IDENTITY LOAD API ERROR: '
        '${error.statusCode} / '
        '${error.message}',
      );
    } catch (error, stackTrace) {
      debugPrint(
        '===== LATEST IDENTITY LOAD ERROR =====',
      );
      debugPrint(
        'error: $error',
      );
      debugPrint(
        'stackTrace: $stackTrace',
      );
    }
  }

  GameIdentityPreviewResult? _restorePreviewResult(
    Map<String, dynamic> snapshot, {
    required String fallbackDisplayName,
  }) {
    final averageTopPercent = snapshot['averageTopPercent'];

    final includedGameCount = snapshot['includedGameCount'];

    final evaluationMessage = snapshot['evaluationMessage'];

    final selectedGamesJson = snapshot['selectedGames'];

    if (selectedGamesJson is! List) {
      return null;
    }

    final gamesJson = <Map<String, dynamic>>[];

    for (final item in selectedGamesJson) {
      if (item is! Map) {
        continue;
      }

      final id = item['id'];

      if (id is! num) {
        continue;
      }

      final gameType = _restoreGameTypeApiValue(
        item['gameType'],
      );

      if (gameType == null) {
        continue;
      }

      gamesJson.add({
        'gameAccountId': id.toInt(),
        'gameType': gameType,
        'accountName': item['accountName']?.toString() ?? '',
        'metricLabel': item['metricLabel'],
        'metricValue': item['metricValue'],
        'topPercent': item['topPercent'],
        'estimated': item['estimated'] ?? false,
        'exclusionReason': item['exclusionReason'],

        // 계산 포함 여부
        'includedInAverage':
            item['includedInAverage'] as bool? ?? item['topPercent'] != null,
      });
    }

    try {
      return GameIdentityPreviewResult.fromJson({
        'displayName':
            snapshot['displayName']?.toString() ?? fallbackDisplayName,
        'averageTopPercent': averageTopPercent,
        'evaluationType': snapshot['evaluationType']?.toString() ?? 'RPG_ONLY',
        'includedGameCount': includedGameCount ?? 0,
        'evaluationMessage': evaluationMessage ?? '',
        'games': gamesJson,
      });
    } catch (error) {
      debugPrint(
        '최근 신분증 Preview 복원 실패: '
        '$error',
      );

      return null;
    }
  }

  String? _restoreGameTypeApiValue(Object? value) {
    final rawValue = value?.toString();

    if (rawValue == null || rawValue.isEmpty) {
      return null;
    }

    for (final gameType in GameType.values) {
      if (rawValue == gameType.apiValue || rawValue == gameType.name) {
        return gameType.apiValue;
      }
    }

    return null;
  }

  Future<void> _createIdentityCard() async {
    await _generateIdentityCardImage();
  }

  Future<void> _shareLatestIdentity() async {
    try {
      final shared =
          await PublicProfileRepository.instance.enableIdentityShare();
      final shareId = shared.shareId;
      if (shareId == null || shareId.isEmpty) {
        throw const FormatException('Missing share id');
      }
      await copyPublicLink('/identity/$shareId');
      if (mounted) _showMessageBubble('공유 링크를 복사했습니다.');
    } catch (_) {
      if (mounted) _showMessageBubble('공유 링크를 만들지 못했습니다.');
    }
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
                  '연결 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
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
          key: _pageTopKey,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (widget.showHeader) ...[
              const _GameIdentityHeader(),
              const SizedBox(height: 24),
            ],
            if (wideLayout)
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 6,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildWizard(),
                        const SizedBox(height: 28),
                        _buildLatestIdentitySection(),
                      ],
                    ),
                  ),
                  const SizedBox(width: 24),
                  Expanded(flex: 4, child: _buildPreview()),
                ],
              )
            else
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildWizard(),
                  if (_showPreview) ...[
                    const SizedBox(height: 24),
                    _buildClosablePreview(),
                  ],
                  const SizedBox(height: 28),
                  _buildLatestIdentityDisclosure(),
                ],
              ),
          ],
        );
      },
    );
  }

  Widget _buildLatestIdentityDisclosure() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton.icon(
          onPressed: () => setState(
            () => _showLatestIdentity = !_showLatestIdentity,
          ),
          style: OutlinedButton.styleFrom(
            foregroundColor:
                isDark ? const Color(0xFFC8BEFF) : const Color(0xFF5946B8),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          ),
          icon: Icon(
            _showLatestIdentity
                ? Icons.keyboard_arrow_up_rounded
                : Icons.history_rounded,
          ),
          label: Text(
            _showLatestIdentity ? '최근 생성한 게임 신분증 숨기기' : '최근 생성한 게임 신분증 보기',
          ),
        ),
        if (_showLatestIdentity) ...[
          const SizedBox(height: 12),
          _buildLatestIdentitySection(),
        ],
      ],
    );
  }

  Widget _buildLatestIdentitySection() {
    final identity = _latestIdentity;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (identity == null) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF081321) : Colors.white,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: isDark ? const Color(0xFF1D2A3D) : const Color(0xFFDDE3EC),
          ),
        ),
        child: Row(
          children: [
            const Icon(
              Icons.badge_outlined,
              color: Color(0xFF67758A),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '최근 생성한 게임 신분증',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '아직 생성한 게임 신분증이 없습니다.',
                  style: TextStyle(
                    color: isDark
                        ? const Color(0xFF78869A)
                        : const Color(0xFF687386),
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ],
        ),
      );
    }

    final percent = identity.previewResult?.averageTopPercent;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF081321) : Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isDark ? const Color(0xFF24324A) : const Color(0xFFDDE3EC),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 4,
            children: [
              const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.history_rounded,
                    color: Color(0xFF9B8BFF),
                    size: 20,
                  ),
                  SizedBox(width: 8),
                  Text(
                    '최근 생성한 게임 신분증',
                    maxLines: 1,
                    softWrap: false,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ],
              ),
              Text(
                identity.issuedDate,
                maxLines: 1,
                softWrap: false,
                style: const TextStyle(
                  color: Color(0xFF748197),
                  fontSize: 10,
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          LayoutBuilder(
            builder: (context, summaryConstraints) {
              final narrowLayout = summaryConstraints.maxWidth < 520;
              final iconSize = narrowLayout ? 54.0 : 64.0;

              final identityInfo = Row(
                children: [
                  Container(
                    width: iconSize,
                    height: iconSize,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [
                          Color(0xFF40398A),
                          Color(0xFF232B5C),
                        ],
                      ),
                      borderRadius: BorderRadius.circular(
                        narrowLayout ? 15 : 17,
                      ),
                    ),
                    child: Icon(
                      Icons.badge_rounded,
                      color: const Color(0xFFC2B8FF),
                      size: narrowLayout ? 27 : 31,
                    ),
                  ),
                  SizedBox(width: narrowLayout ? 12 : 15),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          identity.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          softWrap: false,
                          style: const TextStyle(
                            fontSize: 19,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          '등록 게임 ${identity.gameCount}개',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          softWrap: false,
                          style: const TextStyle(
                            color: Color(0xFF8996A9),
                            fontSize: 11,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          percent == null
                              ? 'RPG / 기타 게임 프로필'
                              : '게임력 상위 ${_formatTopPercent(percent)}%',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          softWrap: false,
                          style: const TextStyle(
                            color: Color(0xFFA99DFF),
                            fontSize: 12,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              );

              final buttons = Row(
                mainAxisSize:
                    narrowLayout ? MainAxisSize.max : MainAxisSize.min,
                children: [
                  if (narrowLayout) const Spacer(),
                  FilledButton.tonalIcon(
                    onPressed: _shareLatestIdentity,
                    style: narrowLayout
                        ? FilledButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                          )
                        : null,
                    icon: const Icon(Icons.share_rounded, size: 14),
                    label: const Text(
                      '공유하기',
                      maxLines: 1,
                      softWrap: false,
                    ),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    onPressed: () {
                      _openLatestIdentity(identity);
                    },
                    style: narrowLayout
                        ? OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                          )
                        : null,
                    icon: const Icon(
                      Icons.open_in_new_rounded,
                      size: 14,
                    ),
                    label: const Text(
                      '다시보기',
                      maxLines: 1,
                      softWrap: false,
                    ),
                  ),
                ],
              );

              if (narrowLayout) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    identityInfo,
                    const SizedBox(height: 14),
                    buttons,
                  ],
                );
              }

              return Row(
                children: [
                  Expanded(child: identityInfo),
                  const SizedBox(width: 16),
                  buttons,
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildWizard() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isMobile = MediaQuery.sizeOf(context).width < 700;
    return Container(
      width: double.infinity,
      padding: EdgeInsets.all(isMobile ? 16 : 24),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF081321) : Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isDark ? const Color(0xFF1D2A3D) : const Color(0xFFDDE3EC),
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

  Widget _buildIdentityPreviewUtilityButton() {
    return _IdentityImageActionButton(
      previewVisible: _showPreview,
      onPressed: () => setState(() => _showPreview = !_showPreview),
    );
  }

  Widget _buildStepActions({
    required VoidCallback? onPrevious,
    required String primaryLabel,
    required VoidCallback? onPrimary,
    required IconData primaryIcon,
    bool showImageAction = true,
  }) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final viewportWidth = MediaQuery.sizeOf(context).width;
        final isMobile = viewportWidth < 700;
        final showPreviewButton = showImageAction && viewportWidth < 1100;
        final previousButton = _IdentitySecondaryActionButton(
          onPressed: onPrevious,
        );
        final primaryButton = _IdentityPrimaryActionButton(
          onPressed: onPrimary,
          label: primaryLabel,
          icon: primaryIcon,
        );

        if (!showPreviewButton) {
          return Row(
            children: [
              previousButton,
              const Spacer(),
              primaryButton,
            ],
          );
        }

        return Row(
          children: [
            previousButton,
            const Spacer(),
            if (showPreviewButton) ...[
              _buildIdentityPreviewUtilityButton(),
              const SizedBox(width: 8),
            ],
            if (isMobile)
              Expanded(
                child: Align(
                  alignment: Alignment.centerRight,
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerRight,
                    child: primaryButton,
                  ),
                ),
              )
            else
              Flexible(
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerRight,
                  child: primaryButton,
                ),
              ),
          ],
        );
      },
    );
  }

  Widget _buildNicknameStep() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Column(
      key: const ValueKey('nickname-step'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: 92,
              height: 92,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  ExcludeSemantics(
                    child: IgnorePointer(
                      child: Container(
                        clipBehavior: Clip.antiAlias,
                        decoration: BoxDecoration(
                          color: isDark
                              ? const Color(0xFF18213C)
                              : const Color(0xFFECE9FF),
                          borderRadius: BorderRadius.circular(18),
                          border: Border.all(
                            color: const Color(0xFF7565E8),
                          ),
                        ),
                        child: _profileImageBytes == null
                            ? const Icon(
                                Icons.add_a_photo_outlined,
                                color: Color(0xFF8B72FF),
                                size: 30,
                              )
                            : Image.memory(
                                _profileImageBytes!,
                                fit: BoxFit.cover,
                                gaplessPlayback: true,
                              ),
                      ),
                    ),
                  ),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(18),
                    child: ProfileImageInputOverlay(
                      onImageSelected: _processSelectedProfileImage,
                      onError: _handleProfileImagePickerError,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '신분증 사진 (선택)',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 5),
                  const Text(
                    '사진을 선택한 뒤 정사각형에 맞게 확대하고 이동할 수 있어요.',
                    style: TextStyle(
                      color: Color(0xFF8290A4),
                      fontSize: 12,
                      height: 1.45,
                    ),
                  ),
                  if (_profileImageBytes != null) ...[
                    const SizedBox(height: 7),
                    InkWell(
                      onTap: () => setState(() => _profileImageBytes = null),
                      borderRadius: BorderRadius.circular(8),
                      child: const Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: 2,
                          vertical: 3,
                        ),
                        child: Text(
                          '삭제',
                          style: TextStyle(
                            color: Color(0xFFE78996),
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TextField(
          controller: _displayNameController,
          maxLength: 12,
          decoration: InputDecoration(
            labelText: '표시할 닉네임',
            hintText: '입력하지 않으면 The Gamer로 표시됩니다.',
            filled: true,
            fillColor:
                isDark ? const Color(0xFF0E1A2A) : const Color(0xFFF7F8FB),
            border: const OutlineInputBorder(),
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
          child: _IdentityPrimaryActionButton(
            onPressed: () => _moveToStep(1),
            label: '다음',
            icon: Icons.arrow_forward_rounded,
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
          LayoutBuilder(
            builder: (context, constraints) {
              const cardWidth = 250.0;
              const spacing = 12.0;
              final singleColumn =
                  constraints.maxWidth < cardWidth * 2 + spacing;
              final mobileLayout = MediaQuery.sizeOf(context).width < 1100;
              final centerCardRows = mobileLayout || singleColumn;

              return SizedBox(
                width: double.infinity,
                child: Wrap(
                  alignment: centerCardRows
                      ? WrapAlignment.center
                      : WrapAlignment.start,
                  spacing: spacing,
                  runSpacing: spacing,
                  children: widget.games.map((game) {
                    final selected = _selectedGameIds.contains(game.id);

                    return SizedBox(
                      width: cardWidth,
                      child: _SelectableGameAccountCard(
                        game: game,
                        selected: selected,
                        onTap: () => _toggleGame(game),
                      ),
                    );
                  }).toList(),
                ),
              );
            },
          ),
        const SizedBox(height: 24),
        _buildStepActions(
          onPrevious: () => _moveToStep(0),
          primaryLabel: _hasSelectedGames ? '다음' : '기타 게임 추가',
          onPrimary: () => _moveToStep(2),
          primaryIcon: _hasSelectedGames
              ? Icons.arrow_forward_rounded
              : Icons.add_rounded,
        ),
      ],
    );
  }

  Widget _buildNewGameStep() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final showInlineLoading =
        _isAddingGame && MediaQuery.sizeOf(context).width >= 600;
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
              color: isDark ? const Color(0xFF0E1A2A) : const Color(0xFFF7F8FB),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: const Color(0xFF5746A8),
              ),
            ),
            child: showInlineLoading
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
            color: isDark ? const Color(0xFF0E1A2A) : const Color(0xFFF7F8FB),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: isDark ? const Color(0xFF2B3A50) : const Color(0xFFD8DEE8),
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
                decoration: InputDecoration(
                  labelText: '게임명',
                  hintText: '예: Minecraft',
                  filled: true,
                  fillColor: isDark ? const Color(0xFF101D2D) : Colors.white,
                  border: const OutlineInputBorder(),
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
                      color: isDark ? const Color(0xFF111F30) : Colors.white,
                      borderRadius: BorderRadius.circular(13),
                      border: Border.all(
                        color: isDark
                            ? const Color(0xFF2B3A50)
                            : const Color(0xFFD8DEE8),
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
                decoration: InputDecoration(
                  labelText: '플레이 시간 및 티어',
                  hintText: '예: 1,240시간 · 다이아몬드',
                  filled: true,
                  fillColor: isDark ? const Color(0xFF101D2D) : Colors.white,
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerRight,
                child: _IdentityAccentActionButton(
                  onPressed: _addCustomGame,
                  label: '기타 게임 추가',
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
              color: isDark ? const Color(0xFF101A2B) : const Color(0xFFF1FAF4),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color:
                    isDark ? const Color(0xFF27364D) : const Color(0xFFCFE7D7),
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
                    style: TextStyle(
                      color: isDark
                          ? const Color(0xFFAEB9C8)
                          : const Color(0xFF4F6758),
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
          ),
        const SizedBox(height: 24),
        _buildStepActions(
          onPrevious: _isAddingGame ? null : () => _moveToStep(1),
          primaryLabel: '최종 확인',
          onPrimary: _isAddingGame ? null : () => _moveToStep(3),
          primaryIcon: Icons.arrow_forward_rounded,
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
          trailing: IconButton(
            onPressed: _editDisplayNameFromFinalStep,
            tooltip: '신분증 닉네임 수정',
            style: IconButton.styleFrom(
              backgroundColor: const Color(0x261F64FF),
              foregroundColor: const Color(0xFFB6A8FF),
              side: const BorderSide(color: Color(0x554F46A4)),
              minimumSize: const Size(40, 40),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(11),
              ),
            ),
            icon: const Icon(Icons.edit_rounded, size: 18),
          ),
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
            _IdentitySecondaryActionButton(
              onPressed: () => _moveToStep(2),
            ),
            const Spacer(),
            _IdentityGenerateButton(
              onPressed: _isGeneratingImage ? null : _createIdentityCard,
              isLoading: _isGeneratingImage,
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
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 430),
          child: _GameIdentityPreview(
            displayName: _previewDisplayName,
            profileImageBytes: _profileImageBytes,
            identityNumber: _identityNumber,
            issuedDate: _issuedDateText,
            selectedGames: _selectedGames,
            customGames: _customGames,
            hasCompetitiveGame: _hasCompetitiveGame,
            hasRpgGame: _hasRpgGame,
            previewResult: _previewResult,
            isLoadingPreview: _isLoadingPreview,
            previewError: _previewError,
            showDetailActions: !_isGeneratingImage,
          ),
        ),
      ),
    );
  }

  Widget _buildClosablePreview() {
    return Stack(
      children: [
        _buildPreview(),
        Positioned(
          left: 8,
          top: 8,
          child: Material(
            color: const Color(0xFF111B2B).withValues(alpha: 0.92),
            shape: const CircleBorder(
              side: BorderSide(color: Color(0xFF40506A)),
            ),
            child: IconButton(
              onPressed: () => setState(() => _showPreview = false),
              tooltip: '미리보기 닫기',
              color: const Color(0xFFF2EFFF),
              icon: const Icon(Icons.close_rounded),
            ),
          ),
        ),
      ],
    );
  }
}

class _IdentityNicknameEditDialog extends StatefulWidget {
  const _IdentityNicknameEditDialog({required this.initialValue});

  final String initialValue;

  @override
  State<_IdentityNicknameEditDialog> createState() =>
      _IdentityNicknameEditDialogState();
}

class _IdentityNicknameEditDialogState
    extends State<_IdentityNicknameEditDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialValue);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    Navigator.of(context).pop(_controller.text);
  }

  @override
  Widget build(BuildContext context) {
    final viewInsets = MediaQuery.viewInsetsOf(context);

    return SafeArea(
      child: AnimatedPadding(
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.only(bottom: viewInsets.bottom),
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
            child: Dialog(
              insetPadding: EdgeInsets.zero,
              backgroundColor: Colors.transparent,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [Color(0xFF151A35), Color(0xFF10172B)],
                    ),
                    borderRadius: BorderRadius.circular(23),
                    border: Border.all(
                      color: const Color(0xFF6251C7),
                      width: 1.2,
                    ),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x66000000),
                        blurRadius: 28,
                        offset: Offset(0, 10),
                      ),
                      BoxShadow(
                        color: Color(0x2E6848D8),
                        blurRadius: 24,
                      ),
                    ],
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Expanded(
                            child: Text(
                              '신분증 닉네임 수정',
                              style: TextStyle(
                                color: Color(0xFFF5F3FF),
                                fontSize: 23,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          IconButton(
                            onPressed: () => Navigator.of(context).pop(),
                            tooltip: '닫기',
                            style: IconButton.styleFrom(
                              backgroundColor: const Color(0xFF1A2339),
                              foregroundColor: const Color(0xFFD6D0EA),
                              side: const BorderSide(
                                color: Color(0xFF3B4660),
                              ),
                              minimumSize: const Size(42, 42),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            icon: const Icon(Icons.close_rounded, size: 21),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        '게임 신분증에 표시될 닉네임을 입력해주세요.',
                        style: TextStyle(
                          color: Color(0xFFA1A9BD),
                          fontSize: 14,
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 24),
                      const Text(
                        '신분증 닉네임',
                        style: TextStyle(
                          color: Color(0xFFE6E2F3),
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 9),
                      TextField(
                        controller: _controller,
                        autofocus: true,
                        maxLength: 12,
                        textInputAction: TextInputAction.done,
                        onChanged: (_) => setState(() {}),
                        onSubmitted: (_) => _submit(),
                        style: const TextStyle(
                          color: Color(0xFFF5F3FF),
                          fontSize: 17,
                          fontWeight: FontWeight.w600,
                        ),
                        decoration: InputDecoration(
                          counterText: '',
                          filled: true,
                          fillColor: const Color(0xFF10182B),
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 17,
                            vertical: 17,
                          ),
                          suffixIcon: Padding(
                            padding: const EdgeInsets.only(right: 14),
                            child: Center(
                              widthFactor: 1,
                              child: Text(
                                '${_controller.text.characters.length}/12',
                                style: const TextStyle(
                                  color: Color(0xFF918CA7),
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(13),
                            borderSide: const BorderSide(
                              color: Color(0xFF38445F),
                            ),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(13),
                            borderSide: const BorderSide(
                              color: Color(0xFF8068FF),
                              width: 1.5,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        '최대 12자까지 입력할 수 있습니다. 비워두면 The Gamer로 표시됩니다.',
                        style: TextStyle(
                          color: Color(0xFF8F99AD),
                          fontSize: 12,
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Row(
                        children: [
                          Expanded(
                            child: SizedBox(
                              height: 52,
                              child: OutlinedButton(
                                onPressed: () => Navigator.of(context).pop(),
                                style: OutlinedButton.styleFrom(
                                  foregroundColor: const Color(0xFFC9BFFF),
                                  side: const BorderSide(
                                    color: Color(0xFF77758A),
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(15),
                                  ),
                                ),
                                child: const Text(
                                  '취소',
                                  style: TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: SizedBox(
                              height: 52,
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  gradient: const LinearGradient(
                                    colors: [
                                      Color(0xFF6848D8),
                                      Color(0xFF8A6BFF),
                                    ],
                                  ),
                                  borderRadius: BorderRadius.circular(15),
                                  boxShadow: const [
                                    BoxShadow(
                                      color: Color(0x337B61FF),
                                      blurRadius: 13,
                                      offset: Offset(0, 4),
                                    ),
                                  ],
                                ),
                                child: Material(
                                  color: Colors.transparent,
                                  child: InkWell(
                                    onTap: _submit,
                                    borderRadius: BorderRadius.circular(15),
                                    child: const Center(
                                      child: Text(
                                        '수정',
                                        style: TextStyle(
                                          color: Colors.white,
                                          fontSize: 15,
                                          fontWeight: FontWeight.w800,
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _GeneratedPreviewActionButton extends StatelessWidget {
  const _GeneratedPreviewActionButton({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: const Color(0xF2111B2B),
      shape: const CircleBorder(
        side: BorderSide(color: Color(0xFF53637D)),
      ),
      elevation: 8,
      child: IconButton(
        onPressed: onPressed,
        tooltip: tooltip,
        color: const Color(0xFFF2EFFF),
        icon: Icon(icon),
      ),
    );
  }
}

class _IdentitySecondaryActionButton extends StatelessWidget {
  const _IdentitySecondaryActionButton({required this.onPressed});

  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: OutlinedButton.icon(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: const Color(0xFFC9BFFF),
          side: const BorderSide(color: Color(0xFF77758A)),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(15),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16),
        ),
        icon: const Icon(Icons.arrow_back_rounded, size: 18),
        label: const Text(
          '이전',
          maxLines: 1,
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class _IdentityPrimaryActionButton extends StatelessWidget {
  const _IdentityPrimaryActionButton({
    required this.onPressed,
    required this.label,
    required this.icon,
  });

  final VoidCallback? onPressed;
  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    return AnimatedOpacity(
      duration: const Duration(milliseconds: 150),
      opacity: enabled ? 1 : 0.55,
      child: Container(
        height: 48,
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF6848D8), Color(0xFF8A6BFF)],
          ),
          borderRadius: BorderRadius.circular(15),
          boxShadow: const [
            BoxShadow(
              color: Color(0x337B61FF),
              blurRadius: 13,
              offset: Offset(0, 4),
            ),
          ],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onPressed,
            borderRadius: BorderRadius.circular(15),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 17),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    label,
                    maxLines: 1,
                    softWrap: false,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Icon(icon, color: Colors.white, size: 18),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _IdentityAccentActionButton extends StatelessWidget {
  const _IdentityAccentActionButton({
    required this.onPressed,
    required this.label,
  });

  final VoidCallback? onPressed;
  final String label;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: FilledButton.icon(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          backgroundColor: const Color(0xFF4E3F99),
          foregroundColor: const Color(0xFFF3F0FF),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(15),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16),
        ),
        icon: const Icon(Icons.add_rounded, size: 18),
        label: Text(
          label,
          maxLines: 1,
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class _IdentityImageActionButton extends StatelessWidget {
  const _IdentityImageActionButton({
    required this.previewVisible,
    required this.onPressed,
  });

  final bool previewVisible;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: previewVisible ? '현재 게임 신분증 미리보기 닫기' : '현재 게임 신분증 미리보기',
      child: Material(
        color: const Color(0xFF0E172A),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(13),
          side: BorderSide(
            color: previewVisible
                ? const Color(0xFF8068FF)
                : const Color(0xFF77758A),
          ),
        ),
        child: InkWell(
          onTap: onPressed,
          borderRadius: BorderRadius.circular(13),
          child: SizedBox.square(
            dimension: 50,
            child: Icon(
              previewVisible ? Icons.image_rounded : Icons.image_outlined,
              color: const Color(0xFFB6A8FF),
              size: 21,
            ),
          ),
        ),
      ),
    );
  }
}

class _IdentityGenerateButton extends StatelessWidget {
  const _IdentityGenerateButton({
    required this.onPressed,
    required this.isLoading,
  });

  final VoidCallback? onPressed;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    const radius = 15.0;
    return AnimatedOpacity(
      duration: const Duration(milliseconds: 160),
      opacity: isLoading ? 0.76 : 1,
      child: Container(
        height: 50,
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF6848D8), Color(0xFF8A6BFF)],
          ),
          borderRadius: BorderRadius.circular(radius),
          boxShadow: const [
            BoxShadow(
              color: Color(0x387B5CEE),
              blurRadius: 16,
              offset: Offset(0, 5),
            ),
          ],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onPressed,
            borderRadius: BorderRadius.circular(radius),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox.square(
                    dimension: 18,
                    child: isLoading
                        ? const CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          )
                        : const Icon(
                            Icons.download_rounded,
                            size: 18,
                            color: Colors.white,
                          ),
                  ),
                  const SizedBox(width: 9),
                  Text(
                    isLoading ? '이미지 생성 중' : '게임 신분증 생성',
                    maxLines: 1,
                    softWrap: false,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
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
                    ? (isDark
                        ? const Color(0xFF362A74)
                        : const Color(0xFFE9E5FF))
                    : (isDark
                        ? const Color(0xFF0E1A2A)
                        : const Color(0xFFF7F8FB)),
                borderRadius: BorderRadius.circular(30),
                border: Border.all(
                  color: selected
                      ? const Color(0xFF826DFF)
                      : (isDark
                          ? const Color(0xFF24344A)
                          : const Color(0xFFD8DEE8)),
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
                      color: selected
                          ? (isDark ? Colors.white : const Color(0xFF403493))
                          : (isDark
                              ? const Color(0xFFA5B0C0)
                              : const Color(0xFF596579)),
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Material(
      color: selected
          ? (isDark ? const Color(0xFF241D4B) : const Color(0xFFF0EDFF))
          : (isDark ? const Color(0xFF0E1A2A) : const Color(0xFFF7F8FB)),
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
              color: selected
                  ? const Color(0xFF8069FF)
                  : (isDark
                      ? const Color(0xFF24344A)
                      : const Color(0xFFD8DEE8)),
              width: selected ? 2 : 1,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: isDark
                      ? const Color(0xFF172438)
                      : const Color(0xFFECE9FF),
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
                      style: TextStyle(
                        color: isDark
                            ? const Color(0xFFA5B0C0)
                            : const Color(0xFF687386),
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
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: 20,
        vertical: 32,
      ),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0E1A2A) : const Color(0xFFF7F8FB),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? const Color(0xFF24344A) : const Color(0xFFD8DEE8),
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
    required this.profileImageBytes,
    required this.identityNumber,
    required this.issuedDate,
    required this.selectedGames,
    required this.hasCompetitiveGame,
    required this.hasRpgGame,
    required this.customGames,
    required this.previewResult,
    required this.isLoadingPreview,
    required this.previewError,
    required this.showDetailActions,
  });

  final String displayName;
  final Uint8List? profileImageBytes;
  final String identityNumber;
  final String issuedDate;

  final List<GameProfile> selectedGames;
  final List<CustomGameEntry> customGames;

  final bool hasCompetitiveGame;
  final bool hasRpgGame;

  final GameIdentityPreviewResult? previewResult;
  final bool isLoadingPreview;
  final String? previewError;
  final bool showDetailActions;

  double get _displayNameFontSize {
    final characterCount = displayName.runes.length;

    if (characterCount <= 6) return 24;
    if (characterCount <= 8) return 21;
    if (characterCount <= 10) return 18;
    if (characterCount == 11) return 16;
    return 15;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(
        minHeight: 690,
      ),
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: const Color(0xFF6655D8),
          width: 1.2,
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
            color: Color(0x44000000),
            blurRadius: 30,
            offset: Offset(0, 14),
          ),
          BoxShadow(
            color: Color(0x222F28A0),
            blurRadius: 22,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildCardHeader(),
          const SizedBox(height: 18),
          _buildGamePowerArea(),
          const SizedBox(height: 24),
          _buildGameInformation(),
          const SizedBox(height: 18),
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

  // TODO: Remove with the legacy preview helpers after visual QA.
  // ignore: unused_element
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
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 150),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xFF182344),
            Color(0xFF101B32),
          ],
        ),
        border: Border.all(
          color: const Color(0xFF3B4770),
        ),
      ),
      child: Stack(
        children: [
          const Positioned(
            right: -18,
            bottom: -28,
            child: Icon(
              Icons.sports_esports_rounded,
              size: 142,
              color: Color(0x121A0D68),
            ),
          ),
          Row(
            children: [
              Container(
                width: 82,
                height: 82,
                clipBehavior: Clip.antiAlias,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [Color(0xFF4E459E), Color(0xFF252B64)],
                  ),
                  border: Border.all(
                    color: const Color(0xFF5D55A8),
                    width: 1.5,
                  ),
                ),
                child: profileImageBytes == null
                    ? const Icon(
                        Icons.person_rounded,
                        size: 43,
                        color: Color(0xFFC5BCFF),
                      )
                    : Image.memory(
                        profileImageBytes!,
                        fit: BoxFit.cover,
                        gaplessPlayback: true,
                      ),
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'MY GAME HUB',
                      style: TextStyle(
                        color: Color(0xFFA99EFF),
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1.1,
                      ),
                    ),
                    const SizedBox(height: 7),
                    Text(
                      displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: const Color(0xFFF5F3FF),
                        fontSize: _displayNameFontSize,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'PLAYER ID',
                      style: TextStyle(
                        color: Color(0xFF8795AA),
                        fontSize: 9,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      identityNumber,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Color(0xFFAEB8CB),
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // TODO: Remove after the redesigned shared preview has completed visual QA.
  // ignore: unused_element
  Widget _buildOwnerInformation() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xAA18213D),
            Color(0xAA1D173A),
          ],
        ),
        border: Border.all(
          color: const Color(0xFF394564),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x22000000),
            blurRadius: 14,
            offset: Offset(0, 6),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 62,
            height: 62,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Color(0xFF40398A),
                  Color(0xFF232B5C),
                ],
              ),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: const Color(0xFF5D55A8),
              ),
            ),
            child: profileImageBytes == null
                ? const Icon(
                    Icons.person_rounded,
                    size: 33,
                    color: Color(0xFFC5BCFF),
                  )
                : Image.memory(
                    profileImageBytes!,
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                  ),
          ),
          const SizedBox(width: 15),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Text(
                      'GAMER NAME',
                      style: TextStyle(
                        color: Color(0xFF858FA5),
                        fontSize: 8,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.1,
                      ),
                    ),
                    const SizedBox(width: 7),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 7,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0x332F64FF),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Text(
                        'PLAYER',
                        style: TextStyle(
                          color: Color(0xFFAAA0FF),
                          fontSize: 7,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 0.7,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                SizedBox(
                  height: 30,
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerLeft,
                      child: Text(
                        displayName,
                        maxLines: 1,
                        softWrap: false,
                        style: TextStyle(
                          color: const Color(0xFFF0EEFF),
                          fontSize: _displayNameFontSize,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'MY GAME HUB GAMER',
                  style: TextStyle(
                    color: Color(0xFF777F96),
                    fontSize: 8,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: 11,
              vertical: 9,
            ),
            decoration: BoxDecoration(
              color: const Color(0x55121A30),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: const Color(0xFF34405D),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.calendar_today_rounded,
                      size: 10,
                      color: Color(0xFF8F82E4),
                    ),
                    SizedBox(width: 4),
                    Text(
                      'ISSUED',
                      style: TextStyle(
                        color: Color(0xFF858FA5),
                        fontSize: 7,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  issuedDate,
                  style: const TextStyle(
                    color: Color(0xFFD0D4E3),
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
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

    const visibleGameLimit = 4;
    final visibleSelectedGames = selectedGames.take(visibleGameLimit).toList();
    final remainingSlots = visibleGameLimit - visibleSelectedGames.length;
    final visibleCustomGames = customGames.take(remainingSlots).toList();
    final hiddenGameCount = selectedGames.length +
        customGames.length -
        visibleSelectedGames.length -
        visibleCustomGames.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(
              Icons.sports_esports_rounded,
              size: 19,
              color: Color(0xFFA99EFF),
            ),
            const SizedBox(width: 8),
            const Expanded(
              child: Text(
                '내 게임 목록',
                style: TextStyle(
                  color: Color(0xFFF0EDFF),
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
              decoration: BoxDecoration(
                color: const Color(0xFF28205B),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(
                '총 ${selectedGames.length + customGames.length}개',
                style: const TextStyle(
                  color: Color(0xFFA99EFF),
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        ...visibleSelectedGames.map(
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
              showDetailAction: showDetailActions,
            );
          },
        ),
        if (visibleCustomGames.isNotEmpty) ...[
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(
                Icons.extension_rounded,
                size: 14,
                color: Color(0xFF8B7BDF),
              ),
              const SizedBox(width: 6),
              const Text(
                'OTHER GAMES',
                style: TextStyle(
                  color: Color(0xFF8E96B8),
                  fontSize: 8,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1.1,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 7,
            runSpacing: 7,
            children: visibleCustomGames
                .map(
                  (game) => Container(
                    constraints: const BoxConstraints(
                      maxWidth: 180,
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 7,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0x66141C31),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: const Color(0xFF313A56),
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.videogame_asset_rounded,
                          size: 12,
                          color: Color(0xFF8F80E8),
                        ),
                        const SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            game.gameName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Color(0xFFD6D9E6),
                              fontSize: 9,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            game.playInfo,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Color(0xFF7E8A9F),
                              fontSize: 8,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                )
                .toList(),
          ),
        ],
        if (hiddenGameCount > 0)
          Padding(
            padding: const EdgeInsets.only(left: 4, top: 8),
            child: Text(
              '외 $hiddenGameCount개 게임',
              style: const TextStyle(
                color: Color(0xFF8290A4),
                fontSize: 10,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildGamePowerArea() {
    final percent = previewResult?.averageTopPercent;
    final totalGameCount = selectedGames.length + customGames.length;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: const LinearGradient(
          colors: [Color(0xFF111D34), Color(0xFF121B33)],
        ),
        border: Border.all(
          color: const Color(0xFF30405F),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: _IdentitySummaryItem(
              icon: Icons.sports_esports_rounded,
              label: '등록한 게임',
              value: '$totalGameCount개',
            ),
          ),
          Container(
            width: 1,
            height: 58,
            color: const Color(0xFF334360),
          ),
          Expanded(
            child: _IdentitySummaryItem(
              icon: percent == null
                  ? Icons.auto_awesome_rounded
                  : Icons.emoji_events_rounded,
              label: '평균 게임력',
              value: percent == null ? '-' : _gamePowerText,
              highlight: percent != null,
            ),
          ),
        ],
      ),
    );
  }

  // TODO: Remove after the redesigned shared preview has completed visual QA.
  // ignore: unused_element
  Widget _buildAdventurerProfile() {
    final rpgCount = selectedGames
        .where(
          (game) =>
              game.type == GameType.lostArk ||
              game.type == GameType.mapleStory ||
              game.type == GameType.dungeonFighter,
        )
        .length;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xCC18233D),
            Color(0xCC18203A),
          ],
        ),
        border: Border.all(
          color: const Color(0xFF405779),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x221C4A70),
            blurRadius: 16,
            offset: Offset(0, 6),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 68,
            height: 68,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Color(0xFF365A7C),
                  Color(0xFF25345E),
                ],
              ),
              border: Border.all(
                color: const Color(0xFF55769B),
                width: 2,
              ),
            ),
            child: const Icon(
              Icons.auto_awesome_rounded,
              size: 31,
              color: Color(0xFFB6D7FF),
            ),
          ),
          const SizedBox(width: 17),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'ADVENTURER PROFILE',
                  style: TextStyle(
                    color: Color(0xFF86A9CC),
                    fontSize: 9,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1.3,
                  ),
                ),
                const SizedBox(height: 5),
                const Text(
                  'RPG ADVENTURER',
                  style: TextStyle(
                    color: Color(0xFFE5F1FF),
                    fontSize: 19,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  '모험 기록 $rpgCount개',
                  style: const TextStyle(
                    color: Color(0xFF8799AD),
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: 10,
              vertical: 9,
            ),
            decoration: BoxDecoration(
              color: const Color(0x55162131),
              borderRadius: BorderRadius.circular(11),
              border: Border.all(
                color: const Color(0xFF374B64),
              ),
            ),
            child: const Column(
              children: [
                Icon(
                  Icons.shield_rounded,
                  color: Color(0xFFAEC9E8),
                  size: 20,
                ),
                SizedBox(height: 3),
                Text(
                  'RPG',
                  style: TextStyle(
                    color: Color(0xFF9EB3C9),
                    fontSize: 8,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
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
        minHeight: 96,
      ),
      padding: const EdgeInsets.symmetric(
        horizontal: 18,
        vertical: 15,
      ),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: const LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [
            Color(0xFF28205B),
            Color(0xFF152442),
          ],
        ),
        border: Border.all(
          color: const Color(0xFF554A91),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x222F2070),
            blurRadius: 14,
            offset: Offset(0, 5),
          ),
        ],
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
          : Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: const Color(0xFF302B69),
                    borderRadius: BorderRadius.circular(13),
                  ),
                  child: const Icon(
                    Icons.emoji_events_rounded,
                    color: Color(0xFFC0B4FF),
                    size: 23,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    _previewMessage(),
                    style: const TextStyle(
                      color: Color(0xFFE7E3FF),
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      height: 1.6,
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

class _IdentitySummaryItem extends StatelessWidget {
  const _IdentitySummaryItem({
    required this.icon,
    required this.label,
    required this.value,
    this.highlight = false,
  });

  final IconData icon;
  final String label;
  final String value;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10),
      child: Row(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: const Color(0xFF272553),
              borderRadius: BorderRadius.circular(13),
            ),
            child: Icon(icon, color: const Color(0xFFB79CFF), size: 25),
          ),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF94A3B8),
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 5),
                FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    value,
                    maxLines: 1,
                    style: TextStyle(
                      color: highlight
                          ? const Color(0xFFB79CFF)
                          : const Color(0xFFF5F3FF),
                      fontSize: 17,
                      fontWeight: FontWeight.w900,
                    ),
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

// TODO: Remove after the redesigned shared preview has completed visual QA.
// ignore: unused_element
class _GamePowerRing extends StatelessWidget {
  const _GamePowerRing({
    required this.percent,
  });

  final double percent;

  @override
  Widget build(BuildContext context) {
    final progress = (1.0 - (percent / 100.0)).clamp(0.0, 1.0);

    final text = _formatGamePowerPercent(percent);

    return SizedBox(
      width: 78,
      height: 78,
      child: Stack(
        alignment: Alignment.center,
        children: [
          SizedBox(
            width: 72,
            height: 72,
            child: CircularProgressIndicator(
              value: progress,
              strokeWidth: 7,
              backgroundColor: const Color(0xFF252B4E),
              valueColor: const AlwaysStoppedAnimation(
                Color(0xFF8C7CFF),
              ),
              strokeCap: StrokeCap.round,
            ),
          ),
          Text(
            '$text%',
            style: const TextStyle(
              color: Color(0xFFE9E4FF),
              fontSize: 14,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

String _formatGamePowerPercent(
  double value,
) {
  if (value == value.roundToDouble()) {
    return value.toStringAsFixed(0);
  }

  return value.toStringAsFixed(1);
}

class _PreviewGameRow extends StatelessWidget {
  const _PreviewGameRow({
    required this.game,
    required this.calculatedEntry,
    required this.showDetailAction,
  });

  final GameProfile game;
  final GameIdentityPreviewEntry? calculatedEntry;
  final bool showDetailAction;

  @override
  Widget build(BuildContext context) {
    final rawMetricValue =
        calculatedEntry?.metricValue ?? _fallbackMetricValue();
    final rawMetricLabel =
        calculatedEntry?.metricLabel ?? _fallbackMetricLabel();

    final metricLabel = _identityMetricLabel(rawMetricLabel);
    final metricValue = _identityMetricValue(rawMetricValue);
    final detailUrl = showDetailAction ? _detailUrl(game) : null;

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(
          horizontal: 14,
          vertical: 13,
        ),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.centerLeft,
            end: Alignment.centerRight,
            colors: [
              Color(0xFF101C32),
              Color(0xFF101A30),
            ],
          ),
          borderRadius: BorderRadius.circular(17),
          border: Border.all(
            color: const Color(0xFF2B3C5A),
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 54,
              height: 54,
              padding: const EdgeInsets.all(9),
              decoration: BoxDecoration(
                color: const Color(0xFF17253A),
                borderRadius: BorderRadius.circular(15),
                border: Border.all(
                  color: const Color(0xFF394663),
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
                    size: 24,
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
                      color: Color(0xFF8997AD),
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.3,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    game.accountName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Color(0xFFE4E7F2),
                      fontSize: 14,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFF252458),
                      borderRadius: BorderRadius.circular(9),
                    ),
                    child: Text(
                      metricLabel,
                      style: const TextStyle(
                        color: Color(0xFFB8AEFF),
                        fontSize: 9,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 120),
              child: Text(
                metricValue,
                textAlign: TextAlign.right,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Color(0xFFE6E1FF),
                  fontSize: 14,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
            if (detailUrl != null) ...[
              const SizedBox(width: 5),
              GestureDetector(
                onTap: () => _openGameDetails(game),
                behavior: HitTestBehavior.opaque,
                child: const Padding(
                  padding: EdgeInsets.all(3),
                  child: Icon(
                    Icons.chevron_right_rounded,
                    size: 22,
                    color: Color(0xFFD8D0FF),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _openGameDetails(
    GameProfile game,
  ) async {
    final url = _detailUrl(game);

    if (url == null) {
      return;
    }

    final uri = Uri.parse(url);

    await launchUrl(
      uri,
      mode: LaunchMode.externalApplication,
      webOnlyWindowName: '_blank',
    );
  }

  String? _detailUrl(GameProfile game) {
    switch (game.type) {
      case GameType.lostArk:
        final name = Uri.encodeComponent(game.accountName.trim());

        return 'https://kloa.gg/characters/$name';

      case GameType.eternalReturn:
        final name = Uri.encodeComponent(game.accountName.trim());

        return 'https://dak.gg/er/players/$name?hl=ko';

      case GameType.mapleStory:
        final name = Uri.encodeComponent(game.accountName.trim());

        return 'https://maple.gg/u/$name';

      case GameType.leagueOfLegends:
        final riotId = _parseRiotId(
          game.accountName,
        );

        if (riotId == null) return null;

        final gameName = Uri.encodeComponent(riotId.$1);

        final tagLine = Uri.encodeComponent(riotId.$2);

        return 'https://op.gg/lol/summoners/kr/'
            '$gameName-$tagLine';

      case GameType.tft:
        final riotId = _parseRiotId(
          game.accountName,
        );

        if (riotId == null) return null;

        final gameName = Uri.encodeComponent(riotId.$1);

        final tagLine = Uri.encodeComponent(riotId.$2);

        return 'https://lolchess.gg/profile/kr/'
            '$gameName-$tagLine';

      case GameType.valorant:
        // 대시보드에서 사용 중인
        // Valorant 전적 사이트 URL로 연결
        return null;

      case GameType.battlegrounds:
        // 대시보드에서 사용 중인
        // PUBG 전적 사이트 URL로 연결
        return null;

      case GameType.dungeonFighter:
        // 대시보드에서 사용하는
        // 던파 검색 주소가 있다면 연결
        return null;
    }
  }

  (String, String)? _parseRiotId(
    String accountName,
  ) {
    final parts = accountName.split('#');

    if (parts.length != 2) {
      return null;
    }

    final gameName = parts[0].trim();
    final tagLine = parts[1].trim();

    if (gameName.isEmpty || tagLine.isEmpty) {
      return null;
    }

    return (
      gameName,
      tagLine,
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

  // TODO: Remove with the legacy percentile badge after visual QA.
  // ignore: unused_element
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
    this.trailing,
  });

  final String label;
  final String value;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                color:
                    isDark ? const Color(0xFF8290A4) : const Color(0xFF687386),
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
          if (trailing != null) ...[
            const SizedBox(width: 4),
            trailing!,
          ],
        ],
      ),
    );
  }
}

class _SquarePhotoCropDialog extends StatefulWidget {
  const _SquarePhotoCropDialog({required this.imageBytes});

  final Uint8List imageBytes;

  @override
  State<_SquarePhotoCropDialog> createState() => _SquarePhotoCropDialogState();
}

class _SquarePhotoCropDialogState extends State<_SquarePhotoCropDialog> {
  final CropController _cropController = CropController();
  bool _isCropping = false;
  String? _errorMessage;

  Future<void> _crop() async {
    if (_isCropping) return;

    setState(() {
      _isCropping = true;
      _errorMessage = null;
    });

    await WidgetsBinding.instance.endOfFrame;
    await Future<void>.delayed(const Duration(milliseconds: 16));

    if (!mounted) return;
    _cropController.crop();
  }

  Future<void> _onCropped(CropResult result) async {
    if (!mounted) return;

    switch (result) {
      case CropSuccess(:final croppedImage):
        try {
          final normalizedImage = await compute(
            _normalizeIdentityProfileImage,
            croppedImage,
          );

          if (mounted) {
            Navigator.of(context).pop(normalizedImage);
          }
        } catch (error, stackTrace) {
          debugPrint('PROFILE IMAGE RESIZE ERROR: $error');
          debugPrint('$stackTrace');

          if (mounted) {
            setState(() {
              _isCropping = false;
              _errorMessage = '사진을 적용하지 못했습니다. 다시 시도해주세요.';
            });
          }
        }
      case CropFailure(:final cause):
        debugPrint('PROFILE IMAGE CROP ERROR: $cause');
        setState(() {
          _isCropping = false;
          _errorMessage = '사진을 자르지 못했습니다. 다시 시도해주세요.';
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Dialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 440),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '신분증 사진 맞추기',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 6),
              const Text(
                '정사각형 안에서 사진을 움직이거나 확대해 위치를 맞춰주세요.',
                style: TextStyle(
                  color: Color(0xFF8290A4),
                  fontSize: 12,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 16),
              AspectRatio(
                aspectRatio: 1,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: Crop(
                    image: widget.imageBytes,
                    controller: _cropController,
                    onCropped: (result) {
                      _onCropped(result);
                    },
                    aspectRatio: 1,
                    initialRectBuilder: InitialRectBuilder.withSizeAndRatio(
                      size: 0.9,
                      aspectRatio: 1,
                    ),
                    interactive: true,
                    fixCropRect: true,
                    baseColor: isDark
                        ? const Color(0xFF050B14)
                        : const Color(0xFFE7EAF0),
                    maskColor: Colors.black.withValues(alpha: 0.58),
                    cornerDotBuilder: (size, edgeAlignment) =>
                        const DotControl(color: Color(0xFF8B72FF)),
                    progressIndicator: const Center(
                      child: CircularProgressIndicator(),
                    ),
                  ),
                ),
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 10),
                Text(
                  _errorMessage!,
                  style: const TextStyle(
                    color: Color(0xFFE86C7A),
                    fontSize: 12,
                  ),
                ),
              ],
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed:
                        _isCropping ? null : () => Navigator.of(context).pop(),
                    child: const Text('취소'),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    onPressed: _isCropping ? null : _crop,
                    icon: _isCropping
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.crop_rounded, size: 18),
                    label: Text(_isCropping ? '적용 중' : '사진 적용'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class GameIdentityHistory {
  const GameIdentityHistory({
    required this.displayName,
    required this.profileImageBytes,
    required this.identityNumber,
    required this.issuedDate,
    required this.selectedGames,
    required this.customGames,
    required this.previewResult,
    required this.previewError,
    required this.hasCompetitiveGame,
    required this.hasRpgGame,
  });

  final String displayName;
  final Uint8List? profileImageBytes;
  final String identityNumber;
  final String issuedDate;

  final List<GameProfile> selectedGames;
  final List<CustomGameEntry> customGames;

  final GameIdentityPreviewResult? previewResult;
  final String? previewError;

  final bool hasCompetitiveGame;
  final bool hasRpgGame;

  int get gameCount => selectedGames.length + customGames.length;
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
