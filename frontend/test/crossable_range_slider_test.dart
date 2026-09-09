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
}
