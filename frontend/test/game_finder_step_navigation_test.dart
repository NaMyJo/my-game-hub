import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/screens/game_finder_page.dart';

void main() {
  testWidgets('추천 결과에서 방문한 취향/조건 단계로 이동할 수 있다', (tester) async {
    int selected = 3;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: GameFinderStepNavigation(
          currentStep: 3,
          maxVisitedStep: 3,
          onStepSelected: (value) => selected = value,
        ),
      ),
    ));

    await tester.tap(find.text('취향 게임'));
    expect(selected, 1);
    await tester.tap(find.text('탐색 범위'));
    expect(selected, 2);
  });

  testWidgets('아직 방문하지 않은 다음 단계는 비활성화된다', (tester) async {
    int selected = 1;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: GameFinderStepNavigation(
          currentStep: 1,
          maxVisitedStep: 1,
          onStepSelected: (value) => selected = value,
        ),
      ),
    ));

    await tester.tap(find.text('추천 결과'), warnIfMissed: false);
    expect(selected, 1);
  });
}
