import 'package:flutter/material.dart';

import '../models/game_finder_admin.dart';
import '../services/api_client.dart';
import '../services/game_finder_admin_repository.dart';

class GameFinderAdminPage extends StatefulWidget {
  const GameFinderAdminPage({super.key});

  @override
  State<GameFinderAdminPage> createState() => _GameFinderAdminPageState();
}

class _GameFinderAdminPageState extends State<GameFinderAdminPage> {
  GameFinderAdminStatus? _status;
  GameFinderAdminEnrichResult? _result;
  int _batchSize = 1;
  bool _loadingStatus = true;
  bool _running = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  Future<void> _loadStatus() async {
    if (mounted) setState(() => _loadingStatus = true);
    try {
      final value = await GameFinderAdminRepository.instance.status();
      if (mounted) setState(() => _status = value);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loadingStatus = false);
    }
  }

  Future<void> _runEnrichment() async {
    if (_running) return;
    setState(() {
      _running = true;
      _error = null;
    });
    try {
      final value =
          await GameFinderAdminRepository.instance.enrich(_batchSize);
      if (!mounted) return;
      setState(() => _result = value);
      await _loadStatus();
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } catch (_) {
      if (mounted) setState(() => _error = '네트워크 오류가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _running = false);
    }
  }

  String _message(ApiException error) {
    return switch (error.statusCode) {
      400 => '처리 개수 설정을 확인해주세요.',
      401 => '로그인 상태를 다시 확인해주세요.',
      403 => 'GAME FINDER 관리자 권한이 없습니다.',
      409 => '이미 GAME FINDER 데이터 보강 작업이 실행 중입니다.',
      _ => error.message,
    };
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final panel = isDark ? const Color(0xFF091322) : Colors.white;
    final border = isDark ? const Color(0xFF24344B) : const Color(0xFFDDE3EC);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 46,
              height: 46,
              decoration: BoxDecoration(
                color: const Color(0xFF6F5AE8).withValues(alpha: .16),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Icon(Icons.admin_panel_settings_outlined,
                  color: Color(0xFF8D79FF)),
            ),
            const SizedBox(width: 14),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('GAME FINDER ADMIN',
                      style:
                          TextStyle(fontSize: 26, fontWeight: FontWeight.w900)),
                  SizedBox(height: 3),
                  Text('현재 Web Service 안에서 catalog 상태를 확인하고 데이터를 보강합니다.'),
                ],
              ),
            ),
            IconButton(
              tooltip: '상태 새로고침',
              onPressed: _loadingStatus || _running ? null : _loadStatus,
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
        ),
        const SizedBox(height: 20),
        if (_error != null) ...[
          _messagePanel(_error!, isDark, error: true),
          const SizedBox(height: 14),
        ],
        if (_loadingStatus && _status == null)
          const Center(
              child: Padding(
            padding: EdgeInsets.all(40),
            child: CircularProgressIndicator(),
          ))
        else if (_status != null) ...[
          LayoutBuilder(builder: (context, constraints) {
            final columns = constraints.maxWidth >= 900 ? 4 : 2;
            return GridView.count(
              crossAxisCount: columns,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
              childAspectRatio: columns == 4 ? 2.3 : 1.8,
              children: [
                _metric('전체 게임', _status!.total, Icons.storage_rounded),
                _metric('활성', _status!.active, Icons.check_circle_outline),
                _metric('조회 불가', _status!.unavailable,
                    Icons.cloud_off_outlined),
                _metric('스토어 제거', _status!.removed,
                    Icons.remove_circle_outline),
              ],
            );
          }),
          const SizedBox(height: 14),
          LayoutBuilder(builder: (context, constraints) {
            final wide = constraints.maxWidth >= 760;
            final cards = [
              _statusCard('STEAM METADATA', _status!.metadata, panel, border),
              _statusCard('IGDB', _status!.igdb, panel, border),
            ];
            return wide
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: cards
                        .map((card) => Expanded(
                            child: Padding(
                                padding: const EdgeInsets.only(right: 12),
                                child: card)))
                        .toList())
                : Column(children: cards);
          }),
        ],
        const SizedBox(height: 18),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            color: panel,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('ENRICHMENT 실행',
                  style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
              const SizedBox(height: 6),
              const Text('첫 운영 검증은 1개로 시작하고 Render Memory peak를 확인하세요.'),
              const SizedBox(height: 18),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [1, 2, 5, 10]
                    .map((value) => ChoiceChip(
                          label: Text('$value개'),
                          selected: _batchSize == value,
                          onSelected: _running
                              ? null
                              : (_) => setState(() => _batchSize = value),
                        ))
                    .toList(),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: 210,
                child: DropdownButtonFormField<int>(
                  initialValue: const [20, 40].contains(_batchSize)
                      ? _batchSize
                      : null,
                  decoration: const InputDecoration(
                    labelText: '고급 batch 선택',
                    border: OutlineInputBorder(),
                  ),
                  items: const [20, 40]
                      .map((value) => DropdownMenuItem(
                            value: value,
                            child: Text('$value개'),
                          ))
                      .toList(),
                  onChanged: _running
                      ? null
                      : (value) {
                          if (value != null) setState(() => _batchSize = value);
                        },
                ),
              ),
              const SizedBox(height: 18),
              FilledButton.icon(
                onPressed: _running ? null : _runEnrichment,
                icon: _running
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.auto_awesome_rounded),
                label: Text(_running ? '게임 데이터를 불러오고 있습니다' : 'Enrichment 실행'),
              ),
            ],
          ),
        ),
        if (_result != null) ...[
          const SizedBox(height: 14),
          _resultPanel(_result!, panel, border),
        ],
      ],
    );
  }

  Widget _metric(String label, int value, IconData icon) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: const Color(0xFF6F5AE8).withValues(alpha: .10),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
              color: const Color(0xFF8D79FF).withValues(alpha: .24)),
        ),
        child: Row(children: [
          Icon(icon, color: const Color(0xFF8D79FF)),
          const SizedBox(width: 12),
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label, style: const TextStyle(color: Color(0xFF8794A8))),
            Text('$value',
                style:
                    const TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
          ]),
        ]),
      );

  Widget _statusCard(String title, GameFinderEnrichmentCounts counts,
          Color panel, Color border) =>
      Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: panel,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title,
              style:
                  const TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
          const SizedBox(height: 14),
          _line('성공', counts.success),
          _line('대기', counts.pending),
          _line('정보 없음', counts.notFound),
          _line('재시도 가능 실패', counts.retryableFailure),
          _line('영구 실패', counts.permanentFailure),
        ]),
      );

  Widget _line(String label, int value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text(label),
          Text('$value', style: const TextStyle(fontWeight: FontWeight.w800)),
        ]),
      );

  Widget _resultPanel(GameFinderAdminEnrichResult value, Color panel, Color border) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: panel,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('최근 실행 결과',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          _line('처리 게임', value.processed),
          _line('Metadata 성공', value.metadataSuccess),
          _line('IGDB 성공', value.igdbSuccess),
          _line('실패', value.failures),
          _line('처리 시간(ms)', value.durationMs),
          const SizedBox(height: 4),
          Text('처리 시간 ${(value.durationMs / 1000).toStringAsFixed(2)}초',
              style: const TextStyle(color: Color(0xFF8794A8))),
        ]),
      );

  Widget _messagePanel(String text, bool isDark, {required bool error}) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: (error ? Colors.red : Colors.green).withValues(alpha: .10),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Text(text),
      );
}
