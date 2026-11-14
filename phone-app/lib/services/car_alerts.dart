import 'dart:async';

import 'package:firebase_database/firebase_database.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'firebase_service.dart';

/// 차가 올리는 기록(짖음·진정·위험)을 듣고 있다가 휴대폰 알림으로 띄운다.
// ponytail: 앱이 살아 있을 때만 온다. 앱이 꺼져도 받아야 하면 Cloud Functions + FCM 푸시로 (Blaze 요금제 필요)
class CarAlerts {
  CarAlerts._();
  static final CarAlerts instance = CarAlerts._();

  final _plugin = FlutterLocalNotificationsPlugin();
  StreamSubscription<DatabaseEvent>? _sub;

  /// 이번에 받은 알림 (최신이 앞). 홈의 종 버튼이 보여 준다
  final ValueNotifier<List<String>> received = ValueNotifier(const []);

  Future<void> init() async {
    await _plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(),
      ),
    );
    await _plugin
        .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();
    final service = FirebaseService.instance;
    service.pairedCar.addListener(_listen);
    _listen();
  }

  // 연결한 차가 바뀌면 그 차의 기록을 새로 듣는다. 지금 이후에 생긴 기록만
  void _listen() {
    _sub?.cancel();
    final carId = FirebaseService.instance.pairedCar.value;
    if (carId == null || !FirebaseService.instance.online) return;
    _sub = FirebaseDatabase.instance
        .ref('vehicles/$carId/events')
        .orderByChild('timestamp')
        .startAt(DateTime.now().millisecondsSinceEpoch)
        .onChildAdded
        .listen((e) {
      final v = e.snapshot.value;
      if (v is Map) _show(Map<String, dynamic>.from(v));
    });
  }

  void _show(Map<String, dynamic> e) {
    final text = '${e['text'] ?? ''}';
    final value = '${e['value'] ?? ''}';
    final String title, body;
    int id = 1; // 같은 종류는 한 알림을 고쳐 쓴다 (짖을 때마다 쌓이지 않게)
    bool urgent = false;
    if (e['type'] == 'danger') {
      title = '위험: $text';
      body = e['temp'] != null ? '실내 ${(e['temp'] as num).toStringAsFixed(1)}°C · 차를 확인해 주세요' : '차를 확인해 주세요';
      id = 2;
      urgent = true;
    } else if (e['bark'] == true) {
      title = '달이가 짖고 있어요';
      body = '차에서 조명·음악·목소리로 달래는 중이에요 · 짖음 $value';
    } else if (text == '진정 완료') {
      title = '달이가 진정했어요';
      body = '달래는 데 $value 걸렸어요';
      id = 3; // 짖음 알림은 남겨 둔다
    } else {
      return;
    }
    received.value = ['${e['time'] ?? ''}  $title', ...received.value];
    _plugin.show(
      id: id,
      title: title,
      body: body,
      notificationDetails: NotificationDetails(
        android: AndroidNotificationDetails(
          urgent ? 'danger' : 'bark',
          urgent ? '위험 알림' : '짖음 알림',
          importance: Importance.high,
          priority: Priority.high,
          onlyAlertOnce: !urgent, // 짖음 횟수만 바뀔 때는 다시 울리지 않는다
        ),
        iOS: const DarwinNotificationDetails(),
      ),
    );
  }
}
