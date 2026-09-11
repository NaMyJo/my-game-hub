import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/game_finder.dart';
import '../services/game_finder_repository.dart';
import '../services/my_game_picks_controller.dart';
import '../theme/app_typography.dart';
import '../utils/steam_store_launcher.dart';
import '../widgets/icon_page_header.dart';
import '../widgets/crossable_range_slider.dart';
import '../widgets/game_pick_button.dart';

const double gameFinderFloatingActionThreshold = 280;

bool shouldShowGameFinderFloatingActions(int step, double scrollOffset) =>
    step == 3 && scrollOffset > gameFinderFloatingActionThreshold;

class GameFinderPage extends StatefulWidget {
  const GameFinderPage({
    super.key,
    this.isAdmin = false,
    this.onOpenAdmin,
    this.webScrollController,
    this.mobileScrollController,
    this.active = true,
  });
  final bool isAdmin;
  final VoidCallback? onOpenAdmin;
  final ScrollController? webScrollController;
  final ScrollController? mobileScrollController;
  final bool active;
  @override
  State<GameFinderPage> createState() => _GameFinderPageState();
}

class GameFinderStepNavigation extends StatelessWidget {
  const GameFinderStepNavigation({
    super.key,
    required this.currentStep,
    required this.maxVisitedStep,
    required this.onStepSelected,
    this.webStyle = true,
  });

  final int currentStep;
  final int maxVisitedStep;
  final ValueChanged<int> onStepSelected;
  final bool webStyle;

  @override
  Widget build(BuildContext context) {
    const labels = ['취향 게임', '탐색 범위', '추천 결과'];
    if (!webStyle) {
      return LayoutBuilder(builder: (context, constraints) {
        final compact = constraints.maxWidth < 620;
        return Row(children: [
          for (var index = 0; index < labels.length; index++) ...[
            if (index > 0)
              Expanded(
                  child: Container(
                      height: 2,
                      color: index + 1 <= maxVisitedStep
                          ? const Color(0xFF6F5AE8)
                          : Theme.of(context).dividerColor)),
            Tooltip(
                message: labels[index],
                child: InkWell(
                    borderRadius: BorderRadius.circular(14),
                    onTap: index + 1 <= maxVisitedStep
                        ? () => onStepSelected(index + 1)
                        : null,
                    child: AnimatedContainer(
                        duration: const Duration(milliseconds: 140),
                        padding: EdgeInsets.symmetric(
                            horizontal: compact ? 9 : 16, vertical: 11),
                        decoration: BoxDecoration(
                            color: currentStep == index + 1
                                ? const Color(0xFF6F5AE8).withValues(alpha: .20)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(
                                color: currentStep == index + 1
                                    ? const Color(0xFF8D79FF)
                                    : Theme.of(context).dividerColor)),
                        child: Row(mainAxisSize: MainAxisSize.min, children: [
                          CircleAvatar(
                              radius: 12,
                              backgroundColor: index + 1 <= maxVisitedStep
                                  ? const Color(0xFF6F5AE8)
                                  : Theme.of(context).disabledColor,
                              child: Text('${index + 1}',
                                  style: const TextStyle(
                                      color: Colors.white, fontSize: 12))),
                          if (!compact) ...[
                            const SizedBox(width: 8),
                            Text(labels[index],
                                style: const TextStyle(
                                    fontWeight: FontWeight.w800))
                          ]
                        ]))))
          ]
        ]);
      });
    }
    return LayoutBuilder(builder: (context, constraints) {
      final compact = constraints.maxWidth < 620;
      return Wrap(spacing: 10, runSpacing: 10, children: [
        for (var index = 0; index < labels.length; index++)
          Tooltip(
              message: labels[index],
              child: InkWell(
                  borderRadius: BorderRadius.circular(30),
                  onTap: index + 1 <= maxVisitedStep
                      ? () => onStepSelected(index + 1)
                      : null,
                  child: AnimatedContainer(
                      key: ValueKey('game-finder-web-step-${index + 1}'),
                      duration: const Duration(milliseconds: 140),
                      padding: EdgeInsets.symmetric(
                          horizontal: compact ? 11 : 16, vertical: 10),
                      decoration: BoxDecoration(
                          color: currentStep == index + 1
                              ? const Color(0xFF6F5AE8).withValues(alpha: .20)
                              : (Theme.of(context).brightness == Brightness.dark
                                  ? const Color(0xFF0E1A2A)
                                  : const Color(0xFFF7F8FB)),
                          borderRadius: BorderRadius.circular(30),
                          border: Border.all(
                              color: currentStep == index + 1
                                  ? const Color(0xFF8D79FF)
                                  : Theme.of(context).dividerColor)),
                      child: Row(mainAxisSize: MainAxisSize.min, children: [
                        CircleAvatar(
                            radius: 12,
                            backgroundColor: index + 1 <= currentStep
                                ? const Color(0xFF6F5AE8)
                                : Theme.of(context).disabledColor,
                            child: index + 1 < currentStep
                                ? const Icon(Icons.check_rounded,
                                    color: Colors.white, size: 14)
                                : Text('${index + 1}',
                                    style: const TextStyle(
                                        color: Colors.white, fontSize: 12))),
                        if (!compact) ...[
                          const SizedBox(width: 8),
                          Text(labels[index],
                              style:
                                  const TextStyle(fontWeight: FontWeight.w800))
                        ]
                      ]))))
      ]);
    });
  }
}

class _GameFinderPageState extends State<GameFinderPage> {
  OverlayEntry? _floatingActions;
  bool _showFloatingActions = false;
  int step = 1;
  int maxVisitedStep = 1;
  RangeValues price = const RangeValues(0, 100000);
  RangeValues players = const RangeValues(1, 15);
  bool includeAdult = false;
  GameFinderPlayMode playMode = GameFinderPlayMode.multi;
  GameFinderPriceMode priceMode = GameFinderPriceMode.paid;
  GameFinderReleasePreference releasePreference =
      GameFinderReleasePreference.recent;
  final searchController = TextEditingController();
  final playerMinController = TextEditingController(text: '1');
  final playerMaxController = TextEditingController(text: '15');
  final priceMinController = TextEditingController(text: '0');
  final priceMaxController = TextEditingController(text: '100000');
  bool directPlayerInput = false;
  bool directPriceInput = false;
  Timer? debounce;
  List<SteamGameSearchItem> searchResults = [];
  final selected = <SteamGameSearchItem>[];
  final recent = <SteamGameSearchItem>[];
  final availableTags = <GameFinderTag>[];
  final selectedTags = <String>{};
  final shown = <int>{};
  List<GameFinderRecommendation> recommendations = [];
  bool initialLoading = true;
  bool loading = false;
  bool refreshingRecommendations = false;
  String? error;
  ScrollController? get _pageScrollController =>
      widget.webScrollController ?? widget.mobileScrollController;

  @override
  void initState() {
    super.initState();
    _pageScrollController?.addListener(_handlePageScroll);
    _loadPreferences();
  }

  @override
  void didUpdateWidget(covariant GameFinderPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.active && !widget.active) {
      _removeFloatingActions();
    } else if (!oldWidget.active && widget.active) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _handlePageScroll();
      });
    }
  }

  Future<void> _loadPreferences() async {
    try {
      final results = await Future.wait<Object>([
        GameFinderRepository.instance.preferences(),
        GameFinderRepository.instance.tags(),
      ]);
      final prefs = results[0] as GameFinderPreferences;
      final tags = results[1] as List<GameFinderTag>;
      if (!mounted) return;
      setState(() {
        selected
          ..clear()
          ..addAll(prefs.selectedGames.take(10));
        recent
          ..clear()
          ..addAll(prefs.recentGames);
        selectedTags
          ..clear()
          ..addAll(prefs.preferredTags);
        price =
            RangeValues(prefs.priceMin.toDouble(), prefs.priceMax.toDouble());
        players =
            RangeValues(prefs.playerMin.toDouble(), prefs.playerMax.toDouble());
        playerMinController.text = prefs.playerMin.toString();
        playerMaxController.text = prefs.playerMax.toString();
        priceMinController.text = prefs.priceMin.toString();
        priceMaxController.text = prefs.priceMax.toString();
        includeAdult = prefs.includeAdult;
        releasePreference = prefs.releasePreference;
        availableTags
          ..clear()
          ..addAll(tags);
        initialLoading = false;
      });
    } catch (_) {
      // 저장 조건을 불러오지 못해도 Finder 기본값으로 계속 이용합니다.
      if (mounted) setState(() => initialLoading = false);
    }
  }

  @override
  void dispose() {
    _pageScrollController?.removeListener(_handlePageScroll);
    _floatingActions?.remove();
    debounce?.cancel();
    searchController.dispose();
    playerMinController.dispose();
    playerMaxController.dispose();
    priceMinController.dispose();
    priceMaxController.dispose();
    super.dispose();
  }

  void _applyDirectPlayerRange() {
    final first = int.tryParse(playerMinController.text);
    final second = int.tryParse(playerMaxController.text);
    if (first == null || second == null) return;
    setState(() => players = normalizeDirectRange(first, second, 1, 15));
  }

  void _applyDirectPriceRange() {
    final first = int.tryParse(priceMinController.text);
    final second = int.tryParse(priceMaxController.text);
    if (first == null || second == null) return;
    setState(() => price = normalizeDirectRange(first, second, 0, 100000));
  }

  Widget _directRangeField({
    required TextEditingController controller,
    required String suffix,
    required VoidCallback onChanged,
    double width = 130,
  }) =>
      SizedBox(
        width: width,
        child: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          onChanged: (_) => onChanged(),
          decoration: InputDecoration(
            isDense: true,
            suffixText: suffix,
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          ),
        ),
      );

  void _handlePageScroll() {
    final controller = _pageScrollController;
    final floatingExitDistance =
        widget.mobileScrollController == null ? 110.0 : 170.0;
    final approachingBottom = controller?.hasClients == true &&
        controller!.position.extentAfter <= floatingExitDistance;
    final visible =
        shouldShowGameFinderFloatingActions(step, controller?.offset ?? 0) &&
            !approachingBottom &&
            widget.active;
    if (visible == _showFloatingActions) return;
    _showFloatingActions = visible;
    if (visible) {
      _floatingActions = OverlayEntry(
        builder: (context) => Positioned(
          right: widget.mobileScrollController == null ? 28 : 14,
          bottom: widget.mobileScrollController == null ? 28 : 84,
          child: Material(
            color: Colors.transparent,
            child: _resultActions(floating: true),
          ),
        ),
      );
      Overlay.of(context).insert(_floatingActions!);
    } else {
      _floatingActions?.remove();
      _floatingActions = null;
    }
    if (mounted) setState(() {});
  }

  void _removeFloatingActions() {
    _floatingActions?.remove();
    _floatingActions = null;
    _showFloatingActions = false;
  }

  void _setStep(int value) {
    setState(() => step = value);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _handlePageScroll();
    });
  }

  Widget _resultActions({bool floating = false}) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final shape =
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(15));
    final secondaryStyle = OutlinedButton.styleFrom(
      minimumSize: const Size(0, 46),
      padding: const EdgeInsets.symmetric(horizontal: 18),
      foregroundColor: dark ? const Color(0xFFE9E4FF) : const Color(0xFF433776),
      backgroundColor: dark ? const Color(0xFF111C2E) : const Color(0xFFFFFFFF),
      side: BorderSide(
        color: dark ? const Color(0xFF8074A7) : const Color(0xFF8276A8),
      ),
      shape: shape,
    );
    final primaryStyle = FilledButton.styleFrom(
      minimumSize: const Size(0, 46),
      padding: const EdgeInsets.symmetric(horizontal: 20),
      foregroundColor: const Color(0xFF241B48),
      backgroundColor: const Color(0xFFC4B5FF),
      disabledForegroundColor: const Color(0xFF544A75),
      disabledBackgroundColor: const Color(0xFF9185BD),
      shape: shape,
    );
    final actions = Wrap(
      spacing: 10,
      runSpacing: 8,
      children: [
        OutlinedButton.icon(
          key: floating
              ? const ValueKey('game-finder-floating-edit-conditions')
              : null,
          style: secondaryStyle,
          onPressed: () {
            _floatingActions?.remove();
            _floatingActions = null;
            _showFloatingActions = false;
            _setStep(2);
          },
          icon: const Icon(Icons.settings_outlined),
          label: const Text('조건 수정'),
        ),
        FilledButton.icon(
          key: floating
              ? const ValueKey('game-finder-floating-more-games')
              : null,
          style: primaryStyle,
          onPressed: loading ? null : () => recommend(more: true),
          icon: const Icon(Icons.refresh),
          label: const Text('다른 게임 보기'),
        ),
      ],
    );
    return actions;
  }

  void searchChanged(String value) {
    debounce?.cancel();
    final query = value.trim();
    setState(() {
      if (query.length < 2) {
        searchResults = [];
        loading = false;
      }
    });
    if (query.length < 2) return;

    debounce = Timer(const Duration(milliseconds: 450), () async {
      setState(() => loading = true);
      try {
        final result = await GameFinderRepository.instance.search(query);
        if (mounted) setState(() => searchResults = result);
      } catch (_) {
        if (mounted) setState(() => error = 'Steam 게임 검색에 실패했습니다.');
      } finally {
        if (mounted) setState(() => loading = false);
      }
    });
  }

  void _addSearchResult(SteamGameSearchItem game) {
    debounce?.cancel();
    setState(() {
      selected.add(game);
      searchController.clear();
      searchResults = [];
      error = null;
    });
  }

  Future<void> _removeRecent(SteamGameSearchItem game) async {
    final index = recent.indexOf(game);
    setState(() => recent.remove(game));
    try {
      await GameFinderRepository.instance.removeRecentGame(game.appId);
    } catch (_) {
      if (!mounted) return;
      setState(() => recent.insert(index.clamp(0, recent.length), game));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('최근 선택 기록을 삭제하지 못했습니다.')),
      );
    }
  }

  void _addPickToTaste(int appId, String name, String? imageUrl) {
    if (selected.length >= 10 || selected.any((game) => game.appId == appId)) {
      return;
    }
    setState(() => selected.add(
        SteamGameSearchItem(appId: appId, name: name, imageUrl: imageUrl)));
  }

  Future<void> recommend({bool more = false}) async {
    if (!canRequestGameFinderRecommendation(
        selected.map((game) => game.appId), selectedTags)) {
      return;
    }
    if (more) {
      _removeFloatingActions();
    }
    setState(() {
      loading = true;
      refreshingRecommendations = more;
      if (more) recommendations = [];
      error = null;
    });
    if (more) {
      final controller = _pageScrollController;
      if (controller?.hasClients == true) {
        controller!.jumpTo(controller.position.minScrollExtent);
      }
    }
    try {
      final result = await GameFinderRepository.instance.recommend(
          likedIds: selected.map((e) => e.appId).toList(),
          preferredTags: selectedTags.toList(),
          priceMin: price.start.round(),
          priceMax: price.end.round(),
          includeAdult: includeAdult,
          playerMin: players.start.round(),
          playerMax: players.end.round(),
          playMode: playMode,
          priceMode: priceMode,
          releasePreference: releasePreference,
          excluded: more ? shown : <int>{});
      final saved = await GameFinderRepository.instance.savePreferences(
          selectedIds: selected.map((e) => e.appId).toList(),
          preferredTags: selectedTags.toList(),
          priceMin: price.start.round(),
          priceMax: price.end.round(),
          includeAdult: includeAdult,
          playerMin: players.start.round(),
          playerMax: players.end.round(),
          releasePreference: releasePreference);
      if (mounted) {
        final orderedResult = _orderRecommendations(result);
        setState(() {
          recent
            ..clear()
            ..addAll(saved.recentGames);
          recommendations = orderedResult;
          shown.addAll(orderedResult.map((e) => e.appId));
          step = 3;
          maxVisitedStep = 3;
        });
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted) return;
          final mobileController = widget.mobileScrollController;
          if (!more && mobileController?.hasClients == true) {
            mobileController!.jumpTo(0);
          }
          _handlePageScroll();
        });
      }
    } catch (_) {
      if (mounted) setState(() => error = '추천 결과를 불러오지 못했습니다.');
    } finally {
      if (mounted) {
        setState(() {
          loading = false;
          refreshingRecommendations = false;
        });
      }
    }
  }

  List<GameFinderRecommendation> _orderRecommendations(
    List<GameFinderRecommendation> values,
  ) {
    final ordered = List<GameFinderRecommendation>.from(values);
    if (releasePreference != GameFinderReleasePreference.recent) {
      ordered.sort((left, right) {
        final scoreOrder = right.matchScore.compareTo(left.matchScore);
        return scoreOrder != 0 ? scoreOrder : left.appId.compareTo(right.appId);
      });
      return ordered;
    }

    final today = DateTime.now();
    final releaseCutoff = DateTime(today.year, today.month, today.day);
    ordered.sort((left, right) {
      final leftDate = _releasedDate(left.releaseDate, releaseCutoff);
      final rightDate = _releasedDate(right.releaseDate, releaseCutoff);

      if (leftDate == null && rightDate == null) {
        final scoreOrder = right.matchScore.compareTo(left.matchScore);
        return scoreOrder != 0 ? scoreOrder : left.appId.compareTo(right.appId);
      }
      if (leftDate == null) return 1;
      if (rightDate == null) return -1;

      final dateOrder = rightDate.compareTo(leftDate);
      if (dateOrder != 0) return dateOrder;

      final scoreOrder = right.matchScore.compareTo(left.matchScore);
      return scoreOrder != 0 ? scoreOrder : left.appId.compareTo(right.appId);
    });
    return ordered;
  }

  DateTime? _releasedDate(String? value, DateTime releaseCutoff) {
    if (value == null) return null;
    final parsed = DateTime.tryParse(value);
    if (parsed == null || parsed.isAfter(releaseCutoff)) return null;
    return parsed;
  }

  @override
  Widget build(BuildContext context) {
    final webStyle = widget.webScrollController != null;
    if (initialLoading) {
      return const SizedBox(
        height: 320,
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (!webStyle) {
      return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (step == 3)
          _results()
        else ...[
          _taste(),
          const SizedBox(height: 16),
          _filters(),
        ],
        if (loading && !refreshingRecommendations)
          const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()))
      ]);
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Expanded(
          child: const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              IconPageHeader(icon: Icons.explore_rounded, title: 'GAME FINDER'),
              SizedBox(height: 5),
              Text(
                '취향 반영 스팀 내 게임 검색 서비스',
                style: TextStyle(
                  color: Color(0xFF8290A4),
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
        if (widget.isAdmin)
          OutlinedButton.icon(
            onPressed: widget.onOpenAdmin,
            icon: const Icon(Icons.admin_panel_settings_outlined),
            label: const Text('관리'),
          ),
      ]),
      const SizedBox(height: 20),
      _stepNavigation(),
      const SizedBox(height: 18),
      if (error != null)
        Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            color: Colors.red.withValues(alpha: .1),
            child: Text(error!)),
      if (step == 1) _taste(),
      if (step == 2) _filters(),
      if (step == 3) _results(),
      if (loading && !refreshingRecommendations)
        const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()))
    ]);
  }

  Widget _panel(Widget child) => Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark
              ? const Color(0xFF091322)
              : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: Theme.of(context).brightness == Brightness.dark
                  ? const Color(0xFF24344B)
                  : const Color(0xFFDDE3EC))),
      child: child);

  Widget _stepNavigation() {
    return GameFinderStepNavigation(
      currentStep: step,
      maxVisitedStep: maxVisitedStep,
      webStyle: widget.webScrollController != null,
      onStepSelected: _setStep,
    );
  }

  Widget _filters() =>
      _panel(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('탐색 범위',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        const SizedBox(height: 20),
        const Text('출시 선호',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        Wrap(
            spacing: 8,
            runSpacing: 8,
            children: GameFinderReleasePreference.values
                .map((value) => ChoiceChip(
                    label: Text(value.label),
                    selected: releasePreference == value,
                    onSelected: (_) =>
                        setState(() => releasePreference = value)))
                .toList()),
        const SizedBox(height: 22),
        const Text('플레이 방식',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        SegmentedButton<GameFinderPlayMode>(
            segments: GameFinderPlayMode.values
                .map((value) =>
                    ButtonSegment(value: value, label: Text(value.label)))
                .toList(),
            selected: {playMode},
            onSelectionChanged: (value) =>
                setState(() => playMode = value.first)),
        if (playMode.showsPlayerRange) ...[
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: Text(
                  '플레이 인원 ${players.start.round()}명 ~ ${players.end == 15 ? '15명+' : '${players.end.round()}명'}',
                  style: AppTypography.numericStyle),
            ),
            if (widget.webScrollController != null)
              TextButton.icon(
                style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    padding: const EdgeInsets.symmetric(horizontal: 9)),
                onPressed: () => setState(() {
                  directPlayerInput = !directPlayerInput;
                  if (directPlayerInput) {
                    playerMinController.text = players.start.round().toString();
                    playerMaxController.text = players.end.round().toString();
                  }
                }),
                icon: Icon(
                    directPlayerInput
                        ? Icons.linear_scale_rounded
                        : Icons.edit_outlined,
                    size: 15),
                label: Text(directPlayerInput ? '슬라이더 사용' : '직접 입력'),
              ),
          ]),
          if (widget.webScrollController != null && directPlayerInput)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 10,
                runSpacing: 8,
                children: [
                  _directRangeField(
                      controller: playerMinController,
                      suffix: '명',
                      onChanged: _applyDirectPlayerRange),
                  const Text('~'),
                  _directRangeField(
                      controller: playerMaxController,
                      suffix: '명',
                      onChanged: _applyDirectPlayerRange),
                  const Text('15명 이상은 15명+로 적용됩니다.',
                      style: TextStyle(fontSize: 12)),
                ],
              ),
            )
          else
            CrossableRangeSlider(
                key: const ValueKey('game-finder-player-range'),
                debugLabel: 'player',
                enableTrackTap: true,
                values: players,
                min: 1,
                max: 15,
                divisions: 14,
                onChanged: (v) => setState(() => players = v))
        ],
        const SizedBox(height: 18),
        const Text('가격 유형',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        SegmentedButton<GameFinderPriceMode>(
            segments: GameFinderPriceMode.values
                .map((value) =>
                    ButtonSegment(value: value, label: Text(value.label)))
                .toList(),
            selected: {priceMode},
            onSelectionChanged: (value) =>
                setState(() => priceMode = value.first)),
        if (priceMode.showsPriceRange) ...[
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: Text(
                  '가격 ${_won(price.start.round())} ~ ${price.end == 100000 ? '₩100,000+' : _won(price.end.round())}',
                  style: AppTypography.numericStyle),
            ),
            if (widget.webScrollController != null)
              TextButton.icon(
                style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    padding: const EdgeInsets.symmetric(horizontal: 9)),
                onPressed: () => setState(() {
                  directPriceInput = !directPriceInput;
                  if (directPriceInput) {
                    priceMinController.text = price.start.round().toString();
                    priceMaxController.text = price.end.round().toString();
                  }
                }),
                icon: Icon(
                    directPriceInput
                        ? Icons.linear_scale_rounded
                        : Icons.edit_outlined,
                    size: 15),
                label: Text(directPriceInput ? '슬라이더 사용' : '직접 입력'),
              ),
          ]),
          if (widget.webScrollController != null && directPriceInput)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 10,
                runSpacing: 8,
                children: [
                  _directRangeField(
                      controller: priceMinController,
                      suffix: '원',
                      width: 160,
                      onChanged: _applyDirectPriceRange),
                  const Text('~'),
                  _directRangeField(
                      controller: priceMaxController,
                      suffix: '원',
                      width: 160,
                      onChanged: _applyDirectPriceRange),
                  const Text('₩100,000 이상은 ₩100,000+로 적용됩니다.',
                      style: TextStyle(fontSize: 12)),
                ],
              ),
            )
          else
            CrossableRangeSlider(
                key: const ValueKey('game-finder-price-range'),
                debugLabel: 'price',
                enableTrackTap: true,
                values: price,
                min: 0,
                max: 100000,
                divisions: 100,
                onChanged: (v) => setState(() => price = v))
        ],
        const SizedBox(height: 12),
        SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('성인용 게임 포함'),
            subtitle: const Text('끄면 성인용으로 확인된 게임을 제외합니다.'),
            value: includeAdult,
            onChanged: (v) => setState(() => includeAdult = v)),
        const SizedBox(height: 18),
        Row(
            mainAxisAlignment: widget.webScrollController != null
                ? MainAxisAlignment.spaceBetween
                : MainAxisAlignment.center,
            children: [
              if (widget.webScrollController != null)
                TextButton(
                    onPressed: () => _setStep(1), child: const Text('취향 게임 수정'))
              else
                const SizedBox.shrink(),
              FilledButton.icon(
                  onPressed: !canRequestGameFinderRecommendation(
                              selected.map((game) => game.appId),
                              selectedTags) ||
                          loading
                      ? null
                      : () => recommend(),
                  icon: const Icon(Icons.auto_awesome),
                  label: const Text('게임 추천받기'))
            ]),
        if (widget.webScrollController == null && error != null) ...[
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFF321823),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF6B3041)),
            ),
            child: Text(error!, textAlign: TextAlign.center),
          ),
        ],
      ]));
  Widget _taste() =>
      _panel(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(
              child: widget.webScrollController != null
                  ? const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('게임 검색',
                            style: TextStyle(
                                fontSize: 20, fontWeight: FontWeight.w900)),
                        SizedBox(height: 4),
                        Text('추가 시 취향 일치도에 반영됩니다',
                            style: TextStyle(
                                fontSize: 12, color: Color(0xFF8290A4))),
                      ],
                    )
                  : const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('게임 추가',
                            style: TextStyle(
                                fontSize: 20, fontWeight: FontWeight.w900)),
                        SizedBox(height: 4),
                        Text('추가 시 취향 일치도에 반영됩니다',
                            style: TextStyle(
                                fontSize: 12, color: Color(0xFF8290A4))),
                      ],
                    )),
          Text('${selected.length} / 10')
        ]),
        const SizedBox(height: 14),
        TextField(
            controller: searchController,
            onChanged: searchChanged,
            decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Steam 게임명 검색',
                border: OutlineInputBorder())),
        if (searchController.text.trim().length == 1) ...[
          const SizedBox(height: 6),
          const Padding(
            padding: EdgeInsets.only(left: 4),
            child: Text(
              '두 글자 이상 입력해 주세요',
              style: TextStyle(fontSize: 12, color: Color(0xFF8290A4)),
            ),
          ),
        ],
        if (selected.isNotEmpty) ...[
          const SizedBox(height: 12),
          Wrap(
              spacing: 8,
              runSpacing: 8,
              children: selected
                  .map((g) => InputChip(
                      label: Text(g.name),
                      onDeleted: () => setState(() => selected.remove(g))))
                  .toList())
        ],
        if (recent.isNotEmpty) ...[
          const SizedBox(height: 18),
          const Text('최근 선택한 취향 게임',
              style: TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          Wrap(
              spacing: 8,
              runSpacing: 8,
              children: recent
                  .where((g) => !selected.any((s) => s.appId == g.appId))
                  .take(20)
                  .map((g) => InputChip(
                      avatar: const Icon(Icons.history, size: 16),
                      label: Text(g.name),
                      onPressed: selected.length >= 10
                          ? null
                          : () => setState(() => selected.add(g)),
                      deleteIcon:
                          const Icon(Icons.delete_outline_rounded, size: 18),
                      deleteButtonTooltipMessage: '최근 선택 기록 삭제',
                      onDeleted: () => _removeRecent(g)))
                  .toList()),
        ],
        const SizedBox(height: 18),
        const Text('My Game Picks',
            style: TextStyle(fontWeight: FontWeight.w800)),
        const SizedBox(height: 5),
        const Text('저장한 게임 중 이번 검색의 취향에 반영할 게임을 선택하세요.',
            style: TextStyle(fontSize: 12, color: Color(0xFF8290A4))),
        const SizedBox(height: 8),
        AnimatedBuilder(
          animation: MyGamePicksController.instance,
          builder: (context, _) {
            final picks = MyGamePicksController.instance.items
                .where(
                    (pick) => !selected.any((game) => game.appId == pick.appId))
                .toList();
            if (MyGamePicksController.instance.loading) {
              return const Padding(
                padding: EdgeInsets.symmetric(vertical: 6),
                child: LinearProgressIndicator(),
              );
            }
            if (picks.isEmpty) {
              return const Text('추가할 수 있는 저장 게임이 없습니다.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF8290A4)));
            }
            return Wrap(
              spacing: 8,
              runSpacing: 8,
              children: picks
                  .map((game) => ActionChip(
                        avatar: const Icon(Icons.bookmark_rounded, size: 16),
                        label: Text(game.name),
                        onPressed: selected.length >= 10
                            ? null
                            : () => _addPickToTaste(
                                game.appId, game.name, game.imageUrl),
                      ))
                  .toList(),
            );
          },
        ),
        const SizedBox(height: 14),
        if (!loading &&
            searchController.text.length >= 2 &&
            searchResults.isEmpty)
          const Text('검색 결과가 없습니다.'),
        ...searchResults
            .where((g) => !selected.any((s) => s.appId == g.appId))
            .take(10)
            .map((g) => ListTile(
                leading: _image(g.imageUrl, 48),
                title: Text(g.name),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  GamePickButton(steamAppId: g.appId),
                  const Icon(Icons.add_circle_outline),
                ]),
                onTap:
                    selected.length >= 10 ? null : () => _addSearchResult(g))),
        const Divider(height: 34),
        Row(children: [
          const Expanded(
              child: Text('선호 태그',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900))),
          Text('${selectedTags.length} / 10'),
        ]),
        const SizedBox(height: 6),
        const Text('태그는 제외 조건이 아니라 추천 점수를 높이는 선호도로 반영됩니다.'),
        const SizedBox(height: 10),
        _tagGroup('장르', 'GENRE'),
        _tagGroup('플레이 방식', 'FEATURE'),
        _tagGroup('테마 / 분위기', 'THEME'),
        _tagGroup('게임 스타일', 'STYLE'),
        _tagGroup('기타', 'TAG'),
        const SizedBox(height: 16),
        if (widget.webScrollController != null)
          Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                  onPressed: !canRequestGameFinderRecommendation(
                          selected.map((game) => game.appId), selectedTags)
                      ? null
                      : () => setState(() {
                            step = 2;
                            if (maxVisitedStep < 2) maxVisitedStep = 2;
                          }),
                  icon: const Icon(Icons.arrow_forward),
                  label: const Text('조건 설정')))
      ]));

  Widget _tagGroup(String title, String type) {
    final group = availableTags.where((tag) => tag.type == type).toList();
    if (group.isEmpty) return const SizedBox.shrink();
    return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title,
              style:
                  const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
          const SizedBox(height: 7),
          Wrap(
              spacing: 8,
              runSpacing: 8,
              children: group.map((tag) {
                final active = selectedTags.contains(tag.canonicalName);
                return FilterChip(
                    label: Text(tag.displayName),
                    selected: active,
                    onSelected: (value) => setState(() {
                          if (value && selectedTags.length < 10) {
                            selectedTags.add(tag.canonicalName);
                          } else if (!value) {
                            selectedTags.remove(tag.canonicalName);
                          }
                        }));
              }).toList())
        ]));
  }

  Widget _results() =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Visibility(
          visible: !_showFloatingActions,
          maintainState: true,
          maintainAnimation: true,
          maintainSize: true,
          child: Align(
            alignment: widget.webScrollController == null
                ? Alignment.center
                : Alignment.centerRight,
            child: _resultActions(),
          ),
        ),
        const SizedBox(height: 12),
        _panel(Wrap(spacing: 16, runSpacing: 10, children: [
          _resultCondition('플레이 방식', playMode.label),
          if (playMode.showsPlayerRange)
            _resultCondition('인원',
                '${players.start.round()}명~${players.end == 15 ? '15명+' : '${players.end.round()}명'}'),
          _resultCondition('가격 유형', priceMode.label),
          if (priceMode.showsPriceRange)
            _resultCondition('가격',
                '${_won(price.start.round())}~${price.end == 100000 ? '₩100,000+' : _won(price.end.round())}'),
          _resultCondition('성인 게임', includeAdult ? '포함' : '제외'),
          _resultCondition('취향 게임', '${selected.length}개'),
          _resultCondition('출시 선호', releasePreference.label)
        ])),
        const SizedBox(height: 16),
        if (refreshingRecommendations)
          _panel(const Column(
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text(
                '현재 조건으로 다시 검색하는 중입니다.\n잠시만 기다려 주십시오.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
              ),
            ],
          ))
        else if (recommendations.isEmpty)
          _panel(Column(children: [
            const Icon(Icons.explore_outlined,
                size: 44, color: Color(0xFF8D79FF)),
            const SizedBox(height: 12),
            const Text('조건에 맞는 게임을 모두 확인했어요',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
            const SizedBox(height: 6),
            const Text('조건을 조금 넓히거나 취향을 수정하면 더 많은 게임을 찾을 수 있어요.'),
            const SizedBox(height: 14),
            Wrap(spacing: 8, runSpacing: 8, children: [
              OutlinedButton(
                  onPressed: () => setState(() => step = 2),
                  child: const Text('조건 수정')),
              FilledButton(
                  onPressed: () => setState(() => step = 1),
                  child: const Text('취향 다시 선택'))
            ])
          ]))
        else
          LayoutBuilder(builder: (context, c) {
            final mobileResults = widget.webScrollController == null;
            final columns = mobileResults
                ? 2
                : c.maxWidth >= 1100
                    ? 4
                    : c.maxWidth >= 700
                        ? 3
                        : c.maxWidth >= 430
                            ? 2
                            : 1;
            return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: recommendations.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: columns,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: mobileResults ? .61 : .82),
                itemBuilder: (_, i) => _card(recommendations[i]));
          }),
        if (recommendations.isNotEmpty) ...[
          const SizedBox(height: 18),
          Align(
            alignment: widget.webScrollController == null
                ? Alignment.center
                : Alignment.centerRight,
            child: _resultActions(),
          ),
        ],
        const SizedBox(height: 18),
      ]);

  Widget _resultCondition(String label, String value) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final labelColor =
        isDark ? const Color(0xFF9B8CFF) : const Color(0xFF654ED6);
    final valueColor =
        isDark ? const Color(0xFFF4F1FF) : const Color(0xFF202638);

    return RichText(
      text: TextSpan(
        style: DefaultTextStyle.of(context).style.copyWith(
              fontSize: 14,
              fontWeight: FontWeight.w700,
            ),
        children: [
          TextSpan(
            text: '$label ',
            style: TextStyle(color: labelColor),
          ),
          TextSpan(
            text: value,
            style: TextStyle(color: valueColor),
          ),
        ],
      ),
    );
  }

  Widget _card(GameFinderRecommendation g) => Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
          onTap: () => launchSteamStore(g.storeUrl),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            AspectRatio(
                aspectRatio: 460 / 215,
                child: _image(g.imageUrl, double.infinity)),
            Expanded(
                child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Scrollbar(
                        child: SingleChildScrollView(
                            primary: false,
                            child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Expanded(
                                        child: Text(g.name,
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(
                                                fontSize: 16,
                                                fontWeight: FontWeight.w900)),
                                      ),
                                      GamePickButton(steamAppId: g.appId),
                                    ],
                                  ),
                                  const SizedBox(height: 7),
                                  Text('취향 일치도 ${g.matchScore}%',
                                      style: AppTypography.numericStyle
                                          .copyWith(
                                              color: Color(0xFF8D79FF),
                                              fontWeight: FontWeight.w800)),
                                  if (g.genres.any(selectedTags.contains)) ...[
                                    const SizedBox(height: 4),
                                    Text(
                                        '선호 태그 ${g.genres.where(selectedTags.contains).take(2).map(_tagLabel).join(' · ')} 일치',
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodySmall),
                                  ],
                                  const SizedBox(height: 7),
                                  Text(
                                    _price(g),
                                    style: AppTypography.numericStyle.copyWith(
                                      color: Theme.of(context).brightness ==
                                              Brightness.dark
                                          ? const Color(0xFF65D6B4)
                                          : const Color(0xFF087F68),
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    _release(g),
                                    style: AppTypography.numericStyle.copyWith(
                                      color: Theme.of(context).brightness ==
                                              Brightness.dark
                                          ? const Color(0xFF8DBDFF)
                                          : const Color(0xFF2866B1),
                                    ),
                                  ),
                                  const SizedBox(height: 7),
                                  Text(
                                      [
                                        if (g.singlePlayer == true) '싱글',
                                        if (g.multiplayer == true) '멀티',
                                        if (g.onlineCoop == true) '온라인 협동',
                                        if (g.maxPlayers != null)
                                          '최대 ${g.maxPlayers}명'
                                      ].join(' · '),
                                      maxLines: 2),
                                  const SizedBox(height: 14),
                                  Text(
                                    g.genres.take(3).map(_tagLabel).join(' · '),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      color: Theme.of(context).brightness ==
                                              Brightness.dark
                                          ? const Color(0xFFC1B7F4)
                                          : const Color(0xFF6756A8),
                                      fontSize:
                                          widget.webScrollController == null
                                              ? 11
                                              : 13,
                                      height: 1.25,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  Row(
                                      mainAxisAlignment: MainAxisAlignment.end,
                                      children: [
                                        Text(
                                          'Steam Store',
                                          style: TextStyle(
                                            fontSize:
                                                widget.webScrollController ==
                                                        null
                                                    ? 11
                                                    : 13,
                                            color:
                                                Theme.of(context).brightness ==
                                                        Brightness.dark
                                                    ? const Color(0xFFB4BECD)
                                                    : const Color(0xFF596579),
                                          ),
                                        ),
                                        const SizedBox(width: 4),
                                        const Icon(Icons.open_in_new, size: 13)
                                      ])
                                ])))))
          ])));
  Widget _image(String? url, double size) => url == null || url.isEmpty
      ? Container(
          width: size,
          color: const Color(0xFF18243A),
          child: const Icon(Icons.sports_esports))
      : Image.network(url,
          width: size,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Container(
              width: size,
              color: const Color(0xFF18243A),
              child: const Icon(Icons.broken_image_outlined)));
  String _price(GameFinderRecommendation g) {
    if (g.isFree) return '무료';
    if (g.currentPrice == null) return '가격 미정';
    final discount =
        (g.discountPercent ?? 0) > 0 ? ' (-${g.discountPercent}%)' : '';
    return '${_won(g.currentPrice!)}$discount';
  }

  String _release(GameFinderRecommendation g) {
    if (g.releaseDate != null) {
      final d = DateTime.tryParse(g.releaseDate!);
      if (d != null) {
        return '${d.year}. ${d.month.toString().padLeft(2, '0')}. ${d.day.toString().padLeft(2, '0')} ${g.comingSoon ? '출시 예정' : '출시'}';
      }
    }
    return g.comingSoon
        ? (g.releaseDateText?.isNotEmpty == true
            ? '${g.releaseDateText} 출시 예정'
            : '출시 예정')
        : (g.releaseDateText?.isNotEmpty == true
            ? '${g.releaseDateText} 출시'
            : '출시일 미상');
  }

  String _tagLabel(String canonicalName) {
    for (final tag in availableTags) {
      if (tag.canonicalName == canonicalName) return tag.displayName;
    }
    return canonicalName;
  }

  String _won(int value) =>
      '₩${value.toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (m) => ',')}';
}
