import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/models/game_finder_admin.dart';
import 'package:my_game_hub/models/game_finder_admin_game_catalog.dart';
import 'package:my_game_hub/screens/game_finder_admin_page.dart';
import 'package:my_game_hub/services/game_finder_admin_repository.dart';

void main() {
  testWidgets('catalog target is bounded, blocks duplicate click, and refreshes status',
      (tester) async {
    final repository = _FakeRepository();
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: GameFinderAdminPage(repository: repository)),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ChoiceChip, '1000'));
    await tester.tap(find.text('Catalog 확장 실행'));
    await tester.pump();

    expect(repository.requestedTargets, [1000]);
    expect(find.text('Catalog 확장 중'), findsOneWidget);

    repository.completeCatalog();
    await tester.pumpAndSettle();

    expect(repository.statusCalls, 2);
    expect(find.text('최근 Catalog 확장 결과'), findsOneWidget);
    expect(find.text('아직 목표에 도달하지 않았습니다. 다음 chunk를 계속 실행할 수 있습니다.'),
        findsOneWidget);
  });

  testWidgets('full catalog next page uses one sequential request', (tester) async {
    final repository = _FakeRepository();
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: GameFinderAdminPage(repository: repository)),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('다음 500개 수집'));
    await tester.pumpAndSettle();

    expect(repository.fullSyncCalls, 1);
    expect(find.text('최근 전체 Catalog 수집 결과'), findsOneWidget);
    expect(find.text('다음 최대 500개를 이어서 수집할 수 있습니다.'), findsOneWidget);
  });

  testWidgets('continuous enrichment stops when processed is zero', (tester) async {
    final repository = _FakeRepository();
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: GameFinderAdminPage(repository: repository)),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('연속 Enrichment'));
    await tester.pumpAndSettle(const Duration(seconds: 1));

    expect(repository.enrichCalls, 2);
    expect(find.text('현재 처리 가능한 후보가 없습니다.'), findsOneWidget);
  });
}

class _FakeRepository extends GameFinderAdminRepository {
  int statusCalls = 0;
  int fullSyncCalls = 0;
  int enrichCalls = 0;
  final requestedTargets = <int>[];
  final _catalog = Completer<GameFinderAdminCatalogExpandResult>();

  @override
  Future<GameFinderAdminStatus> status() async {
    statusCalls++;
    return const GameFinderAdminStatus(
      total: 102,
      active: 102,
      unavailable: 0,
      removed: 0,
      metadata: GameFinderEnrichmentCounts(
        pending: 23,
        success: 79,
        notFound: 0,
        retryableFailure: 0,
        permanentFailure: 0,
      ),
      igdb: GameFinderEnrichmentCounts(
        pending: 21,
        success: 80,
        notFound: 1,
        retryableFailure: 0,
        permanentFailure: 0,
      ),
      checkpoint: GameFinderCatalogCheckpoint(
        lastAppId: 0,
        lastSuccessfulSyncAt: null,
        status: 'NEW',
        hasFailure: false,
      ),
      fullCatalogSync: GameFinderFullCatalogSyncStatus(
        status: 'NEW',
        lastAppId: 0,
        discoveredCount: 0,
        lastSuccessfulRunAt: null,
        completed: false,
        hasFailure: false,
      ),
      gameOnlyCatalogSync: GameFinderFullCatalogSyncStatus(
        status: 'NEW',
        lastAppId: 0,
        discoveredCount: 0,
        lastSuccessfulRunAt: null,
        completed: false,
        hasFailure: false,
      ),
      remainingCandidates: 23,
      gameCatalogCount: 102,
      gameCount: 79,
      nonGameCount: 0,
      unclassifiedCount: 23,
    );
  }

  @override
  Future<GameFinderAdminCatalogExpandResult> expandCatalog(int targetTotal) {
    requestedTargets.add(targetTotal);
    return _catalog.future;
  }

  @override
  Future<GameFinderAdminFullCatalogSyncResult> syncNextFullCatalogPage() async {
    fullSyncCalls++;
    return const GameFinderAdminFullCatalogSyncResult(
      fetched: 500,
      newlySaved: 498,
      currentCatalogTotal: 600,
      lastAppId: 500,
      discoveredCount: 500,
      completed: false,
      durationMs: 500,
    );
  }

  @override
  Future<GameFinderAdminGameCatalogSyncResult> syncNextGameCatalogPage() async {
    return const GameFinderAdminGameCatalogSyncResult(
      fetched: 500,
      eligibleCatalogTotal: 500,
      lastAppId: 500,
      discoveredCount: 500,
      completed: false,
      durationMs: 100,
    );
  }

  @override
  Future<GameFinderAdminEnrichResult> enrich(int batchSize) async {
    enrichCalls++;
    return GameFinderAdminEnrichResult(
      requestedBatchSize: batchSize,
      processed: enrichCalls == 1 ? 1 : 0,
      metadataSuccess: enrichCalls == 1 ? 1 : 0,
      metadataNotFound: 0,
      metadataRetryableFailure: 0,
      metadataPermanentFailure: 0,
      igdbSuccess: 0,
      igdbNotFound: 0,
      igdbRetryableFailure: 0,
      igdbPermanentFailure: 0,
      durationMs: 10,
      hasMoreCandidates: enrichCalls == 1,
    );
  }

  void completeCatalog() {
    _catalog.complete(const GameFinderAdminCatalogExpandResult(
      fetched: 500,
      upserted: 500,
      newlySaved: 498,
      currentTotal: 600,
      targetTotal: 1000,
      targetReached: false,
      durationMs: 500,
    ));
  }
}
