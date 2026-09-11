import 'package:flutter/material.dart';

class BottomReachGlow extends StatefulWidget {
  const BottomReachGlow({
    super.key,
    required this.child,
  });

  final Widget child;

  @override
  State<BottomReachGlow> createState() => _BottomReachGlowState();
}

class _BottomReachGlowState extends State<BottomReachGlow>
    with SingleTickerProviderStateMixin {
  static const _bottomTolerance = 5.0;
  static const _rearmDistance = 24.0;

  late final AnimationController _controller;
  late final Animation<double> _opacity;
  var _armed = true;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 650),
    );
    _opacity = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: 0, end: 1).chain(
          CurveTween(curve: Curves.easeOutCubic),
        ),
        weight: 35,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 1, end: 0).chain(
          CurveTween(curve: Curves.easeInCubic),
        ),
        weight: 65,
      ),
    ]).animate(_controller);
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.depth != 0) return false;

    final metrics = notification.metrics;
    if (metrics.maxScrollExtent <= metrics.minScrollExtent) {
      _armed = true;
      return false;
    }

    if (metrics.extentAfter > _rearmDistance) {
      _armed = true;
      return false;
    }

    if (_armed && metrics.extentAfter <= _bottomTolerance) {
      _armed = false;
      _controller.forward(from: 0);
    }

    return false;
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final glowColor = isDark
        ? const Color(0x4D8D79FF)
        : const Color(0x38654ED6);

    return NotificationListener<ScrollNotification>(
      onNotification: _onScroll,
      child: Stack(
        fit: StackFit.expand,
        children: [
          widget.child,
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 36,
            child: IgnorePointer(
              child: FadeTransition(
                key: const ValueKey('bottom-reach-glow'),
                opacity: _opacity,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: RadialGradient(
                      center: Alignment.bottomCenter,
                      radius: 1.35,
                      colors: [
                        glowColor,
                        glowColor.withValues(alpha: 0),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
