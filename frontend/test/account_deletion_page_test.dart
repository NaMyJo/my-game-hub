import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/app.dart';
import 'package:my_game_hub/screens/account_deletion_page.dart';

void main() {
  test('account deletion path resolves before the authentication gate', () {
    final route = generateAppRoute(
      const RouteSettings(name: AccountDeletionPage.path),
    );

    expect(route, isA<MaterialPageRoute<void>>());
  });

  for (final size in <Size>[
    const Size(320, 700),
    const Size(1440, 900),
  ]) {
    testWidgets('account deletion page renders at ${size.width}px',
        (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        const MaterialApp(home: AccountDeletionPage()),
      );

      expect(find.text('MY GAME HUB 계정 삭제 안내'), findsOneWidget);
      expect(find.textContaining('마이페이지 → 계정 삭제 → 확인'), findsOneWidget);
      expect(find.textContaining('왼쪽 Sidebar 사용자 영역'), findsOneWidget);
      expect(find.textContaining('Firebase Authentication 계정'), findsOneWidget);
      expect(find.textContaining('audwhd1113@gmail.com'), findsOneWidget);
      expect(find.byType(SingleChildScrollView), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}
