import 'dart:math' as math;

import 'package:flutter/material.dart';

RangeValues sortedRange(double first, double second) =>
    RangeValues(math.min(first, second), math.max(first, second));

const double crossableRangeTrackHeight = 8;
const double crossableRangeSliderHeight = 32;
const Color crossableRangeDarkInactiveColor = Color(0xFF46556E);
const Color crossableRangeLightInactiveColor = Color(0xFFBCC6D5);

class CrossableRangeSlider extends StatefulWidget {
  const CrossableRangeSlider({
    super.key,
    required this.values,
    required this.min,
    required this.max,
    required this.divisions,
    required this.onChanged,
  });

  final RangeValues values;
  final double min;
  final double max;
  final int divisions;
  final ValueChanged<RangeValues> onChanged;

  @override
  State<CrossableRangeSlider> createState() => _CrossableRangeSliderState();
}

class _CrossableRangeSliderState extends State<CrossableRangeSlider> {
  bool _movingFirst = true;
  bool _dragging = false;
  late double _first;
  late double _second;

  @override
  void initState() {
    super.initState();
    _sync();
  }

  @override
  void didUpdateWidget(CrossableRangeSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_dragging && oldWidget.values != widget.values) _sync();
  }

  void _sync() {
    _first = widget.values.start;
    _second = widget.values.end;
  }

  double _value(double dx, double width) {
    final ratio = (dx / width).clamp(0.0, 1.0);
    final raw = widget.min + ratio * (widget.max - widget.min);
    final step = (widget.max - widget.min) / widget.divisions;
    return (widget.min + ((raw - widget.min) / step).round() * step)
        .clamp(widget.min, widget.max)
        .toDouble();
  }

  void _start(DragStartDetails details, double width) {
    _dragging = true;
    final target = _value(details.localPosition.dx, width);
    _movingFirst = (target - _first).abs() <= (target - _second).abs();
  }

  void _end() {
    _dragging = false;
    _sync();
  }

  void _update(DragUpdateDetails details, double width) {
    final value = _value(details.localPosition.dx, width);
    if (_movingFirst) {
      _first = value;
    } else {
      _second = value;
    }
    widget.onChanged(sortedRange(_first, _second));
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
        builder: (context, constraints) => Semantics(
          label: '범위 선택',
          value: '${widget.values.start.round()}에서 ${widget.values.end.round()}',
          child: MouseRegion(
            cursor: SystemMouseCursors.click,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onHorizontalDragStart: (event) =>
                  _start(event, constraints.maxWidth),
              onHorizontalDragUpdate: (event) =>
                  _update(event, constraints.maxWidth),
              onHorizontalDragEnd: (_) => _end(),
              onHorizontalDragCancel: _end,
              child: SizedBox(
                height: crossableRangeSliderHeight,
                child: CustomPaint(
                  key: const ValueKey('crossable-range-track'),
                  painter: _CrossableRangePainter(
                    values: widget.values,
                    min: widget.min,
                    max: widget.max,
                    color: const Color(0xFF806AFF),
                    inactiveColor:
                        Theme.of(context).brightness == Brightness.dark
                            ? crossableRangeDarkInactiveColor
                            : crossableRangeLightInactiveColor,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
}

class _CrossableRangePainter extends CustomPainter {
  const _CrossableRangePainter({
    required this.values,
    required this.min,
    required this.max,
    required this.color,
    required this.inactiveColor,
  });

  final RangeValues values;
  final double min;
  final double max;
  final Color color;
  final Color inactiveColor;

  @override
  void paint(Canvas canvas, Size size) {
    const radius = 10.0;
    final y = size.height / 2;
    final usable = size.width - radius * 2;
    double x(double value) => radius + (value - min) / (max - min) * usable;
    final start = x(values.start);
    final end = x(values.end);
    canvas.drawRRect(
      RRect.fromRectAndRadius(
          Rect.fromLTWH(radius, y - crossableRangeTrackHeight / 2, usable,
              crossableRangeTrackHeight),
          const Radius.circular(crossableRangeTrackHeight / 2)),
      Paint()..color = inactiveColor,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
          Rect.fromLTRB(start, y - crossableRangeTrackHeight / 2, end,
              y + crossableRangeTrackHeight / 2),
          const Radius.circular(crossableRangeTrackHeight / 2)),
      Paint()..color = color,
    );
    for (final position in [start, end]) {
      canvas.drawCircle(Offset(position, y), radius + 2,
          Paint()..color = color.withValues(alpha: .18));
      canvas.drawCircle(Offset(position, y), radius,
          Paint()..color = color);
      canvas.drawCircle(Offset(position, y), radius - 3,
          Paint()..color = Colors.white);
    }
  }

  @override
  bool shouldRepaint(_CrossableRangePainter oldDelegate) =>
      oldDelegate.values != values || oldDelegate.color != color ||
      oldDelegate.inactiveColor != inactiveColor;
}
