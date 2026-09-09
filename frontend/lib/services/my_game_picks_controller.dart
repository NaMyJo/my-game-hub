import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

import '../models/my_game_pick.dart';
import 'my_game_picks_repository.dart';

class MyGamePicksController extends ChangeNotifier {
  MyGamePicksController._();
  static final instance = MyGamePicksController._();

  String? _uid;
  List<MyGamePick> _items = const [];
  Set<int> _pickedIds = {};
  final Set<int> _busyAppIds = {};
  bool _loading = false;
  String? _error;

  List<MyGamePick> get items => List.unmodifiable(_items);
  bool get loading => _loading;
  String? get error => _error;
  bool isPicked(int appId) => _pickedIds.contains(appId);
  bool isBusy(int appId) => _busyAppIds.contains(appId);

  Future<void> syncUser(User? user, {bool force = false}) async {
    if (user == null || user.isAnonymous) {
      clear();
      return;
    }
    if (!force && _uid == user.uid) return;
    _uid = user.uid;
    _loading = true;
    _error = null;
    _items = const [];
    _pickedIds = {};
    notifyListeners();
    try {
      _items = await MyGamePicksRepository.instance.list();
      _pickedIds = _items.map((item) => item.appId).toSet();
    } catch (error) {
      _error = error.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> toggle(int appId) async {
    if (_uid == null || _busyAppIds.contains(appId)) return;
    final wasPicked = isPicked(appId);
    final previous = _items;
    final previousIds = Set<int>.from(_pickedIds);
    _busyAppIds.add(appId);
    if (wasPicked) {
      _items = _items.where((item) => item.appId != appId).toList();
      _pickedIds.remove(appId);
    } else {
      _pickedIds.add(appId);
    }
    notifyListeners();
    try {
      if (wasPicked) {
        await MyGamePicksRepository.instance.remove(appId);
      } else {
        final added = await MyGamePicksRepository.instance.add(appId);
        _items = [added, ..._items.where((item) => item.appId != appId)];
      }
      _error = null;
    } catch (_) {
      _items = previous;
      _pickedIds = previousIds;
      rethrow;
    } finally {
      _busyAppIds.remove(appId);
      notifyListeners();
    }
  }

  void clear() {
    if (_uid == null && _items.isEmpty && !_loading) return;
    _uid = null;
    _items = const [];
    _pickedIds = {};
    _busyAppIds.clear();
    _loading = false;
    _error = null;
    notifyListeners();
  }
}
