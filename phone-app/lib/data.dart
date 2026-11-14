// 반려견 프로필과 차량 상태.
// ponytail: 지금은 앱 안의 데모 값. Firebase 연결 후 vehicles/{carId}/status·events·profile 구독으로 교체
class Dog {
  Dog({required this.name, this.photo, this.breed = '', this.age = '', this.tempMin = 22, this.tempMax = 24, this.anxious = false});

  String name;
  String? photo;
  String breed;
  String age;
  int tempMin, tempMax;
  bool anxious;
  int voiceCount = 0;
}

class CarStatus {
  const CarStatus({required this.temp, required this.battery, required this.careHours, required this.locked, required this.place, this.state = 'off'});

  final double temp;
  final int battery;
  final int careHours;
  final bool locked;
  final String place;
  final String state; // 차가 올리는 펫케어 상태: off · calm · calming · danger
}

class LogEntry {
  const LogEntry(this.time, this.text, this.value, {this.bark = false});

  final String time, text, value;
  final bool bark;
}

final dogs = [Dog(name: '달이', photo: 'assets/dali.jpg', anxious: true)..voiceCount = 2];

const demoStatus = CarStatus(temp: 23.0, battery: 82, careHours: 42, locked: true, place: '[주차 위치]');

const demoLog = [
  LogEntry('14:15', '펫케어 시작', '23.0℃'),
  LogEntry('14:21', '짖음 감지 · 진정', '3회', bark: true),
  LogEntry('14:23', '진정 완료', '1분 40초'),
];

// 받침 유무에 맞는 조사: josa('달이', '은', '는') → 달이는, josa('별', '은', '는') → 별은
String josa(String name, String withBatchim, String without) {
  final last = name.codeUnitAt(name.length - 1);
  final batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
  return name + (batchim ? withBatchim : without);
}
