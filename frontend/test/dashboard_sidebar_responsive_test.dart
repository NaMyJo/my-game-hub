import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/dashboard_screen.dart';
import 'package:my_game_hub/theme/app_theme_controller.dart';

void main() {
  void useViewport(WidgetTester tester, Size size) {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = size;
    addTearDown(tester.view.reset);
  }

  Future<void> pumpSidebar(
    WidgetTester tester,
    Size size, {
    AppThemeMode themeMode = AppThemeMode.dark,
    VoidCallback? onToggleTheme,
    bool collapsed = false,
  }) async {
    useViewport(tester, size);
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.dark(),
        home: Scaffold(
          body: Row(
            children: [
              DashboardSidebar(
                user: null,
                currentPage: DashboardPage.dashboard,
                onDashboard: () {},
                onAddGame: () {},
                onDeleteGames: () {},
                dashboardMenuExpanded: true,
                onTools: () {},
                onSignOut: () {},
                onDeleteAccount: () {},
                deleteMode: false,
                collapsed: collapsed,
                onToggleCollapsed: () {},
                onGameIdentity: () {},
                onGameFinder: () {},
                onMyGamePicks: () {},
                isGameFinderAdmin: true,
                onGameFinderAdmin: () {},
                themeMode: themeMode,
                onToggleTheme: onToggleTheme ?? () {},
              ),
              const Expanded(child: SizedBox()),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  ScrollController menuController(WidgetTester tester) {
    final scrollView = tester.widget<CustomScrollView>(
      find.byKey(const ValueKey('desktop-sidebar-menu-scroll')),
    );
    return scrollView.controller!;
  }

  testWidgets('desktop sidebar does not overflow at normal height',
      (tester) async {
    await pumpSidebar(tester, const Size(1440, 900));

    expect(tester.takeException(), isNull);
    expect(menuController(tester).position.maxScrollExtent, 0);
    expect(find.byKey(const ValueKey('web-account-deletion')), findsOneWidget);
  });

  testWidgets('expanded sidebar exposes one three-mode cycle control',
      (tester) async {
    var taps = 0;
    await pumpSidebar(
      tester,
      const Size(1440, 900),
      themeMode: AppThemeMode.pink,
      onToggleTheme: () => taps++,
    );

    expect(find.text('Pink'), findsOneWidget);
    expect(find.textContaining('Dark'), findsOneWidget);
    expect(find.textContaining('Light'), findsOneWidget);
    expect(find.textContaining('Pink'), findsNWidgets(2));
    expect(find.text('모드 변경'), findsNothing);
    expect(find.byType(Switch), findsNothing);

    await tester.tap(find.byKey(const ValueKey('web-theme-current-mode')));
    expect(taps, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('collapsed sidebar keeps one mode icon cycle control',
      (tester) async {
    var taps = 0;
    await pumpSidebar(
      tester,
      const Size(1440, 900),
      themeMode: AppThemeMode.light,
      onToggleTheme: () => taps++,
      collapsed: true,
    );

    expect(find.byIcon(Icons.light_mode_rounded), findsOneWidget);
    expect(find.text('Light'), findsNothing);

    await tester.tap(find.byIcon(Icons.light_mode_rounded));
    expect(taps, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('desktop sidebar does not overflow at 1024x768', (tester) async {
    await pumpSidebar(tester, const Size(1024, 768));

    expect(tester.takeException(), isNull);
    expect(menuController(tester).position.maxScrollExtent, 0);
    expect(find.byKey(const ValueKey('web-account-deletion')), findsOneWidget);
  });

  testWidgets('desktop sidebar menu scrolls independently at low height',
      (tester) async {
    await pumpSidebar(tester, const Size(1024, 450));
    final controller = menuController(tester);

    expect(tester.takeException(), isNull);
    expect(controller.position.maxScrollExtent, greaterThan(0));
    expect(controller.offset, 0);

    await tester.ensureVisible(find.text('FINDER ADMIN'));
    await tester.pumpAndSettle();

    expect(controller.offset, greaterThan(0));
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('web-account-deletion')), findsOneWidget);
  });

  testWidgets('portrait width keeps the mobile dashboard policy',
      (tester) async {
    useViewport(tester, const Size(390, 844));

    expect(usesMobileDashboardLayout(tester.view.physicalSize.width), isTrue);
  });

  testWidgets('landscape phone uses a scrollable sidebar without overflow',
      (tester) async {
    const landscapeSize = Size(760, 360);
    expect(usesMobileDashboardLayout(landscapeSize.width), isFalse);

    await pumpSidebar(tester, landscapeSize);
    final controller = menuController(tester);

    expect(tester.takeException(), isNull);
    expect(controller.position.maxScrollExtent, greaterThan(0));

    for (final label in [
      '대시보드',
      '게임 카드 추가',
      '게임 카드 삭제',
      '도구 모음',
      '게임 신분증',
      'GAME FINDER',
      'My Game Picks',
      'FINDER ADMIN',
    ]) {
      await tester.ensureVisible(find.text(label));
      await tester.pump();
      expect(find.text(label), findsOneWidget);
    }

    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('web-account-deletion')), findsOneWidget);
  });
}
