import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/game_finder_page.dart';

void main() {
  testWidgets('admin entry is hidden from normal users', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: GameFinderPage())),
    );

    expect(find.text('관리'), findsNothing);
  });

  testWidgets('admin entry is shown only after backend admin check',
      (tester) async {
    var opened = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GameFinderPage(
            isAdmin: true,
            onOpenAdmin: () => opened = true,
          ),
        ),
      ),
    );

    expect(find.text('관리'), findsOneWidget);
    await tester.tap(find.text('관리'));
    expect(opened, isTrue);
  });
}
