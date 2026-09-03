import 'dart:async';

import 'package:flutter/material.dart';

import '../models/game_finder_admin.dart';
import '../models/game_finder_admin_game_catalog.dart';
import '../services/api_client.dart';
import '../services/game_finder_admin_repository.dart';

class GameFinderAdminPage extends StatefulWidget {
  const GameFinderAdminPage({super.key, this.repository});

  final GameFinderAdminRepository? repository;

  @override
  State<GameFinderAdminPage> createState() => _GameFinderAdminPageState();
}

class _GameFinderAdminPageState extends State<GameFinderAdminPage> {
  GameFinderAdminStatus? _status;
  GameFinderAdminEnrichResult? _result;
  GameFinderAdminStageEnrichResult? _metadataResult;
  GameFinderAdminStageEnrichResult? _igdbResult;
  GameFinderAdminMetadataVerifyResult? _metadataVerifyResult;
  GameFinderMetadataRunnerStatus? _metadataRunner;
  Timer? _metadataRunnerPoll;
  GameFinderAdminCatalogExpandResult? _catalogResult;
  GameFinderAdminFullCatalogSyncResult? _fullCatalogResult;
  GameFinderAdminGameCatalogSyncResult? _gameCatalogResult;
  int _batchSize = 1;
  int _targetTotal = 500;
  int _metadataVerifySampleSize = 100;
  String _metadataVerifyMode = 'RANDOM';
  bool _loadingStatus = true;
  bool _running = false;
  bool _continuousEnrichment = false;
  bool _stopEnrichmentRequested = false;
  bool _igdbRunning = false;
  bool _metadataVerifyRunning = false;
  bool _continuousIgdb = false;
  bool _stopIgdbRequested = false;
  bool _catalogRunning = false;
  bool _fullCatalogRunning = false;
  bool _continuousFullCatalog = false;
  bool _stopFullCatalogRequested = false;
  bool _gameCatalogRunning = false;
  bool _continuousGameCatalog = false;
  bool _stopGameCatalogRequested = false;
  String? _error;

  bool get _maintenanceRunning =>
      _running || _igdbRunning || _metadataVerifyRunning || _catalogRunning ||
      _fullCatalogRunning || _gameCatalogRunning;

  @override
  void dispose() {
    _metadataRunnerPoll?.cancel();
    _stopFullCatalogRequested = true;
    _stopEnrichmentRequested = true;
    _stopIgdbRequested = true;
    _stopGameCatalogRequested = true;
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _loadStatus();
    _loadMetadataRunnerStatus();
    _metadataRunnerPoll = Timer.periodic(const Duration(seconds: 5),
        (_) => _loadMetadataRunnerStatus(silent: true));
  }

  Future<void> _loadMetadataRunnerStatus({bool silent = false}) async {
    try {
      final value = await
          (widget.repository ?? GameFinderAdminRepository.instance)
              .metadataRunnerStatus();
      if (mounted) setState(() => _metadataRunner = value);
    } on ApiException catch (error) {
      if (!silent && mounted) setState(() => _error = _message(error));
    }
  }

  Future<void> _startMetadataRunner() async {
    try {
      final value = await
          (widget.repository ?? GameFinderAdminRepository.instance)
              .startMetadataRunner();
      if (mounted) {
        setState(() {
          _metadataRunner = value;
          _error = null;
        });
      }
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    }
  }

  Future<void> _stopMetadataRunner() async {
    try {
      final value = await
          (widget.repository ?? GameFinderAdminRepository.instance)
              .stopMetadataRunner();
      if (mounted) setState(() => _metadataRunner = value);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    }
  }

  Future<void> _loadStatus() async {
    if (mounted) setState(() => _loadingStatus = true);
    try {
      final value = await (widget.repository ?? GameFinderAdminRepository.instance).status();
      if (mounted) setState(() => _status = value);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loadingStatus = false);
    }
  }

  Future<void> _runEnrichment({bool continuous = false}) async {
    if (_maintenanceRunning) return;
    setState(() {
      _running = true;
      _continuousEnrichment = continuous;
      _stopEnrichmentRequested = false;
      _error = null;
    });
    try {
      do {
        final value = await (widget.repository ??
                GameFinderAdminRepository.instance)
            .enrichMetadata(_batchSize);
        if (!mounted) return;
        setState(() => _metadataResult = value);
        await _loadStatus();
        if (value.rateLimited) {
          setState(() => _error =
              'Steam 요청 제한이 감지되어 연속 실행을 중단했습니다. 잠시 후 다시 시도해 주세요.');
        }
        if (!continuous || value.rateLimited || !value.hasMoreCandidates || value.processed == 0 ||
            _stopEnrichmentRequested) break;
        await Future<void>.delayed(const Duration(milliseconds: 300));
      } while (mounted && !_stopEnrichmentRequested);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } catch (_) {
      if (mounted) setState(() => _error = '네트워크 오류가 발생했습니다.');
    } finally {
      if (mounted) {
        setState(() {
          _running = false;
          _continuousEnrichment = false;
        });
      }
    }
  }

  Future<void> _runIgdbEnrichment({bool continuous = false}) async {
    if (_maintenanceRunning) return;
    setState(() {
      _igdbRunning = true;
      _continuousIgdb = continuous;
      _stopIgdbRequested = false;
      _error = null;
    });
    try {
      do {
        final value = await (widget.repository ??
                GameFinderAdminRepository.instance)
            .enrichIgdb(_batchSize);
        if (!mounted) return;
        setState(() => _igdbResult = value);
        await _loadStatus();
        if (!continuous || !value.hasMoreCandidates || value.processed == 0 ||
            _stopIgdbRequested) break;
        await Future<void>.delayed(const Duration(milliseconds: 300));
      } while (mounted && !_stopIgdbRequested);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) {
        setState(() {
          _igdbRunning = false;
          _continuousIgdb = false;
        });
      }
    }
  }

  Future<void> _runMetadataVerification() async {
    if (_maintenanceRunning) return;
    setState(() {
      _metadataVerifyRunning = true;
      _error = null;
    });
    try {
      final value = await (widget.repository ??
              GameFinderAdminRepository.instance)
          .verifyMetadata(_metadataVerifySampleSize, _metadataVerifyMode);
      if (mounted) setState(() => _metadataVerifyResult = value);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } catch (_) {
      if (mounted) setState(() => _error = 'Metadata 정합성 검증 중 오류가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _metadataVerifyRunning = false);
    }
  }

  Future<void> _runCatalogExpand() async {
    if (_maintenanceRunning) return;
    setState(() {
      _catalogRunning = true;
      _error = null;
    });
    try {
      final value = await (widget.repository ?? GameFinderAdminRepository.instance)
          .expandCatalog(_targetTotal);
      if (!mounted) return;
      setState(() => _catalogResult = value);
      await _loadStatus();
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } catch (_) {
      if (mounted) setState(() => _error = 'Catalog 확장 중 오류가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _catalogRunning = false);
    }
  }

  Future<void> _runFullCatalog({required bool continuous}) async {
    if (_maintenanceRunning) return;
    setState(() {
      _fullCatalogRunning = true;
      _continuousFullCatalog = continuous;
      _stopFullCatalogRequested = false;
      _error = null;
    });
    try {
      do {
        final value = await (widget.repository ??
                GameFinderAdminRepository.instance)
            .syncNextFullCatalogPage();
        if (!mounted) return;
        setState(() => _fullCatalogResult = value);
        await _loadStatus();
        if (!continuous || value.completed || _stopFullCatalogRequested) break;
        await Future<void>.delayed(const Duration(milliseconds: 300));
      } while (mounted && !_stopFullCatalogRequested);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } catch (_) {
      if (mounted) setState(() => _error = '전체 Catalog 수집 중 오류가 발생했습니다.');
    } finally {
      if (mounted) {
        setState(() {
          _fullCatalogRunning = false;
          _continuousFullCatalog = false;
        });
      }
    }
  }

  Future<void> _runGameCatalog({required bool continuous}) async {
    if (_maintenanceRunning) return;
    setState(() {
      _gameCatalogRunning = true;
      _continuousGameCatalog = continuous;
      _stopGameCatalogRequested = false;
      _error = null;
    });
    try {
      do {
        final value = await (widget.repository ??
                GameFinderAdminRepository.instance)
            .syncNextGameCatalogPage();
        if (!mounted) return;
        setState(() => _gameCatalogResult = value);
        await _loadStatus();
        if (!continuous || value.completed || _stopGameCatalogRequested) break;
        await Future<void>.delayed(const Duration(milliseconds: 300));
      } while (mounted && !_stopGameCatalogRequested);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) {
        setState(() {
          _gameCatalogRunning = false;
          _continuousGameCatalog = false;
        });
      }
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
              onPressed: _loadingStatus || _maintenanceRunning
                  ? null
                  : _loadStatus,
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
                _metric('전체 Steam Apps', _status!.total, Icons.storage_rounded),
                _metric('Steam Games', _status!.gameCatalogCount,
                    Icons.sports_esports_rounded),
                _metric('Store 조회 불가', _status!.storeUnavailableCount,
                    Icons.cloud_off_outlined),
                _metric('GAME FINDER 사용 가능', _status!.finderEligibleCount,
                    Icons.check_circle_outline),
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
        _catalogPanel(panel, border),
        if (_catalogResult != null) ...[
          const SizedBox(height: 14),
          _catalogResultPanel(_catalogResult!, panel, border),
        ],
        const SizedBox(height: 18),
        _fullCatalogPanel(panel, border),
        if (_fullCatalogResult != null) ...[
          const SizedBox(height: 14),
          _fullCatalogResultPanel(_fullCatalogResult!, panel, border),
        ],
        const SizedBox(height: 18),
        _gameCatalogPanel(panel, border),
        if (_gameCatalogResult != null) ...[
          const SizedBox(height: 14),
          _gameCatalogResultPanel(_gameCatalogResult!, panel, border),
        ],
        const SizedBox(height: 18),
        const Text('STEAM METADATA',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        const SizedBox(height: 8),
        _metadataRunnerPanel(panel, border),
        const SizedBox(height: 14),
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
              if (_status != null) ...[
                const SizedBox(height: 12),
                Text('전체 대상 ${_status!.gameCatalogCount} · terminal 완료 '
                    '${_status!.metadataTerminalCount} '
                    '· 남은 후보 ${_status!.remainingMetadataCandidates}'),
                const SizedBox(height: 4),
                Text('게임 ${_status!.gameCount} · Non-game ${_status!.nonGameCount} '
                    '· 미분류 ${_status!.unclassifiedCount}'),
                const SizedBox(height: 4),
                Text('운영 설정: concurrency ${_status!.metadataConcurrency} '
                    '· 요청 시작 간격 ${_status!.metadataRequestDelayMs}ms'),
                if (_metadataEta() != null) ...[
                  const SizedBox(height: 4),
                  Text('최근 처리 속도 기준 예상 남은 시간: ${_metadataEta()}'),
                ],
                const SizedBox(height: 6),
                LinearProgressIndicator(
                  value: _status!.gameCatalogCount == 0
                      ? 0
                      : (_status!.metadataTerminalCount /
                              _status!.gameCatalogCount)
                          .clamp(0.0, 1.0)
                          .toDouble(),
                ),
              ],
              const SizedBox(height: 18),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [1, 2, 5, 10]
                    .map((value) => ChoiceChip(
                          label: Text('$value개'),
                          selected: _batchSize == value,
                          onSelected: _maintenanceRunning
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
                  onChanged: _maintenanceRunning
                      ? null
                      : (value) {
                          if (value != null) setState(() => _batchSize = value);
                        },
                ),
              ),
              const SizedBox(height: 18),
              Wrap(spacing: 10, runSpacing: 10, children: [
              FilledButton.icon(
                onPressed: _maintenanceRunning
                    ? null
                    : () => _runEnrichment(),
                icon: _running
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.auto_awesome_rounded),
                label: Text(_running ? '게임 데이터를 불러오고 있습니다' : 'Enrichment 실행'),
              ),
              ]),
            ],
          ),
        ),
        if (_metadataResult != null) ...[
          const SizedBox(height: 14),
          _stageResultPanel(_metadataResult!, panel, border),
        ],
        const SizedBox(height: 18),
        _metadataVerificationPanel(panel, border),
        const SizedBox(height: 18),
        _igdbEnrichmentPanel(panel, border),
        if (_igdbResult != null) ...[
          const SizedBox(height: 14),
          _stageResultPanel(_igdbResult!, panel, border),
        ],
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

  Widget _metadataRunnerPanel(Color panel, Color border) {
    final runner = _metadataRunner;
    final active = runner?.active ?? false;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: panel,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('METADATA RUNNER',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 6),
        Text(_metadataRunnerDescription(runner)),
        if (runner != null) ...[
          const SizedBox(height: 14),
          Wrap(spacing: 20, runSpacing: 8, children: [
            Text('처리 ${runner.processedCount}'),
            Text('SUCCESS ${runner.successCount}'),
            Text('NOT_FOUND ${runner.notFoundCount}'),
            Text('재시도 ${runner.retryableFailureCount}'),
            Text('남은 초기 대상 ${runner.remainingMetadataCandidates}'),
            Text('cooldown ${runner.cooldownRetryableCount}'),
          ]),
          if (runner.nextRunAt != null) ...[
            const SizedBox(height: 8),
            Text('다음 자동 실행: ${_runnerCountdown(runner.nextRunAt!)}'),
          ],
          if (runner.lastError?.isNotEmpty ?? false) ...[
            const SizedBox(height: 8),
            Text(runner.lastError!, style: const TextStyle(color: Colors.redAccent)),
          ],
        ],
        const SizedBox(height: 16),
        Wrap(spacing: 10, runSpacing: 10, children: [
          FilledButton.icon(
            onPressed: active || _maintenanceRunning ? null : _startMetadataRunner,
            icon: const Icon(Icons.play_arrow_rounded),
            label: const Text('START'),
          ),
          OutlinedButton.icon(
            onPressed: active ? _stopMetadataRunner : null,
            icon: const Icon(Icons.stop_rounded),
            label: const Text('STOP'),
          ),
          IconButton(
            tooltip: '새로고침',
            onPressed: () => _loadMetadataRunnerStatus(),
            icon: const Icon(Icons.refresh_rounded),
          ),
        ]),
      ]),
    );
  }

  String _metadataRunnerDescription(GameFinderMetadataRunnerStatus? runner) {
    switch (runner?.status) {
      case 'RUNNING': return 'Steam Metadata 연속 수집 중';
      case 'WAITING_RATE_LIMIT': return 'Steam 요청 제한 감지 · 서버에서 자동 재개 대기 중';
      case 'WAITING_RETRY': return '일시 실패 게임의 retry cooldown 대기 중';
      case 'STOP_REQUESTED': return '현재 batch 완료 후 중지 예정';
      case 'STOPPED': return 'Steam Metadata 연속 수집 중지됨';
      case 'COMPLETED': return '초기 Steam Metadata 수집 완료';
      case 'FAILED': return 'Metadata runner 실행 실패';
      default: return '브라우저를 닫아도 서버가 작은 batch 단위로 수집을 이어갑니다.';
    }
  }

  String _runnerCountdown(DateTime target) {
    final seconds = target.toUtc().difference(DateTime.now().toUtc()).inSeconds;
    if (seconds <= 0) return '곧 재개';
    final minutes = seconds ~/ 60;
    return '${minutes.toString().padLeft(2, '0')}:${(seconds % 60).toString().padLeft(2, '0')} 후';
  }

  Widget _catalogPanel(Color panel, Color border) {
    final current = _status?.total ?? 0;
    final progress = (_targetTotal == 0
        ? 0.0
        : (current / _targetTotal).clamp(0.0, 1.0)).toDouble();
    final remaining = (_targetTotal - current).clamp(0, _targetTotal);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: panel,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('CATALOG 관리',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 6),
        Text('현재 $current개 · 목표 $_targetTotal개 · 남은 목표 $remaining개'),
        const SizedBox(height: 12),
        LinearProgressIndicator(value: progress),
        const SizedBox(height: 6),
        Text('진행률 ${(progress * 100).toStringAsFixed(1)}%'),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [500, 1000, 5000, 10000]
              .map((value) => ChoiceChip(
                    label: Text('$value'),
                    selected: _targetTotal == value,
                    onSelected: _maintenanceRunning
                        ? null
                        : (_) => setState(() => _targetTotal = value),
                  ))
              .toList(),
        ),
        const SizedBox(height: 14),
        const Text('한 번 실행할 때 최대 500개만 처리합니다. 목표에 도달할 때까지 다시 실행할 수 있습니다.'),
        const SizedBox(height: 14),
        FilledButton.icon(
          onPressed: _maintenanceRunning ? null : _runCatalogExpand,
          icon: _catalogRunning
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.playlist_add_rounded),
          label: Text(_catalogRunning ? 'Catalog 확장 중' : 'Catalog 확장 실행'),
        ),
        if (_status != null) ...[
          const SizedBox(height: 12),
          Text('Checkpoint: ${_status!.checkpoint.status} · App ID ${_status!.checkpoint.lastAppId}'
              '${_status!.checkpoint.hasFailure ? ' · 최근 오류 있음' : ''}'),
        ],
      ]),
    );
  }

  Widget _catalogResultPanel(GameFinderAdminCatalogExpandResult value,
          Color panel, Color border) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: panel,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('최근 Catalog 확장 결과',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          _line('이번 조회', value.fetched),
          _line('UPSERT', value.upserted),
          _line('신규 저장', value.newlySaved),
          _line('현재 Catalog', value.currentTotal),
          _line('목표', value.targetTotal),
          _line('처리 시간(ms)', value.durationMs),
          const SizedBox(height: 6),
          Text(value.targetReached
              ? '목표에 도달했습니다.'
              : '아직 목표에 도달하지 않았습니다. 다음 chunk를 계속 실행할 수 있습니다.'),
        ]),
      );

  Widget _fullCatalogPanel(Color panel, Color border) {
    final status = _status?.fullCatalogSync;
    final lastRun = status?.lastSuccessfulRunAt?.toLocal().toString() ?? '-';
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: panel,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('전체 STEAM CATALOG',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 8),
        const Text('Steam 전체 개수를 미리 가정하지 않고, App ID 순서로 최대 500개씩 이어서 수집합니다.'),
        const SizedBox(height: 14),
        Text('현재 상태: ${status?.status ?? 'NEW'}'),
        Text('현재까지 발견: ${status?.discoveredCount ?? 0}'),
        Text('마지막 App ID: ${status?.lastAppId ?? 0}'),
        Text('마지막 실행: $lastRun'),
        Text('완료 여부: ${status?.completed == true ? '완료' : '진행 중/미시작'}'),
        if (status?.hasFailure == true) const Text('최근 실행 오류가 있습니다.'),
        const SizedBox(height: 16),
        Wrap(spacing: 10, runSpacing: 10, children: [
          FilledButton.icon(
            onPressed: _maintenanceRunning || status?.completed == true
                ? null
                : () => _runFullCatalog(continuous: false),
            icon: _fullCatalogRunning && !_continuousFullCatalog
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.skip_next_rounded),
            label: const Text('다음 500개 수집'),
          ),
          if (!_fullCatalogRunning)
            OutlinedButton.icon(
              onPressed: status?.completed == true || _maintenanceRunning
                  ? null
                  : () => _runFullCatalog(continuous: true),
              icon: const Icon(Icons.repeat_rounded),
              label: const Text('연속 수집'),
            )
          else if (_continuousFullCatalog)
            OutlinedButton.icon(
              onPressed: () => setState(() => _stopFullCatalogRequested = true),
              icon: const Icon(Icons.stop_circle_outlined),
              label: const Text('현재 요청 후 중지'),
            ),
        ]),
      ]),
    );
  }

  Widget _fullCatalogResultPanel(GameFinderAdminFullCatalogSyncResult value,
          Color panel, Color border) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: panel,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('최근 전체 Catalog 수집 결과',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          _line('이번 조회', value.fetched),
          _line('신규 저장', value.newlySaved),
          _line('DB Catalog 총량', value.currentCatalogTotal),
          _line('누적 발견', value.discoveredCount),
          _line('마지막 App ID', value.lastAppId),
          _line('처리 시간(ms)', value.durationMs),
          const SizedBox(height: 6),
          Text(value.completed
              ? 'Steam App catalog 전체 스캔이 완료됐습니다.'
              : '다음 최대 500개를 이어서 수집할 수 있습니다.'),
        ]),
      );

  Widget _gameCatalogPanel(Color panel, Color border) {
    final status = _status?.gameOnlyCatalogSync;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: panel,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('STEAM GAME-ONLY CATALOG',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 8),
        const Text('Steam 공식 game 필터 목록만 500개씩 수집합니다. 기존 전체 Catalog는 보존됩니다.'),
        const SizedBox(height: 12),
        Text('전체 Steam Apps: ${_status?.total ?? 0}'),
        Text('Steam Games: ${_status?.gameCatalogCount ?? 0}'),
        Text('누적 발견: ${status?.discoveredCount ?? 0} · 마지막 App ID: ${status?.lastAppId ?? 0}'),
        Text('상태: ${status?.status ?? 'NEW'} · ${status?.completed == true ? '완료' : '진행 가능'}'),
        const SizedBox(height: 14),
        Wrap(spacing: 10, runSpacing: 10, children: [
          FilledButton.icon(
            onPressed: _maintenanceRunning || status?.completed == true
                ? null
                : () => _runGameCatalog(continuous: false),
            icon: const Icon(Icons.sports_esports_rounded),
            label: const Text('다음 500개 게임 수집'),
          ),
          if (!_gameCatalogRunning)
            OutlinedButton.icon(
              onPressed: _maintenanceRunning || status?.completed == true
                  ? null
                  : () => _runGameCatalog(continuous: true),
              icon: const Icon(Icons.repeat_rounded),
              label: const Text('연속 수집'),
            )
          else if (_continuousGameCatalog)
            OutlinedButton.icon(
              onPressed: () => setState(() => _stopGameCatalogRequested = true),
              icon: const Icon(Icons.stop_circle_outlined),
              label: const Text('현재 요청 후 중지'),
            ),
        ]),
      ]),
    );
  }

  Widget _gameCatalogResultPanel(GameFinderAdminGameCatalogSyncResult value,
          Color panel, Color border) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: panel,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('최근 Game-only 수집 결과',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          _line('이번 조회', value.fetched),
          _line('Steam Games', value.eligibleCatalogTotal),
          _line('누적 발견', value.discoveredCount),
          _line('마지막 App ID', value.lastAppId),
          _line('처리 시간(ms)', value.durationMs),
        ]),
      );

  Widget _statusCard(String title, GameFinderEnrichmentCounts counts,
          Color panel, Color border) {
    final total = counts.pending + counts.success + counts.notFound +
        counts.retryableFailure + counts.permanentFailure;
    final completed = counts.success + counts.notFound + counts.permanentFailure;
    final progress = total == 0 ? 0.0 : completed / total;
    return Container(
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
          LinearProgressIndicator(value: progress),
          const SizedBox(height: 6),
          Text('$completed / $total · ${(progress * 100).toStringAsFixed(1)}%'),
          const SizedBox(height: 10),
          _line('성공', counts.success),
          _line('대기', counts.pending),
          _line('정보 없음', counts.notFound),
          _line('재시도 가능 실패', counts.retryableFailure),
          _line('영구 실패', counts.permanentFailure),
        ]),
      );
  }

  Widget _line(String label, int value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text(label),
          Text('$value', style: const TextStyle(fontWeight: FontWeight.w800)),
        ]),
      );

  Widget _metadataVerificationPanel(Color panel, Color border) {
    final result = _metadataVerifyResult;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: panel,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Steam Metadata 정합성 검증',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 7),
        const Text('저장된 Metadata를 Steam Store의 현재 응답과 표본 비교합니다. DB 데이터는 수정하지 않습니다.'),
        const SizedBox(height: 16),
        const Text('표본 크기', style: TextStyle(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [10, 50, 100, 200, 500]
              .map((value) => ChoiceChip(
                    label: Text('$value'),
                    selected: _metadataVerifySampleSize == value,
                    onSelected: _maintenanceRunning
                        ? null
                        : (_) => setState(
                            () => _metadataVerifySampleSize = value),
                  ))
              .toList(),
        ),
        const SizedBox(height: 14),
        const Text('선정 방식', style: TextStyle(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'RANDOM', label: Text('RANDOM')),
            ButtonSegment(value: 'RECENT', label: Text('RECENT')),
          ],
          selected: {_metadataVerifyMode},
          onSelectionChanged: _maintenanceRunning
              ? null
              : (value) =>
                  setState(() => _metadataVerifyMode = value.first),
        ),
        const SizedBox(height: 18),
        FilledButton.icon(
          onPressed: _maintenanceRunning ? null : _runMetadataVerification,
          icon: _metadataVerifyRunning
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.fact_check_outlined),
          label: Text(_metadataVerifyRunning ? '검증 중...' : '검증 시작'),
        ),
        if (result != null) ...[
          const SizedBox(height: 18),
          const Divider(),
          const SizedBox(height: 8),
          _line('표본', result.sampled),
          _line('일치', result.matched),
          _line('변경됨', result.changed),
          _line('심각한 불일치', result.criticalMismatch),
          _line('Store 조회 불가', result.storeUnavailable),
          _line('검증 오류', result.verificationError),
          const SizedBox(height: 6),
          const Text('변경됨은 가격·할인 등 정상적인 Steam 변경일 수 있습니다.',
              style: TextStyle(color: Color(0xFF8794A8))),
          if (result.criticalMismatch > 0) ...[
            const SizedBox(height: 14),
            const Text('심각한 불일치 상세',
                style: TextStyle(fontWeight: FontWeight.w900,
                    color: Colors.redAccent)),
            const SizedBox(height: 8),
            ...result.criticalDetails.map((value) => Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.red.withValues(alpha: .08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                        color: Colors.red.withValues(alpha: .22)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Steam App ID ${value.steamAppId}',
                          style: const TextStyle(fontWeight: FontWeight.w800)),
                      Text('DB: ${value.dbName}'),
                      Text('Steam 응답: ${value.responseAppId ?? '-'} · ${value.responseName ?? '-'}'),
                      Text('불일치 필드: ${value.mismatchedFields.join(', ')}'),
                    ],
                  ),
                )),
          ],
        ],
      ]),
    );
  }

  Widget _igdbEnrichmentPanel(Color panel, Color border) {
    final target = _status?.igdbTargetCount ?? 0;
    final completed = _status?.igdbTerminalCount ?? 0;
    final progress = target == 0 ? 0.0 : (completed / target).clamp(0.0, 1.0);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: panel,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('IGDB ENRICHMENT',
            style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 8),
        Text('대상 $target · terminal 완료 $completed · 남은 후보 ${_status?.remainingIgdbCandidates ?? 0}'),
        const SizedBox(height: 8),
        LinearProgressIndicator(value: progress),
        const SizedBox(height: 14),
        Wrap(spacing: 10, runSpacing: 10, children: [
          FilledButton.icon(
            onPressed: _maintenanceRunning ? null : () => _runIgdbEnrichment(),
            icon: const Icon(Icons.hub_outlined),
            label: const Text('IGDB 1회 batch'),
          ),
          if (!_igdbRunning)
            OutlinedButton.icon(
              onPressed: _maintenanceRunning
                  ? null
                  : () => _runIgdbEnrichment(continuous: true),
              icon: const Icon(Icons.repeat_rounded),
              label: const Text('연속 IGDB'),
            )
          else if (_continuousIgdb)
            OutlinedButton.icon(
              onPressed: () => setState(() => _stopIgdbRequested = true),
              icon: const Icon(Icons.stop_circle_outlined),
              label: const Text('현재 요청 후 중지'),
            ),
        ]),
      ]),
    );
  }

  Widget _stageResultPanel(GameFinderAdminStageEnrichResult value,
          Color panel, Color border) =>
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: panel,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('${value.stage.toUpperCase()} 최근 실행',
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 10),
          _line('처리', value.processed),
          _line('SUCCESS', value.success),
          _line('NOT_FOUND', value.notFound),
          _line('RETRYABLE_FAILURE', value.retryableFailure),
          _line('PERMANENT_FAILURE', value.permanentFailure),
          if (value.rateLimited)
            const Padding(
              padding: EdgeInsets.only(top: 8),
              child: Text('Steam 요청 제한 감지 · 연속 실행 중단',
                  style: TextStyle(color: Color(0xFFE2A93B), fontWeight: FontWeight.w700)),
            ),
          Text('처리 속도 ${value.itemsPerSecond.toStringAsFixed(2)} apps/s · ${value.durationMs}ms'),
        ]),
      );

  String? _metadataEta() {
    final result = _metadataResult;
    final remaining = _status?.remainingMetadataCandidates ?? 0;
    if (result == null || result.processed < 2 || result.itemsPerSecond <= 0 ||
        remaining <= 0) return null;
    final seconds = (remaining / result.itemsPerSecond).ceil();
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    return hours > 0 ? '${hours}시간 ${minutes}분' : '${minutes}분';
  }

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
          Text(value.hasMoreCandidates ? '처리 가능한 후보가 남아 있습니다.' : '현재 처리 가능한 후보가 없습니다.'),
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
