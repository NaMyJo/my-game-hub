import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:my_game_hub/widgets/crossable_range_slider.dart';

void main() {
  test('player thumbs crossing always produces an ordered range', () {
    expect(sortedRange(4, 3), const RangeValues(3, 4));
    expect(sortedRange(8, 6), const RangeValues(6, 8));
  });

  test('price thumbs crossing always produces an ordered range', () {
    expect(sortedRange(20000, 10000), const RangeValues(10000, 20000));
    expect(sortedRange(50000, 40000), const RangeValues(40000, 50000));
  });

  testWidgets('dark web slider renders a visible full-width track',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: ThemeData.dark(),
      home: Scaffold(
        body: SizedBox(
          width: 600,
          child: CrossableRangeSlider(
            values: const RangeValues(4, 6),
            min: 1,
            max: 15,
            divisions: 14,
            onChanged: (_) {},
          ),
        ),
      ),
    ));

    expect(crossableRangeTrackHeight, 8);
    expect(crossableRangeDarkInactiveColor, isNot(const Color(0x00000000)));
    expect(find.byKey(const ValueKey('crossable-range-track')), findsOneWidget);
    expect(tester.getSize(find.byType(CrossableRangeSlider)).width, 600);
    expect(tester.takeException(), isNull);
  });
}
