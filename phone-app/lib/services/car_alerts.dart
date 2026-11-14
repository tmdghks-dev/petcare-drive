import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// 차 기록(짖음·낑낑·진정·위험)은 서버(functions/index.js)가 FCM 푸시로 보낸다.
/// 앱이 꺼져 있거나 뒤에 있으면 시스템이 알림을 띄우고, 화면에 떠 있을 때는 여기서 띄운다.
class CarAlerts {
  CarAlerts._();
  static final CarAlerts instance = CarAlerts._();

  final _plugin = FlutterLocalNotificationsPlugin();

  /// 이번에 받은 알림 (최신이 앞). 홈의 종 버튼이 보여 준다
  final ValueNotifier<List<String>> received = ValueNotifier(const []);

  static const _bark = AndroidNotificationChannel('bark', '짖음 알림', importance: Importance.high);
  static const _danger = AndroidNotificationChannel('danger', '위험 알림', importance: Importance.max);

  Future<void> init() async {
    await _plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(),
      ),
    );
    // 서버 푸시가 쓰는 채널을 미리 만든다 (앱이 꺼져 있을 때도 이 채널로 울린다)
    final android = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    await android?.createNotificationChannel(_bark);
    await android?.createNotificationChannel(_danger);
    android?.requestNotificationsPermission(); // 권한 창에 답할 때까지 앱 시작을 막지 않는다

    FirebaseMessaging.onMessage.listen(_show);
    FirebaseMessaging.onMessageOpenedApp.listen(_remember);
  }

  void _remember(RemoteMessage m) {
    final title = m.notification?.title;
    if (title != null) received.value = ['${m.data['time'] ?? ''}  $title', ...received.value];
  }

  void _show(RemoteMessage m) {
    final n = m.notification;
    if (n == null) return;
    _remember(m);
    final kind = m.data['kind'] ?? 'bark';
    final danger = kind == 'danger';
    _plugin.show(
      id: switch (kind) { 'danger' => 2, 'calm' => 3, _ => 1 }, // 짖음은 한 알림을 고쳐 쓴다
      title: n.title,
      body: n.body,
      notificationDetails: NotificationDetails(
        android: AndroidNotificationDetails(
          danger ? _danger.id : _bark.id,
          danger ? _danger.name : _bark.name,
          importance: Importance.high,
          priority: Priority.high,
          onlyAlertOnce: !danger, // 짖음 횟수만 바뀔 때는 다시 울리지 않는다
        ),
        iOS: const DarwinNotificationDetails(),
      ),
    );
  }
}
