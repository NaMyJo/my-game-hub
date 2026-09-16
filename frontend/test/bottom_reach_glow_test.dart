import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/widgets/bottom_reach_glow.dart';

void main() {
  void enableAnimations(WidgetTester tester) {
    tester.binding.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: false);
    addTearDown(
      tester.binding.platformDispatcher.clearAccessibilityFeaturesTestValue,
    );
  }

  Widget testApp({
    required double contentHeight,
    ScrollController? controller,
  }) {
    return MaterialApp(
      home: Scaffold(
        body: BottomReachGlow(
          child: SingleChildScrollView(
            controller: controller,
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
    enableAnimations(tester);
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      testApp(contentHeight: 1400, controller: controller),
    );

    controller.jumpTo(200);
    await tester.pump();
    controller.jumpTo(0);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 120));

    expect(topGlowOpacity(tester).value, greaterThan(0));
  });

  testWidgets('glows once at bottom and rearms after scrolling up',
      (tester) async {
    enableAnimations(tester);
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      testApp(contentHeight: 1400, controller: controller),
    );

    controller.jumpTo(controller.position.maxScrollExtent);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 120));
    expect(glowOpacity(tester).value, greaterThan(0));

    await tester.pump(const Duration(milliseconds: 650));
    expect(glowOpacity(tester).value, 0);

    controller.jumpTo(controller.position.maxScrollExtent - 100);
    await tester.pump();
    controller.jumpTo(controller.position.maxScrollExtent);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 120));
    expect(glowOpacity(tester).value, greaterThan(0));
  });
}
