import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/dashboard_screen.dart';

void main() {
  testWidgets('mobile navigation has five equal tabs and no global FAB',
      (tester) async {
    var selected = DashboardPage.dashboard;

    await tester.pumpWidget(MaterialApp(
      theme: ThemeData.dark(),
      home: StatefulBuilder(builder: (context, setState) {
        void select(DashboardPage page) => setState(() => selected = page);
        return Scaffold(
          body: const SizedBox.expand(),
          bottomNavigationBar: MobileBottomBar(
            currentPage: selected,
            onDashboard: () => select(DashboardPage.dashboard),
            onTools: () => select(DashboardPage.tools),
            onGameIdentity: () => select(DashboardPage.gameIdentity),
            onGameFinder: () => select(DashboardPage.gameFinder),
            onMyPage: () => select(DashboardPage.myPage),
          ),
        );
      }),
    ));

    expect(find.byType(FloatingActionButton), findsNothing);
    for (final label in ['대시보드', '도구 모음', '게임 신분증', 'FINDER', '마이페이지']) {
      expect(find.text(label), findsOneWidget);
    }

    final expanded = tester.widgetList<Expanded>(
      find.descendant(
        of: find.byType(MobileBottomBar),
        matching: find.byType(Expanded),
      ),
    );
    expect(expanded.length, 5);
    expect(expanded.every((item) => item.flex == 1), isTrue);

    await tester.tap(find.text('마이페이지'));
    await tester.pump();
    expect(selected, DashboardPage.myPage);
  });

  testWidgets('dashboard add-game action keeps its labeled callback',
      (tester) async {
    var tapped = false;
    await tester.pumpWidget(MaterialApp(
      theme: ThemeData.dark(),
      home: Scaffold(
        body: MobileAddGameAction(onTap: () => tapped = true),
      ),
    ));

    expect(find.text('새 게임 / 계정 추가'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mobile-dashboard-add-game')));
    expect(tapped, isTrue);
  });
}
