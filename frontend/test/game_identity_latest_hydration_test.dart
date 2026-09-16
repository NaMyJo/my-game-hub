import 'dart:async';
import 'dart:convert';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/models/game_profile.dart';
import 'package:my_game_hub/screens/game_identity_page.dart';
import 'package:my_game_hub/services/api_client.dart';
import 'package:my_game_hub/utils/network_connection.dart';

void main() {
  const game = GameProfile(
    id: 1,
    type: GameType.lostArk,
    accountName: 'character',
    primaryLabel: '전투력',
    primaryValue: '1,000',
  );

  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized();
  });

  test('recognizes Firebase token refresh network failures', () {
    final error = FirebaseAuthException(
      code: 'network-request-failed',
    );

    expect(isNetworkException(error), isTrue);
  });

  Future<void> pumpPage(
    WidgetTester tester, {
    required List<GameProfile> games,
    required bool gamesLoaded,
    required Future<Map<String, dynamic>?> Function() loader,
    int latestReloadSignal = 0,
    VoidCallback? onLatestLoadFailed,
  }) async {
    tester.view.physicalSize = const Size(1200, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.dark(),
        home: Scaffold(
          body: SingleChildScrollView(
            child: GameIdentityPage(
              games: games,
              gamesLoaded: gamesLoaded,
              onAddGame: () async => null,
              onProfileApplied: (_) {},
              showHeader: false,
              latestIdentityLoader: loader,
              latestReloadSignal: latestReloadSignal,
              onLatestLoadFailed: onLatestLoadFailed,
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('hydrates when games are ready before latest snapshot',
      (tester) async {
    final latest = Completer<Map<String, dynamic>?>();

    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: () => latest.future,
    );

    latest.complete(_latestIdentity(selectedGameIds: const [1]));
    await tester.pump();

    expect(find.text('등록 게임 1개'), findsOneWidget);
  });

  testWidgets('waits to hydrate until gamesLoaded becomes true',
      (tester) async {
    var loadCount = 0;
    final latest = Completer<Map<String, dynamic>?>();

    Future<Map<String, dynamic>?> loader() {
      loadCount += 1;
      return latest.future;
    }

    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: false,
      loader: loader,
    );

    latest.complete(_latestIdentity(selectedGameIds: const [1]));
    await tester.pump();

    expect(find.text('최근 게임 신분증을 불러오는 중입니다.'), findsOneWidget);
    expect(find.text('등록 게임 0개'), findsNothing);

    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: loader,
    );
    await tester.pump();

    expect(loadCount, 1);
    expect(find.text('등록 게임 1개'), findsOneWidget);
  });

  testWidgets('handles a completed empty game list without crashing',
      (tester) async {
    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: true,
      loader: () async => _latestIdentity(selectedGameIds: const [1]),
    );
    await tester.pump();

    expect(find.text('등록 게임 0개'), findsOneWidget);
  });

  testWidgets('restores a custom-only identity count', (tester) async {
    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: true,
      loader: () async => _latestIdentity(
        selectedGameIds: const [],
        customGameCount: 1,
      ),
    );
    await tester.pump();

    expect(find.text('등록 게임 1개'), findsOneWidget);
  });

  testWidgets('restores a mixed identity count', (tester) async {
    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: () async => _latestIdentity(
        selectedGameIds: const [1],
        customGameCount: 1,
      ),
    );
    await tester.pump();

    expect(find.text('등록 게임 2개'), findsOneWidget);
  });

  testWidgets('keeps a network failure distinct from an empty identity',
      (tester) async {
    var failureNotifications = 0;

    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: false,
      loader: () async => throw TimeoutException('offline'),
      onLatestLoadFailed: () => failureNotifications += 1,
    );
    await tester.pump();

    expect(
      find.text(
        '인터넷 연결을 확인하고 있습니다.\n'
        '연결되면 자동으로 다시 불러옵니다.',
      ),
      findsOneWidget,
    );
    expect(find.text('아직 생성한 게임 신분증이 없습니다.'), findsNothing);
    expect(failureNotifications, 1);
  });

  testWidgets('does not schedule network polling for backend API errors',
      (tester) async {
    var failureNotifications = 0;

    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: false,
      loader: () async => throw const ApiException(
        'server error',
        statusCode: 500,
      ),
      onLatestLoadFailed: () => failureNotifications += 1,
    );
    await tester.pump();

    expect(
      find.text('최근 게임 신분증을 불러오지 못했습니다.'),
      findsOneWidget,
    );
    expect(failureNotifications, 0);
  });

  testWidgets('recovers after games load before the latest retry',
      (tester) async {
    var loadCount = 0;

    Future<Map<String, dynamic>?> loader() async {
      loadCount += 1;
      if (loadCount == 1) throw TimeoutException('offline');
      return _latestIdentity(selectedGameIds: const [1]);
    }

    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: false,
      loader: loader,
    );
    await tester.pump();

    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: loader,
      latestReloadSignal: 1,
    );
    await tester.pump();

    expect(loadCount, 2);
    expect(find.text('등록 게임 1개'), findsOneWidget);
  });

  testWidgets('waits for games when the latest retry recovers first',
      (tester) async {
    var loadCount = 0;

    Future<Map<String, dynamic>?> loader() async {
      loadCount += 1;
      if (loadCount == 1) throw TimeoutException('offline');
      return _latestIdentity(selectedGameIds: const [1]);
    }

    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: false,
      loader: loader,
    );
    await tester.pump();

    await pumpPage(
      tester,
      games: const [],
      gamesLoaded: false,
      loader: loader,
      latestReloadSignal: 1,
    );
    await tester.pump();

    expect(find.text('등록 게임 0개'), findsNothing);

    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: loader,
      latestReloadSignal: 1,
    );
    await tester.pump();

    expect(loadCount, 2);
    expect(find.text('등록 게임 1개'), findsOneWidget);
  });

  testWidgets('does not refetch an already loaded identity on recovery signals',
      (tester) async {
    var loadCount = 0;

    Future<Map<String, dynamic>?> loader() async {
      loadCount += 1;
      return _latestIdentity(selectedGameIds: const [1]);
    }

    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: loader,
    );
    await tester.pump();

    await pumpPage(
      tester,
      games: const [game],
      gamesLoaded: true,
      loader: loader,
      latestReloadSignal: 1,
    );
    await tester.pump();

    expect(loadCount, 1);
    expect(find.text('등록 게임 1개'), findsOneWidget);
  });
}

Map<String, dynamic> _latestIdentity({
  required List<int> selectedGameIds,
  int customGameCount = 0,
}) {
  final selectedGames = selectedGameIds
      .map(
        (id) => {
          'id': id,
          'gameType': 'LOST_ARK',
          'accountName': 'character',
          'metricLabel': '전투력',
          'metricValue': '1,000',
          'topPercent': null,
          'includedInAverage': false,
          'estimated': false,
          'exclusionReason': null,
        },
      )
      .toList();
  final customGames = List.generate(
    customGameCount,
    (index) => {
      'id': 'custom-$index',
      'gameName': 'Custom $index',
      'playInfo': '100시간',
    },
  );

  return {
    'identityNumber': '20260914-0000001',
    'displayName': 'The Gamer',
    'issuedDate': '2026.09.14',
    'snapshotJson': jsonEncode({
      'selectedGames': selectedGames,
      'customGames': customGames,
      'averageTopPercent': null,
      'displayName': 'The Gamer',
      'evaluationType': 'RPG_ONLY',
      'includedGameCount': 0,
      'evaluationMessage': '테스트 메시지',
      'hasCompetitiveGame': false,
      'hasRpgGame': selectedGameIds.isNotEmpty,
    }),
  };
}
