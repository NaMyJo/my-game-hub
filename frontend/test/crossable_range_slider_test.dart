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

  test('direct ranges are ordered and clamp open-ended slider sentinels', () {
    expect(normalizeDirectRange(8, 4, 1, 15), const RangeValues(4, 8));
    expect(normalizeDirectRange(4, 30, 1, 15), const RangeValues(4, 15));
    expect(normalizeDirectRange(20000, 250000, 0, 100000),
        const RangeValues(20000, 100000));
  });

  testWidgets('player and price sliders render non-zero full-width tracks',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: ThemeData.dark(),
      home: Scaffold(
        body: SizedBox(
          width: 600,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CrossableRangeSlider(
                debugLabel: 'player',
                values: const RangeValues(1, 15),
                min: 1,
                max: 15,
                divisions: 14,
                onChanged: (_) {},
              ),
              CrossableRangeSlider(
                debugLabel: 'price',
                values: const RangeValues(0, 100000),
                min: 0,
                max: 100000,
                divisions: 100,
                onChanged: (_) {},
              ),
            ],
          ),
        ),
      ),
    ));

    expect(crossableRangeTrackHeight, 6);
    expect(crossableRangeDarkInactiveColor, isNot(const Color(0x00000000)));
    for (final label in ['player', 'price']) {
      final track = find.byKey(ValueKey('$label-track'));
      expect(track, findsOneWidget);
      expect(tester.getSize(track).width, 600);
      expect(tester.getSize(track).width, greaterThan(0));
    }
    expect(tester.takeException(), isNull);
  });
}
