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
        title: 'GAME POWER ANALYSIS',
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
    final strongest = included.isEmpty
        ? null
        : included.reduce(
            (a, b) => (a.topPercent ?? 101) <= (b.topPercent ?? 101) ? a : b);
    final withinTwenty =
        included.where((e) => (e.topPercent ?? 101) <= 20).length;
    final estimated = included.any((e) => e.estimated);

    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Wrap(spacing: 14, runSpacing: 14, children: [
        _HighlightCard(
          label: '종합 게임력',
          value: data.averageTopPercent == null
              ? '계산 제외'
              : '상위 ${estimated ? '약 ' : ''}${_percent(data.averageTopPercent!)}%',
        ),
        _HighlightCard(
          label: '강점 게임',
          value: strongest == null
              ? '-'
              : '${strongest.gameType.displayName} · 상위 ${_percent(strongest.topPercent!)}%',
        ),
      ]),
      const SizedBox(height: 18),
      Text(data.evaluationMessage,
          style: const TextStyle(fontSize: 15, height: 1.5)),
      if (included.isNotEmpty) ...[
        const SizedBox(height: 8),
        Text('${included.length}개 경쟁 게임 중 $withinTwenty개 게임이 상위 20% 이내입니다.'),
      ],
      const SizedBox(height: 24),
      const Text('게임별 분석',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
      const SizedBox(height: 12),
      ...data.games.map((entry) => _AnalysisGameCard(entry: entry)),
    ]);
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
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.only(bottom: 10),
        child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(children: [
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    Text(entry.gameType.displayName,
                        style: const TextStyle(fontWeight: FontWeight.w900)),
                    Text(entry.accountName),
                    Text('${entry.metricLabel} · ${entry.metricValue}'),
                  ])),
              Text(
                  entry.includedInAverage
                      ? '상위 ${entry.estimated ? '약 ' : ''}${_percent(entry.topPercent!)}%\n평균 포함'
                      : '평균 제외\n${entry.exclusionReason ?? '비교 가능한 기록 없음'}',
                  textAlign: TextAlign.right),
            ])),
      );
}

class _HighlightCard extends StatelessWidget {
  const _HighlightCard({required this.label, required this.value});
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Container(
        width: 300,
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainer,
            borderRadius: BorderRadius.circular(18)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label),
          const SizedBox(height: 6),
          Text(value,
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900))
        ]),
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
