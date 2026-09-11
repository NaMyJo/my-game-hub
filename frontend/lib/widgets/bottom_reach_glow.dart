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
    with TickerProviderStateMixin {
  static const _edgeTolerance = 5.0;
  static const _rearmDistance = 24.0;

  late final AnimationController _topController;
  late final AnimationController _bottomController;
  late final Animation<double> _topOpacity;
  late final Animation<double> _bottomOpacity;
  var _topArmed = false;
  var _bottomArmed = true;

  @override
  void initState() {
    super.initState();
    _topController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 650),
    );
    _bottomController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 650),
    );
    final opacityTween = TweenSequence<double>([
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
    ]);
    _topOpacity = opacityTween.animate(_topController);
    _bottomOpacity = opacityTween.animate(_bottomController);
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.depth != 0) return false;

    final metrics = notification.metrics;
    if (metrics.maxScrollExtent <= metrics.minScrollExtent) {
      _topArmed = false;
      _bottomArmed = true;
      return false;
    }

    if (metrics.extentBefore > _rearmDistance) {
      _topArmed = true;
    }
    if (metrics.extentAfter > _rearmDistance) {
      _bottomArmed = true;
    }

    if (_topArmed && metrics.extentBefore <= _edgeTolerance) {
      _topArmed = false;
      _topController.forward(from: 0);
    }
    if (_bottomArmed && metrics.extentAfter <= _edgeTolerance) {
      _bottomArmed = false;
      _bottomController.forward(from: 0);
    }

    return false;
  }

  @override
  void dispose() {
    _topController.dispose();
    _bottomController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final glowColor =
        isDark ? const Color(0x4D8D79FF) : const Color(0x38654ED6);

    return NotificationListener<ScrollNotification>(
      onNotification: _onScroll,
      child: Stack(
        fit: StackFit.expand,
        children: [
          widget.child,
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            height: 36,
            child: IgnorePointer(
              child: FadeTransition(
                key: const ValueKey('top-reach-glow'),
                opacity: _topOpacity,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: RadialGradient(
                      center: Alignment.topCenter,
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
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 36,
            child: IgnorePointer(
              child: FadeTransition(
                key: const ValueKey('bottom-reach-glow'),
                opacity: _bottomOpacity,
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
