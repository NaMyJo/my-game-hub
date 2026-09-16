import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/game_finder_page.dart';

void main() {
  void useDesktopViewport(WidgetTester tester) {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1200, 800);
    addTearDown(tester.view.reset);
  }

  testWidgets('admin entry is hidden from normal users', (tester) async {
    useDesktopViewport(tester);
    final scrollController = ScrollController();
    addTearDown(scrollController.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GameFinderPage(webScrollController: scrollController),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('관리'), findsNothing);
  });

  testWidgets('admin entry is shown only after backend admin check',
      (tester) async {
    useDesktopViewport(tester);
    var opened = false;
    final scrollController = ScrollController();
    addTearDown(scrollController.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GameFinderPage(
            isAdmin: true,
            onOpenAdmin: () => opened = true,
            webScrollController: scrollController,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('관리'), findsOneWidget);
    await tester.tap(find.text('관리'));
    expect(opened, isTrue);
  });
}
