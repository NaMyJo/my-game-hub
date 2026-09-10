import 'package:url_launcher/url_launcher.dart';
import 'package:web/web.dart' as web;

Future<bool> launchSteamStoreImpl(Uri storeUri) {
  final userAgent = web.window.navigator.userAgent.toLowerCase();
  final isKakaoTalkInAppBrowser = userAgent.contains('kakaotalk');

  return launchUrl(
    storeUri,
    webOnlyWindowName: isKakaoTalkInAppBrowser ? '_self' : '_blank',
  );
}
