import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/models/game_identity_preview.dart';
import 'package:my_game_hub/models/game_profile.dart';
import 'package:my_game_hub/screens/dashboard_screen.dart';
import 'package:my_game_hub/screens/public_pages.dart';

const _darkSurfacePrimary = Color(0xFFF4F1FF);

Color _effectiveTextColor(WidgetTester tester, Finder finder) {
  final element = finder.evaluate().single;
  final text = element.widget as Text;
  return DefaultTextStyle.of(element).style.merge(text.style).color!;
}

Widget _themedApp({
  required Brightness brightness,
  required Widget child,
}) {
  return MaterialApp(
    theme: ThemeData(brightness: brightness),
    home: Scaffold(
      body: SingleChildScrollView(child: child),
    ),
  );
}

const _analysisData = GameIdentityPreviewResult(
  displayName: 'The Gamer',
  averageTopPercent: 20.4,
  evaluationType: 'COMPETITIVE',
  evaluationMessage: '꾸준히 게임을 즐겨온 실력자시군요!',
  includedGameCount: 1,
  games: [
    GameIdentityPreviewEntry(
      gameAccountId: 1,
      gameType: GameType.battlegrounds,
      accountName: 'player',
      metricLabel: '랭크 티어',
      metricValue: 'Survivor 1',
      topPercent: 20.4,
      includedInAverage: true,
      estimated: false,
      exclusionReason: null,
    ),
  ],
);

void main() {
  testWidgets(
      'profile dialog dark surface keeps a light foreground in light mode',
      (tester) async {
    await tester.pumpWidget(
      _themedApp(
        brightness: Brightness.light,
        child: const DashboardProfileDialogForeground(
          child: Text('프로필 수정'),
        ),
      ),
    );

    expect(
      _effectiveTextColor(tester, find.text('프로필 수정')),
      _darkSurfacePrimary,
    );
  });

  testWidgets('profile dialog content scrolls when available height is small',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 440,
              height: 180,
              child: DashboardProfileDialogForeground(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: const [
                    Text('dialog-top'),
                    SizedBox(height: 360),
                    Text('dialog-bottom'),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    final topBefore = tester.getTopLeft(find.text('dialog-top')).dy;

    await tester.drag(
      find.byType(SingleChildScrollView),
      const Offset(0, -140),
    );
    await tester.pumpAndSettle();

    expect(tester.getTopLeft(find.text('dialog-top')).dy, lessThan(topBefore));
    expect(tester.takeException(), isNull);
  });

  test('profile edit outline paint changes without changing border width', () {
    final lightBorder = dashboardProfileEditBorder(false);
    final darkBorder = dashboardProfileEditBorder(true);

    expect(lightBorder.color, Colors.transparent);
    expect(darkBorder.color, const Color(0xFF393568));
    expect(lightBorder.width, darkBorder.width);
  });

  test('profile field muted colors improve only in light mode', () {
    expect(
      dashboardProfileFieldHintColor(false),
      const Color(0xFF8997AD),
    );
    expect(
      dashboardProfileFieldCounterColor(false),
      const Color(0xFF8997AD),
    );
    expect(
      dashboardProfileFieldHintColor(true),
      const Color(0xFF66758B),
    );
    expect(
      dashboardProfileFieldCounterColor(true),
      const Color(0xFF69778B),
    );
  });

  testWidgets('analysis cards use dark-surface foreground in light mode',
      (tester) async {
    await tester.pumpWidget(
      _themedApp(
        brightness: Brightness.light,
        child: const GamePowerAnalysisView(data: _analysisData),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      _effectiveTextColor(
        tester,
        find.text('The Gamer님의 게임 기록을 한눈에 정리했어요.'),
      ),
      isNot(_darkSurfacePrimary),
    );
    expect(
      _effectiveTextColor(tester, find.text('종합 게임력')),
      _darkSurfacePrimary,
    );
    expect(
      _effectiveTextColor(tester, find.text('상위 20.4%').first),
      _darkSurfacePrimary,
    );
    expect(
      _effectiveTextColor(
        tester,
        find.text('꾸준히 게임을 즐겨온 실력자시군요!'),
      ),
      _darkSurfacePrimary,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('analysis dark mode primary foreground is unchanged',
      (tester) async {
    await tester.pumpWidget(
      _themedApp(
        brightness: Brightness.dark,
        child: const GamePowerAnalysisView(data: _analysisData),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      _effectiveTextColor(tester, find.text('종합 게임력')),
      _darkSurfacePrimary,
    );
    expect(
      _effectiveTextColor(
        tester,
        find.text('꾸준히 게임을 즐겨온 실력자시군요!'),
      ),
      _darkSurfacePrimary,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('game taste report header replaces the old analysis title',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: const GamePowerAnalysisPage(initialData: _analysisData),
      ),
    );

    expect(find.text('게임 취향 리포트'), findsOneWidget);
    expect(find.text('대시보드 분석'), findsNothing);
    expect(
      find.text('The Gamer님의 게임 기록을 한눈에 정리했어요.'),
      findsOneWidget,
    );
    expect(
      find.text('등록된 경쟁 게임을 기준으로 계산한 결과입니다.'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('overall power text and 100 percent indicator never overlap',
      (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    addTearDown(tester.view.reset);

    final data = GameIdentityPreviewResult(
      displayName: 'The Gamer',
      averageTopPercent: 100,
      evaluationType: 'COMPETITIVE',
      evaluationMessage: '기록을 확인했어요.',
      includedGameCount: 1,
      games: _analysisData.games,
    );
    await tester.pumpWidget(
      _themedApp(
        brightness: Brightness.dark,
        child: GamePowerAnalysisView(data: data),
      ),
    );
    await tester.pumpAndSettle();

    final textRegion = tester.getRect(
      find.byKey(const ValueKey('overall-power-text-region')),
    );
    final indicatorRegion = tester.getRect(
      find.byKey(const ValueKey('overall-power-indicator-region')),
    );
    expect(textRegion.right, lessThanOrEqualTo(indicatorRegion.left));
    expect(indicatorRegion.size, const Size.square(94));
    expect(find.text('상위 100%'), findsOneWidget);
    expect(find.text('100%'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
