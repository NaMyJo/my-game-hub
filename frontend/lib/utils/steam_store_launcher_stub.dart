import 'package:url_launcher/url_launcher.dart';

Future<bool> launchSteamStoreImpl(Uri storeUri) {
  return launchUrl(storeUri, mode: LaunchMode.externalApplication);
}
