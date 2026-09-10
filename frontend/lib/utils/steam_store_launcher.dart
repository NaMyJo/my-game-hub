import 'steam_store_launcher_stub.dart'
    if (dart.library.js_interop) 'steam_store_launcher_web.dart';

Future<bool> launchSteamStore(String storeUrl) {
  return launchSteamStoreImpl(Uri.parse(storeUrl));
}
