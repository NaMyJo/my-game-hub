import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/app.dart';
import 'package:my_game_hub/screens/dashboard_screen.dart';

void main() {
  void useMobileViewport(WidgetTester tester) {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.reset);
  }

  ThemeData lightTheme() => ThemeData(
        brightness: Brightness.light,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6750D8),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      );

  Future<void> pumpTools(
    WidgetTester tester,
    ThemeData theme,
  ) async {
    useMobileViewport(tester);
    await tester.pumpWidget(
      MaterialApp(
        theme: theme,
        home: const Scaffold(
          body: SingleChildScrollView(
            padding: EdgeInsets.all(14),
            child: ToolsPage(),
          ),
        ),
      ),
    );
  }

  Color cardColor(WidgetTester tester, String title) {
    final container = tester.widget<Container>(
      find.byKey(ValueKey<String>('mobile-tool-card-$title')),
    );
    return (container.decoration! as BoxDecoration).color!;
  }

  testWidgets('dark tools accordion keeps its existing surface and expands',
      (tester) async {
    await pumpTools(tester, ThemeData.dark(useMaterial3: true));

    expect(cardColor(tester, '로스트아크'), const Color(0xFF101A2A));
    expect(find.text('로스트아크'), findsOneWidget);

    await tester.tap(find.text('로스트아크'));
    await tester.pumpAndSettle();

    expect(find.text('KLOA'), findsOneWidget);
    final toolTile = tester.widget<ListTile>(
      find.ancestor(
        of: find.text('KLOA'),
        matching: find.byType(ListTile),
      ),
    );
    expect(toolTile.onTap, isNotNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('light tools accordion consumes light color scheme',
      (tester) async {
    final theme = lightTheme();
    await pumpTools(tester, theme);

    expect(cardColor(tester, '로스트아크'), theme.colorScheme.surfaceContainer);
    expect(cardColor(tester, '로스트아크'), isNot(const Color(0xFF101A2A)));
    expect(find.text('로스트아크'), findsOneWidget);

    await tester.tap(find.text('로스트아크'));
    await tester.pumpAndSettle();

    expect(find.text('KLOA'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('pink tools accordion consumes pink color scheme',
      (tester) async {
    final theme = buildPinkTheme();
    await pumpTools(tester, theme);

    expect(cardColor(tester, '로스트아크'), theme.colorScheme.surfaceContainer);
    expect(cardColor(tester, '로스트아크'), isNot(const Color(0xFF101A2A)));
    expect(
      cardColor(tester, '로스트아크'),
      isNot(lightTheme().colorScheme.surfaceContainer),
    );
    expect(find.text('로스트아크'), findsOneWidget);

    await tester.tap(find.text('로스트아크'));
    await tester.pumpAndSettle();

    expect(find.text('KLOA'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
