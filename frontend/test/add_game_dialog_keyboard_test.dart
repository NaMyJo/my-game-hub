import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/widgets/add_game_dialog.dart';

void main() {
  testWidgets('키보드가 열린 모바일 화면에서 입력창과 버튼이 겹치지 않는다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(390, 700),
            viewInsets: EdgeInsets.only(bottom: 300),
          ),
          child: const Scaffold(
            body: AddGameDialog(),
          ),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 250));

    final accountField = find.byType(TextFormField).last;
    final cancelButton = find.byKey(const ValueKey('add-game-cancel'));
    final submitButton = find.byKey(const ValueKey('add-game-submit'));

    expect(accountField, findsOneWidget);
    expect(cancelButton, findsOneWidget);
    expect(submitButton, findsOneWidget);
    expect(
      tester.getBottomLeft(accountField).dy,
      lessThan(tester.getTopLeft(cancelButton).dy),
    );
    expect(
      tester.getBottomRight(submitButton).dx,
      lessThanOrEqualTo(390),
    );
    expect(tester.takeException(), isNull);
  });
}
