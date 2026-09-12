import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/game_identity_preview.dart';
import '../models/game_profile.dart';
import '../models/public_profile.dart';
import '../services/api_client.dart';
import '../services/public_profile_repository.dart';

class GamePowerAnalysisPage extends StatefulWidget {
  const GamePowerAnalysisPage({super.key, this.initialData});
  final GameIdentityPreviewResult? initialData;

  @override
  State<GamePowerAnalysisPage> createState() => _GamePowerAnalysisPageState();
}

class _GamePowerAnalysisPageState extends State<GamePowerAnalysisPage> {
  GameIdentityPreviewResult? data;
  Object? error;

  @override
  void initState() {
    super.initState();
    data = widget.initialData;
    if (data == null) _load();
  }

  Future<void> _load() async {
    try {
      final value = await PublicProfileRepository.instance.getAnalysis();
      if (mounted) setState(() => data = value);
    } catch (e) {
      if (mounted) setState(() => error = e);
    }
  }

  @override
  Widget build(BuildContext context) => _PublicShell(
        title: '대시보드 분석',
        child: error != null
            ? const _EmptyMessage('게임력 분석을 불러오지 못했습니다.')
            : data == null
                ? const Center(child: CircularProgressIndicator())
                : GamePowerAnalysisView(data: data!),
      );
}

class GamePowerAnalysisView extends StatelessWidget {
  const GamePowerAnalysisView({super.key, required this.data});
  final GameIdentityPreviewResult data;

  @override
  Widget build(BuildContext context) {
    final included = data.games.where((e) => e.includedInAverage).toList();
    final excludedCount = data.games.length - included.length;
    final strongest = included.isEmpty
        ? null
        : included.reduce(
            (a, b) => (a.topPercent ?? 101) <= (b.topPercent ?? 101) ? a : b);
    final withinTwenty =
        included.where((e) => (e.topPercent ?? 101) <= 20).length;
    final estimated = included.any((e) => e.estimated);

    final averageText = data.averageTopPercent == null
        ? '계산 제외'
        : '상위 ${estimated ? '약 ' : ''}${_percent(data.averageTopPercent!)}%';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '${data.displayName}님의 게임 데이터를 분석했어요.',
          style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 7),
        const Text(
          '등록된 경쟁 게임을 기준으로 계산한 결과입니다.',
          style: TextStyle(color: Color(0xFF8997AD), fontSize: 14),
        ),
        const SizedBox(height: 24),
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 900
                ? 3
                : constraints.maxWidth >= 560
                    ? 2
                    : 1;
            const gap = 14.0;
            final width =
                (constraints.maxWidth - gap * (columns - 1)) / columns;
            return Wrap(
              spacing: gap,
              runSpacing: gap,
              children: [
                SizedBox(
                  width: width,
                  child: _OverallPowerCard(
                    percent: data.averageTopPercent,
                    estimated: estimated,
                  ),
                ),
                SizedBox(
                  width: width,
                  child: _StrongestGameCard(entry: strongest),
                ),
                SizedBox(
                  width: width,
                  child: _AnalysisOverviewCard(
                    totalCount: data.games.length,
                    withinTwentyCount: withinTwenty,
                    includedCount: included.length,
                    excludedCount: excludedCount,
                  ),
                ),
              ],
            );
          },
        ),
        const SizedBox(height: 22),
        _GameInsightCard(
          message: data.evaluationMessage,
          averageText: averageText,
          includedCount: included.length,
          withinTwentyCount: withinTwenty,
        ),
        const SizedBox(height: 32),
        Row(
          children: [
            const Icon(Icons.bar_chart_rounded,
                color: Color(0xFF927BFF), size: 25),
            const SizedBox(width: 9),
            const Text('게임별 분석',
                style: TextStyle(fontSize: 21, fontWeight: FontWeight.w900)),
            const SizedBox(width: 10),
            _CountBadge(count: data.games.length),
          ],
        ),
        const SizedBox(height: 14),
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 760 ? 2 : 1;
            const gap = 12.0;
            final width =
                (constraints.maxWidth - gap * (columns - 1)) / columns;
            return Wrap(
              spacing: gap,
              runSpacing: gap,
              children: data.games
                  .map((entry) => SizedBox(
                        width: width,
                        child: _AnalysisGameCard(entry: entry),
                      ))
                  .toList(),
            );
          },
        ),
      ],
    );
  }
}

class PublicProfilePage extends StatefulWidget {
  const PublicProfilePage({super.key, required this.publicId});
  final String publicId;
  @override
  State<PublicProfilePage> createState() => _PublicProfilePageState();
}

class _PublicProfilePageState extends State<PublicProfilePage> {
  PublicProfileData? data;
  bool missing = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await PublicProfileRepository.instance
          .getPublicProfile(widget.publicId);
      if (mounted) setState(() => data = value);
    } on ApiException catch (e) {
      if (mounted) setState(() => missing = e.statusCode == 404);
    } catch (_) {
      if (mounted) setState(() => missing = true);
    }
  }

  @override
  Widget build(BuildContext context) => _PublicShell(
        title: 'MY GAME HUB · PUBLIC GAMER PROFILE',
        child: missing
            ? const _EmptyMessage('존재하지 않거나 비공개된 프로필입니다.')
            : data == null
                ? const Center(child: CircularProgressIndicator())
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                        Text(data!.nickname,
                            style: const TextStyle(
                                fontSize: 32, fontWeight: FontWeight.w900)),
                        if (data!.introduction.isNotEmpty)
                          Text(data!.introduction),
                        const SizedBox(height: 24),
                        GamePowerAnalysisView(data: data!.gamePower),
                        if (data!.latestIdentity != null) ...[
                          const SizedBox(height: 24),
                          const Text('최근 GAME IDENTITY',
                              style: TextStyle(
                                  fontSize: 20, fontWeight: FontWeight.w900)),
                          const SizedBox(height: 10),
                          SharedIdentityCard(data: data!.latestIdentity!),
                        ],
                      ]),
      );
}

class SharedIdentityPage extends StatefulWidget {
  const SharedIdentityPage({super.key, required this.shareId});
  final String shareId;
  @override
  State<SharedIdentityPage> createState() => _SharedIdentityPageState();
}

class _SharedIdentityPageState extends State<SharedIdentityPage> {
  PublicIdentityData? data;
  bool missing = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await PublicProfileRepository.instance
          .getSharedIdentity(widget.shareId);
      if (mounted) setState(() => data = value);
    } on ApiException catch (e) {
      if (mounted) setState(() => missing = e.statusCode == 404);
    } catch (_) {
      if (mounted) setState(() => missing = true);
    }
  }

  @override
  Widget build(BuildContext context) => _PublicShell(
        title: 'MY GAME HUB · SHARED GAME IDENTITY',
        child: missing
            ? const _EmptyMessage('존재하지 않거나 공유가 중단된 게임 신분증입니다.')
            : data == null
                ? const Center(child: CircularProgressIndicator())
                : Center(
                    child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 680),
                        child: SharedIdentityCard(data: data!))),
      );
}

class SharedIdentityCard extends StatelessWidget {
  const SharedIdentityCard({super.key, required this.data});
  final PublicIdentityData data;

  @override
  Widget build(BuildContext context) {
    List<dynamic> games = const [];
    List<dynamic> custom = const [];
    try {
      final snapshot = jsonDecode(data.snapshotJson);
      if (snapshot is Map) {
        games = snapshot['selectedGames'] as List? ?? const [];
        custom = snapshot['customGames'] as List? ?? const [];
      }
    } catch (_) {}
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
            colors: [Color(0xFF121D35), Color(0xFF18143A), Color(0xFF081321)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: const Color(0xFF6655D8), width: 1.5),
        boxShadow: const [
          BoxShadow(
              color: Color(0x44000000), blurRadius: 28, offset: Offset(0, 12))
        ],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('GAME ID CARD',
            style: TextStyle(
                color: Color(0xFFF0EDFF),
                fontSize: 20,
                fontWeight: FontWeight.w900,
                letterSpacing: 1.2)),
        const SizedBox(height: 22),
        Text(data.displayName,
            style: const TextStyle(
                color: Colors.white,
                fontSize: 30,
                fontWeight: FontWeight.w900)),
        Text('ID ${data.identityNumber} · ${data.issuedDate}',
            style: const TextStyle(color: Color(0xFF8C96AD), fontSize: 11)),
        const Divider(height: 34, color: Color(0xFF33405A)),
        ...games.whereType<Map>().map((g) => _IdentityGameRow(
              name: g['gameType']?.toString() ?? 'GAME',
              account: g['accountName']?.toString() ?? '',
              metric: g['metricValue']?.toString() ??
                  g['primaryValue']?.toString() ??
                  '-',
            )),
        ...custom.whereType<Map>().map((g) => _IdentityGameRow(
              name: g['gameName']?.toString() ?? 'CUSTOM GAME',
              account: '',
              metric: g['playInfo']?.toString() ?? '-',
            )),
        const SizedBox(height: 16),
        Text(
            data.gamePowerPercent == null
                ? 'RPG / CUSTOM PROFILE'
                : 'GAME POWER · 상위 ${_percent(data.gamePowerPercent!)}%',
            style: const TextStyle(
                color: Color(0xFFC5BCFF),
                fontSize: 17,
                fontWeight: FontWeight.w900)),
        const SizedBox(height: 8),
        Text(data.evaluationMessage,
            style: const TextStyle(color: Color(0xFFD0D4E3), height: 1.5)),
      ]),
    );
  }
}

class _AnalysisGameCard extends StatelessWidget {
  const _AnalysisGameCard({required this.entry});
  final GameIdentityPreviewEntry entry;

  @override
  Widget build(BuildContext context) {
    final included = entry.includedInAverage && entry.topPercent != null;
    final percentile = entry.topPercent;
    final progress = percentile == null
        ? 0.0
        : (1 - percentile / 100).clamp(0.0, 1.0).toDouble();

    return _AnalysisSurface(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _GameIcon(gameType: entry.gameType, size: 56),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      entry.gameType.displayName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      entry.accountName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Color(0xFFC5CCE0),
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              _StatusBadge(
                included: included,
                percentile: percentile,
                estimated: entry.estimated,
              ),
            ],
          ),
          const SizedBox(height: 20),
          _MetricRow(label: entry.metricLabel, value: entry.metricValue),
          const SizedBox(height: 18),
          if (included) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(20),
              child: LinearProgressIndicator(
                minHeight: 7,
                value: progress,
                backgroundColor: const Color(0xFF1A2940),
                valueColor:
                    const AlwaysStoppedAnimation<Color>(Color(0xFF8B6DFF)),
              ),
            ),
            const SizedBox(height: 10),
            const Row(
              children: [
                DecoratedBox(
                  decoration: BoxDecoration(
                    color: Color(0xFF42D6A4),
                    shape: BoxShape.circle,
                  ),
                  child: SizedBox.square(dimension: 8),
                ),
                SizedBox(width: 8),
                Text(
                  '평균 포함',
                  style: TextStyle(
                    color: Color(0xFF77E5BF),
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ] else
            _ExclusionNotice(
              reason: entry.exclusionReason ?? '비교 가능한 기록이 없습니다.',
            ),
        ],
      ),
    );
  }
}

class _OverallPowerCard extends StatelessWidget {
  const _OverallPowerCard({required this.percent, required this.estimated});

  final double? percent;
  final bool estimated;

  @override
  Widget build(BuildContext context) {
    final progress =
        percent == null ? 0.0 : (1 - percent! / 100).clamp(0.0, 1.0).toDouble();
    final value = percent == null ? '-' : '${_percent(percent!)}%';

    return _AnalysisSurface(
      highlighted: true,
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const _SummaryTitle(
                  icon: Icons.bar_chart_rounded,
                  label: '종합 게임력',
                ),
                const SizedBox(height: 24),
                Text(
                  percent == null
                      ? '계산 제외'
                      : '상위 ${estimated ? '약 ' : ''}${_percent(percent!)}%',
                  style: const TextStyle(
                    fontSize: 21,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 7),
                Text(
                  percent == null
                      ? '비교 가능한 경쟁 기록이 없습니다.'
                      : '전체 유저 중 상위 ${_percent(percent!)}%의 게임 실력을 보유하고 있어요.',
                  style: const TextStyle(
                    color: Color(0xFF8997AD),
                    fontSize: 12,
                    height: 1.45,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          SizedBox.square(
            dimension: 94,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CircularProgressIndicator(
                  value: progress,
                  strokeWidth: 11,
                  strokeCap: StrokeCap.round,
                  backgroundColor: const Color(0xFF20304D),
                  valueColor: const AlwaysStoppedAnimation<Color>(
                    Color(0xFF7B71FF),
                  ),
                ),
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 18,
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
}

class _StrongestGameCard extends StatelessWidget {
  const _StrongestGameCard({required this.entry});

  final GameIdentityPreviewEntry? entry;

  @override
  Widget build(BuildContext context) => _AnalysisSurface(
        highlighted: true,
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _SummaryTitle(
              icon: Icons.emoji_events_rounded,
              label: '강점 게임',
              iconColor: Color(0xFFFFC84A),
            ),
            const SizedBox(height: 22),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        entry?.gameType.displayName ?? '-',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        entry?.topPercent == null
                            ? '비교 가능한 게임이 없습니다.'
                            : '상위 ${entry!.estimated ? '약 ' : ''}${_percent(entry!.topPercent!)}%',
                        style: const TextStyle(
                          color: Color(0xFF9D7CFF),
                          fontSize: 20,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        '가장 높은 게임력을 보여준 게임이에요.',
                        style: TextStyle(
                          color: Color(0xFF8997AD),
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                if (entry != null) ...[
                  const SizedBox(width: 12),
                  _GameIcon(gameType: entry!.gameType, size: 82),
                ],
              ],
            ),
          ],
        ),
      );
}

class _AnalysisOverviewCard extends StatelessWidget {
  const _AnalysisOverviewCard({
    required this.totalCount,
    required this.withinTwentyCount,
    required this.includedCount,
    required this.excludedCount,
  });

  final int totalCount;
  final int withinTwentyCount;
  final int includedCount;
  final int excludedCount;

  @override
  Widget build(BuildContext context) => _AnalysisSurface(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _SummaryTitle(
              icon: Icons.lightbulb_rounded,
              label: '분석 한눈에 보기',
            ),
            const SizedBox(height: 18),
            _OverviewRow(
              icon: Icons.sports_esports_rounded,
              label: '등록된 경쟁 게임',
              value: '$totalCount개',
            ),
            _OverviewRow(
              icon: Icons.emoji_events_rounded,
              label: '상위 20% 이내 게임',
              value: '$withinTwentyCount개',
            ),
            _OverviewRow(
              icon: Icons.group_rounded,
              label: '평균 포함 게임',
              value: '$includedCount개',
            ),
            _OverviewRow(
              icon: Icons.do_not_disturb_alt_rounded,
              label: '평균 제외 게임',
              value: '$excludedCount개',
              bottomPadding: 0,
            ),
          ],
        ),
      );
}

class _GameInsightCard extends StatelessWidget {
  const _GameInsightCard({
    required this.message,
    required this.averageText,
    required this.includedCount,
    required this.withinTwentyCount,
  });

  final String message;
  final String averageText;
  final int includedCount;
  final int withinTwentyCount;

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF221A5A), Color(0xFF111D3A)],
          ),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: const Color(0xFF6F58DD)),
          boxShadow: const [
            BoxShadow(color: Color(0x221F64FF), blurRadius: 18),
          ],
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.auto_awesome_rounded,
                color: Color(0xFFA48EFF), size: 30),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'GAME INSIGHT',
                    style: TextStyle(
                      color: Color(0xFFAFA2FF),
                      fontSize: 12,
                      fontWeight: FontWeight.w900,
                      letterSpacing: .7,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    message,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      height: 1.5,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '평균 게임력은 $averageText이며, $includedCount개 경쟁 게임 중 '
                    '$withinTwentyCount개 게임이 상위 20% 이내입니다.',
                    style: const TextStyle(
                      color: Color(0xFFAAB4C8),
                      fontSize: 12,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
}

class _AnalysisSurface extends StatelessWidget {
  const _AnalysisSurface({
    required this.child,
    required this.padding,
    this.highlighted = false,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final bool highlighted;

  @override
  Widget build(BuildContext context) => Container(
        padding: padding,
        decoration: BoxDecoration(
          color: const Color(0xFF0D192B),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color:
                highlighted ? const Color(0xFF43327F) : const Color(0xFF263750),
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x22000000),
              blurRadius: 16,
              offset: Offset(0, 8),
            ),
          ],
        ),
        child: child,
      );
}

class _SummaryTitle extends StatelessWidget {
  const _SummaryTitle({
    required this.icon,
    required this.label,
    this.iconColor = const Color(0xFF957BFF),
  });

  final IconData icon;
  final String label;
  final Color iconColor;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(icon, color: iconColor, size: 23),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
            ),
          ),
        ],
      );
}

class _OverviewRow extends StatelessWidget {
  const _OverviewRow({
    required this.icon,
    required this.label,
    required this.value,
    this.bottomPadding = 12,
  });

  final IconData icon;
  final String label;
  final String value;
  final double bottomPadding;

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.only(bottom: bottomPadding),
        child: Row(
          children: [
            Icon(icon, color: const Color(0xFF8D9DC0), size: 17),
            const SizedBox(width: 10),
            Expanded(
              child: Text(label,
                  style:
                      const TextStyle(color: Color(0xFF9AA8BE), fontSize: 12)),
            ),
            Text(value,
                style:
                    const TextStyle(fontSize: 13, fontWeight: FontWeight.w900)),
          ],
        ),
      );
}

class _MetricRow extends StatelessWidget {
  const _MetricRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 92,
            child: Text(label,
                style: const TextStyle(color: Color(0xFF8290A4), fontSize: 12)),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: Color(0xFFE9ECF5),
                fontSize: 13,
                fontWeight: FontWeight.w600,
                height: 1.45,
              ),
            ),
          ),
        ],
      );
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({
    required this.included,
    required this.percentile,
    required this.estimated,
  });

  final bool included;
  final double? percentile;
  final bool estimated;

  @override
  Widget build(BuildContext context) {
    final exceptional = included && percentile != null && percentile! <= 1;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        gradient: included
            ? const LinearGradient(
                colors: [Color(0xFF4C2699), Color(0xFF6840C7)])
            : null,
        color: included ? null : const Color(0xFF1C2A40),
        borderRadius: BorderRadius.circular(30),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (exceptional) ...[
            const Icon(Icons.workspace_premium_rounded,
                color: Color(0xFFFFD369), size: 14),
            const SizedBox(width: 5),
          ],
          Text(
            included
                ? '상위 ${estimated ? '약 ' : ''}${_percent(percentile!)}%'
                : '평균 제외',
            style: TextStyle(
              color: included ? Colors.white : const Color(0xFFB3BED0),
              fontSize: 12,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _ExclusionNotice extends StatelessWidget {
  const _ExclusionNotice({required this.reason});

  final String reason;

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(13),
        decoration: BoxDecoration(
          color: const Color(0xFF132238),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFF2A3B56)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.info_rounded, color: Color(0xFF91A4C9), size: 19),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '$reason\n평균 계산에서 제외되었어요.',
                style: const TextStyle(
                  color: Color(0xFFAAB6C9),
                  fontSize: 12,
                  height: 1.45,
                ),
              ),
            ),
          ],
        ),
      );
}

class _GameIcon extends StatelessWidget {
  const _GameIcon({required this.gameType, required this.size});

  final GameType gameType;
  final double size;

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        padding: EdgeInsets.all(size * .2),
        decoration: BoxDecoration(
          color: const Color(0xFF12233A),
          borderRadius: BorderRadius.circular(size * .22),
          border: Border.all(color: const Color(0xFF293C59)),
        ),
        child: Image.asset(
          gameType.iconAsset,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => const Icon(
            Icons.sports_esports_rounded,
            color: Color(0xFF9B87FF),
          ),
        ),
      );
}

class _CountBadge extends StatelessWidget {
  const _CountBadge({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFF14233A),
          borderRadius: BorderRadius.circular(30),
          border: Border.all(color: const Color(0xFF2D4161)),
        ),
        child: Text(
          '총 $count개 게임',
          style: const TextStyle(
            color: Color(0xFFAFA2FF),
            fontSize: 11,
            fontWeight: FontWeight.w800,
          ),
        ),
      );
}

class _IdentityGameRow extends StatelessWidget {
  const _IdentityGameRow(
      {required this.name, required this.account, required this.metric});
  final String name, account, metric;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Row(children: [
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(name.replaceAll('_', ' '),
                    style: const TextStyle(
                        color: Color(0xFFA99BFF), fontWeight: FontWeight.w800)),
                if (account.isNotEmpty)
                  Text(account,
                      style: const TextStyle(color: Color(0xFF8997AD)))
              ])),
          Text(metric,
              style: const TextStyle(
                  color: Colors.white, fontWeight: FontWeight.w800))
        ]),
      );
}

class _PublicShell extends StatelessWidget {
  const _PublicShell({required this.title, required this.child});
  final String title;
  final Widget child;
  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
            title: Text(title),
            leading: Navigator.canPop(context) ? const BackButton() : null),
        body: Center(
            child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1180),
                child: SingleChildScrollView(
                    padding: const EdgeInsets.all(24), child: child))),
      );
}

class _EmptyMessage extends StatelessWidget {
  const _EmptyMessage(this.message);
  final String message;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.all(48),
      child: Center(child: Text(message, textAlign: TextAlign.center)));
}

String _percent(double value) => value == value.roundToDouble()
    ? value.toStringAsFixed(0)
    : value.toStringAsFixed(1);

Future<bool> copyPublicLink(String path) async {
  try {
    await Clipboard.setData(
        ClipboardData(text: Uri.base.resolve(path).toString()));
    return true;
  } catch (_) {
    return false;
  }
}
