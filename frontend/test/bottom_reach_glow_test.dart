import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/widgets/bottom_reach_glow.dart';

void main() {
  Widget testApp({required double contentHeight}) {
    return MaterialApp(
      home: Scaffold(
        body: BottomReachGlow(
          child: SingleChildScrollView(
            child: SizedBox(height: contentHeight),
          ),
        ),
      ),
    );
  }

  Animation<double> glowOpacity(WidgetTester tester) {
    return tester
        .widget<FadeTransition>(
          find.byKey(const ValueKey('bottom-reach-glow')),
        )
        .opacity;
  }

  Animation<double> topGlowOpacity(WidgetTester tester) {
    return tester
        .widget<FadeTransition>(
          find.byKey(const ValueKey('top-reach-glow')),
        )
        .opacity;
  }

  testWidgets('does not glow when content is not scrollable', (tester) async {
    await tester.pumpWidget(testApp(contentHeight: 100));

    expect(glowOpacity(tester).value, 0);
    expect(topGlowOpacity(tester).value, 0);
  });

  testWidgets('glows at top after scrolling away and returning',
      (tester) async {
    await tester.pumpWidget(testApp(contentHeight: 1400));

    await tester.drag(
        find.byType(SingleChildScrollView), const Offset(0, -200));
    await tester.pump();
    await tester.drag(find.byType(SingleChildScrollView), const Offset(0, 200));
    await tester.pump(const Duration(milliseconds: 120));

    expect(topGlowOpacity(tester).value, greaterThan(0));
  });

  testWidgets('glows once at bottom and rearms after scrolling up',
      (tester) async {
    await tester.pumpWidget(testApp(contentHeight: 1400));

    await tester.drag(
        find.byType(SingleChildScrollView), const Offset(0, -900));
    await tester.pump(const Duration(milliseconds: 120));
    expect(glowOpacity(tester).value, greaterThan(0));

    await tester.pump(const Duration(milliseconds: 650));
    expect(glowOpacity(tester).value, 0);

    await tester.drag(find.byType(SingleChildScrollView), const Offset(0, 100));
    await tester.pump();
    await tester.drag(
        find.byType(SingleChildScrollView), const Offset(0, -100));
    await tester.pump(const Duration(milliseconds: 120));
    expect(glowOpacity(tester).value, greaterThan(0));
  });
}
