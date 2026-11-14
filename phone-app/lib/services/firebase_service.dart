import 'dart:async';
import 'dart:io';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../data.dart';

/// Firebase 통합 서비스 레이어 (보호자 앱)
class FirebaseService {
  FirebaseService._();
  static final FirebaseService instance = FirebaseService._();

  final FirebaseDatabase _db = FirebaseDatabase.instance;
  final FirebaseStorage _storage = FirebaseStorage.instance;
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseMessaging _fcm = FirebaseMessaging.instance;

  bool _initialized = false;
  String _activeCarId = 'car_01'; // 기본 차량 ID

  String get activeCarId => _activeCarId;

  /// Firebase 초기화 및 익명 로그인 / FCM 설정
  Future<void> init() async {
    if (_initialized) return;
    try {
      await Firebase.initializeApp();
      // 익명 사용자 인증
      if (_auth.currentUser == null) {
        await _auth.signInAnonymously();
      }
      await setupFCM();
      _initialized = true;
    } catch (e) {
      // Firebase 설정 파일(google-services.json)이 없을 경우 오프라인 fallback 모드로 수월하게 동작
      print('Firebase init warning (using offline fallback if missing config): $e');
    }
  }

  /// 차량 QR / ID로 바인딩 (S-01, P-01, C-02)
  Future<bool> pairCar(String carId) async {
    _activeCarId = carId;
    if (!_initialized) return true;
    try {
      final ref = _db.ref('vehicles/$carId/info');
      await ref.set({
        'pairedAt': ServerValue.timestamp,
        'ownerUid': _auth.currentUser?.uid ?? 'anonymous',
      });
      return true;
    } catch (e) {
      print('Failed to pair car: $e');
      return false;
    }
  }

  /// 실시간 차량 상태 스트림 (S-02, P-04, C-09)
  Stream<CarStatus> streamCarStatus({String? carId}) {
    final id = carId ?? _activeCarId;
    if (!_initialized) {
      return Stream.value(demoStatus);
    }

    return _db.ref('vehicles/$id/status').onValue.map((event) {
      final snap = event.snapshot;
      if (!snap.exists || snap.value == null) return demoStatus;

      final data = Map<String, dynamic>.from(snap.value as Map);
      return CarStatus(
        temp: (data['temp'] as num?)?.toDouble() ?? 23.0,
        battery: (data['battery'] as num?)?.toInt() ?? 82,
        careHours: (data['careHours'] as num?)?.toInt() ?? 42,
        locked: (data['locked'] as bool?) ?? true,
        place: (data['place'] as String?) ?? '[주차 위치]',
      );
    }).handleError((_) => demoStatus);
  }

  /// 실시간 로그/이벤트 목록 스트림 (S-03, P-04, P-06)
  Stream<List<LogEntry>> streamLogs({String? carId}) {
    final id = carId ?? _activeCarId;
    if (!_initialized) {
      return Stream.value(demoLog);
    }

    return _db.ref('vehicles/$id/events').onValue.map((event) {
      final snap = event.snapshot;
      if (!snap.exists || snap.value == null) return demoLog;

      final List<LogEntry> list = [];
      final rawMap = Map<String, dynamic>.from(snap.value as Map);
      rawMap.forEach((key, val) {
        if (val is Map) {
          final m = Map<String, dynamic>.from(val);
          list.add(LogEntry(
            m['time'] ?? '',
            m['text'] ?? '',
            m['value'] ?? '',
            bark: m['bark'] == true,
          ));
        }
      });
      list.sort((a, b) => b.time.compareTo(a.time));
      return list.isEmpty ? demoLog : list;
    }).handleError((_) => demoLog);
  }

  /// 반려견 프로필 동기화 (P-02)
  Future<void> saveDogProfiles(List<Dog> dogList, {String? carId}) async {
    final id = carId ?? _activeCarId;
    if (!_initialized) return;

    try {
      final List<Map<String, dynamic>> serialized = dogList
          .map((d) => {
                'name': d.name,
                'breed': d.breed,
                'age': d.age,
                'tempMin': d.tempMin,
                'tempMax': d.tempMax,
                'anxious': d.anxious,
                'photoUrl': d.photo,
              })
          .toList();

      await _db.ref('vehicles/$id/profiles').set(serialized);
    } catch (e) {
      print('Failed to save profiles: $e');
    }
  }

  /// 원격 목소리 파일 업로드 및 차에서 즉시 재생 명령 (P-03, P-07, S-04)
  Future<bool> uploadAndTriggerVoice(File audioFile, {String? carId}) async {
    final id = carId ?? _activeCarId;
    if (!_initialized) return false;

    try {
      final fileName = 'voice_${DateTime.now().millisecondsSinceEpoch}.mp3';
      final storageRef = _storage.ref().child('voices/$id/$fileName');
      final uploadTask = await storageRef.putFile(audioFile);
      final downloadUrl = await uploadTask.ref.getDownloadURL();

      // 차량 원격 명령 전달 (S-05)
      await _db.ref('vehicles/$id/commands').push().set({
        'type': 'PLAY_VOICE',
        'voiceUrl': downloadUrl,
        'timestamp': ServerValue.timestamp,
        'executed': false,
      });

      return true;
    } catch (e) {
      print('Voice upload failed: $e');
      return false;
    }
  }

  /// 버튼 클릭으로 데모 목소리/등록된 목소리 즉시 재생 트리거 (P-07, S-05)
  Future<bool> triggerVoicePlayback({String? voiceUrl, String? carId}) async {
    final id = carId ?? _activeCarId;
    if (!_initialized) return true;

    try {
      await _db.ref('vehicles/$id/commands').push().set({
        'type': 'PLAY_VOICE',
        'voiceUrl': voiceUrl ?? 'default_owner_voice',
        'timestamp': ServerValue.timestamp,
        'executed': false,
      });
      return true;
    } catch (e) {
      print('Trigger voice failed: $e');
      return false;
    }
  }

  /// 원격 제어 명령 전송 (P-09, S-05)
  Future<void> sendRemoteCommand(String command, Map<String, dynamic> params, {String? carId}) async {
    final id = carId ?? _activeCarId;
    if (!_initialized) return;

    try {
      await _db.ref('vehicles/$id/commands').push().set({
        'type': command,
        'params': params,
        'timestamp': ServerValue.timestamp,
        'executed': false,
      });
    } catch (e) {
      print('Send remote command failed: $e');
    }
  }

  /// FCM 푸시 알림 설정 (P-05, P-08, S-03)
  Future<void> setupFCM() async {
    try {
      NotificationSettings settings = await _fcm.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.authorized) {
        String? token = await _fcm.getToken();
        if (token != null) {
          await _db.ref('vehicles/$_activeCarId/fcmTokens/$token').set(true);
        }
      }
    } catch (e) {
      print('FCM setup failed: $e');
    }
  }
}
