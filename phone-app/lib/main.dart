import 'dart:async';

import 'package:flutter/material.dart';
import 'data.dart';
import 'qr_scan_screen.dart';
import 'services/firebase_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FirebaseService.instance.init(); // 설정 파일이 없으면 오프라인으로 동작
  runApp(const PetCareApp());
}

const blue = Color(0xFF087DB5);
const background = Color(0xFFF1F4F6);
const ink = Color(0xFF202833);
const muted = Color(0xFF7C8793);

class PetCareApp extends StatelessWidget {
  const PetCareApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '펫케어 드라이브',
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: background,
        colorScheme: ColorScheme.fromSeed(seedColor: blue),
        fontFamily: 'Roboto',
      ),
      home: const MainScreen(),
    );
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int page = 0;
  double temperature = 22;
  Timer? sendTemp;

  // 슬라이더를 움직이는 동안은 모았다가 멈추면 한 번만 차로 보낸다
  void setTemperature(double value) {
    setState(() => temperature = value);
    sendTemp?.cancel();
    sendTemp = Timer(const Duration(milliseconds: 600), () {
      FirebaseService.instance.sendRemoteCommand('SET_TEMP', {'temp': double.parse(value.toStringAsFixed(1))});
    });
  }
  bool ventilation = false;

  final pages = const [
    '홈',
    '온도·환기',
    '목소리',
    '프로필',
  ];

  void goTo(int index) {
    setState(() => page = index);
  }

  void openQrScanner() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const QrScanScreen(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: background,
        title: const Text(
          '펫케어 드라이브',
          style: TextStyle(
            fontWeight: FontWeight.w800,
            color: ink,
            fontSize: 21,
          ),
        ),
        actions: [
          IconButton(
            tooltip: '알림',
            onPressed: () => showMessage('알림 화면은 준비 중이에요.'),
            icon: const Icon(Icons.notifications_none_rounded),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: IndexedStack(
          index: page,
          children: [
            // QR로 연결한 차가 있으면 차가 올리는 상태를 실시간으로 보여 준다
            ValueListenableBuilder<String?>(
              valueListenable: FirebaseService.instance.pairedCar,
              builder: (context, carId, _) => StreamBuilder<CarStatus>(
                stream: carId != null && FirebaseService.instance.online
                    ? FirebaseService.instance.streamCarStatus(carId: carId)
                    : null,
                builder: (context, snap) => HomePage(
                  temperature: temperature,
                  carCode: carId?.replaceFirst('car_', '').toUpperCase(),
                  live: snap.data,
                  onTemperature: () => goTo(1),
                  onVoice: () => goTo(2),
                  onProfile: () => goTo(3),
                  onAlarm: () => showMessage('아직 받은 알림이 없어요.'),
                  onQrConnect: openQrScanner,
                ),
              ),
            ),
            TemperaturePage(
              temperature: temperature,
              ventilation: ventilation,
              onTemperatureChanged: setTemperature,
              onVentilationChanged: (value) {
                setState(() => ventilation = value);
              },
              onBackHome: () => goTo(0),
            ),
            const VoicePage(),
            const ProfilePage(),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: page,
        onDestinationSelected: goTo,
        backgroundColor: Colors.white,
        indicatorColor: const Color(0xFFDCEFFC),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: '홈',
          ),
          NavigationDestination(
            icon: Icon(Icons.thermostat_rounded),
            label: '온도·환기',
          ),
          NavigationDestination(
            icon: Icon(Icons.mic_none_rounded),
            selectedIcon: Icon(Icons.mic_rounded),
            label: '목소리',
          ),
          NavigationDestination(
            icon: Icon(Icons.pets_outlined),
            selectedIcon: Icon(Icons.pets_rounded),
            label: '프로필',
          ),
        ],
      ),
    );
  }

  void showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}

class HomePage extends StatelessWidget {
  final double temperature;
  final String? carCode; // 연결한 차 코드. 연결 전이면 null
  final CarStatus? live; // 차에서 받은 실시간 상태. Firebase 연결 전이면 null
  final VoidCallback onTemperature;
  final VoidCallback onVoice;
  final VoidCallback onProfile;
  final VoidCallback onAlarm;
  final VoidCallback onQrConnect;

  const HomePage({
    super.key,
    required this.temperature,
    this.carCode,
    this.live,
    required this.onTemperature,
    required this.onVoice,
    required this.onProfile,
    required this.onAlarm,
    required this.onQrConnect,
  });

  static String stateLabel(String state) => switch (state) {
        'calm' => '펫케어 중',
        'calming' => '달래는 중',
        'danger' => '위험',
        _ => '펫케어 꺼짐',
      };

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFFE1F2FC), Color(0xFFF8FCFF)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(26),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '오늘도 함께, 달이와 🐾',
                      style: TextStyle(
                        fontSize: 21,
                        fontWeight: FontWeight.w800,
                        color: ink,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '달이의 상태를 확인해 보세요.',
                      style: TextStyle(color: muted, fontSize: 13),
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 7,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(30),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.circle, color: blue, size: 9),
                          const SizedBox(width: 6),
                          Text(
                            carCode == null ? '차량 연결 대기' : '차량 연결됨 · $carCode',
                            style: const TextStyle(
                              color: blue,
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                width: 100,
                height: 115,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(24),
                ),
                child: const Center(
                  child: Text('🐶', style: TextStyle(fontSize: 67)),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        SectionTitle(title: '달이의 현재 상태', subtitle: live == null ? '예시 화면' : '차에서 실시간으로 받아요'),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: InfoCard(
                icon: Icons.thermostat_rounded,
                iconColor: blue,
                title: '실내 온도',
                value: '${(live?.temp ?? temperature).toStringAsFixed(1)}°C',
                detail: live == null ? '예시 데이터' : '차 실내 온도',
                onTap: onTemperature,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: InfoCard(
                icon: Icons.directions_car_rounded,
                iconColor: const Color(0xFF32966F),
                title: '차량 상태',
                value: carCode == null ? '연결 대기' : live == null ? '연결됨' : stateLabel(live!.state),
                detail: carCode == null ? '차량 연결 필요' : '차 코드 $carCode',
                onTap: onAlarm,
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        const SectionTitle(title: '빠른 메뉴', subtitle: '원하는 기능을 선택하세요'),
        const SizedBox(height: 12),
        ActionTile(
          icon: Icons.qr_code_scanner_rounded,
          color: const Color(0xFF32966F),
          title: '자동차 QR 연결',
          subtitle: 'QR 코드를 스캔해 자동차 연결하기',
          onTap: onQrConnect,
        ),
        const SizedBox(height: 10),
        ActionTile(
          icon: Icons.thermostat_rounded,
          color: blue,
          title: '온도 · 환기 조절',
          subtitle: '온도 화면과 환기 설정 열기',
          onTap: onTemperature,
        ),
        const SizedBox(height: 10),
        ActionTile(
          icon: Icons.mic_rounded,
          color: const Color(0xFF8B72D9),
          title: '달이에게 목소리 들려주기',
          subtitle: '목소리 화면 열기',
          onTap: onVoice,
        ),
        const SizedBox(height: 10),
        ActionTile(
          icon: Icons.notifications_active_outlined,
          color: const Color(0xFFE49A42),
          title: '알림 확인',
          subtitle: '짖음 및 위험 알림 화면은 추후 연결',
          onTap: onAlarm,
        ),
        const SizedBox(height: 24),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            children: [
              const Icon(Icons.favorite_rounded, color: Color(0xFFE58D9B)),
              const SizedBox(width: 12),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '오늘도 안전하게!',
                      style: TextStyle(
                        color: ink,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      '차량 연결 후 상태를 확인할 수 있어요.',
                      style: TextStyle(color: muted, fontSize: 12),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: onProfile,
                icon: const Icon(Icons.chevron_right_rounded),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class TemperaturePage extends StatelessWidget {
  final double temperature;
  final bool ventilation;
  final ValueChanged<double> onTemperatureChanged;
  final ValueChanged<bool> onVentilationChanged;
  final VoidCallback onBackHome;

  const TemperaturePage({
    super.key,
    required this.temperature,
    required this.ventilation,
    required this.onTemperatureChanged,
    required this.onVentilationChanged,
    required this.onBackHome,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SectionTitle(
          title: '온도 · 환기',
          subtitle: '반려동물이 머무는 공간을 확인해요.',
        ),
        const SizedBox(height: 18),
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(28),
          ),
          child: Column(
            children: [
              const Icon(
                Icons.thermostat_rounded,
                size: 46,
                color: blue,
              ),
              const SizedBox(height: 8),
              const Text(
                '설정 목표 온도',
                style: TextStyle(color: muted),
              ),
              const SizedBox(height: 8),
              Text(
                '${temperature.toStringAsFixed(1)}°C',
                style: const TextStyle(
                  color: ink,
                  fontSize: 42,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  RoundButton(
                    icon: Icons.remove_rounded,
                    onTap: () => onTemperatureChanged(
                      (temperature - 0.5).clamp(18.0, 30.0),
                    ),
                  ),
                  const SizedBox(width: 24),
                  RoundButton(
                    icon: Icons.add_rounded,
                    onTap: () => onTemperatureChanged(
                      (temperature + 0.5).clamp(18.0, 30.0),
                    ),
                  ),
                ],
              ),
              Slider(
                value: temperature.clamp(18.0, 30.0),
                min: 18,
                max: 30,
                divisions: 24,
                activeColor: blue,
                onChanged: onTemperatureChanged,
              ),
              const Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('18°C', style: TextStyle(color: muted, fontSize: 12)),
                  Text('30°C', style: TextStyle(color: muted, fontSize: 12)),
                ],
              ),
              const SizedBox(height: 18),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: background,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.air_rounded, color: blue),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '환기 설정',
                            style: TextStyle(
                              color: ink,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          Text(
                            '화면 시연용 설정',
                            style: TextStyle(color: muted, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: ventilation,
                      activeTrackColor: blue,
                      onChanged: onVentilationChanged,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              Text(
                FirebaseService.instance.pairedCar.value == null
                    ? '차와 연결하면 설정 온도가 차 에어컨으로 바로 전달돼요. 환기는 아직 시연용이에요.'
                    : '설정 온도는 차 에어컨으로 바로 전달돼요. 환기는 창문 제어 승인 후 연결돼요.',
                style: TextStyle(color: muted, fontSize: 12, height: 1.5),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: onBackHome,
          icon: const Icon(Icons.home_outlined),
          label: const Text('홈으로 돌아가기'),
        ),
      ],
    );
  }
}

class VoicePage extends StatelessWidget {
  const VoicePage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SectionTitle(
          title: '목소리',
          subtitle: '달이에게 들려줄 목소리를 준비해요.',
        ),
        const SizedBox(height: 22),
        Container(
          padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 20),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(28),
          ),
          child: Column(
            children: [
              Container(
                width: 110,
                height: 110,
                decoration: const BoxDecoration(
                  color: Color(0xFFDCEFFC),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.mic_rounded,
                  color: blue,
                  size: 52,
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                '달이에게 목소리 들려주기',
                style: TextStyle(
                  color: ink,
                  fontSize: 19,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                '목소리 녹음 및 차량 전송은 추후 연결할 예정이에요.',
                textAlign: TextAlign.center,
                style: TextStyle(color: muted, height: 1.5),
              ),
              const SizedBox(height: 22),
              FilledButton.icon(
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('음성 녹음 기능은 아직 연결되지 않았어요.'),
                    ),
                  );
                },
                icon: const Icon(Icons.mic_none_rounded),
                label: const Text('녹음 기능 (준비 중)'),
                style: FilledButton.styleFrom(
                  backgroundColor: blue,
                  minimumSize: const Size(double.infinity, 50),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        const SectionTitle(title: '저장된 목소리', subtitle: '예시 목록'),
        const SizedBox(height: 10),
        const VoiceItem(title: '달이야, 괜찮아', duration: '00:05'),
        const VoiceItem(title: '금방 올게', duration: '00:04'),
        const VoiceItem(title: '착하지, 달이야', duration: '00:03'),
      ],
    );
  }
}

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SectionTitle(
          title: '프로필',
          subtitle: '반려동물 정보를 확인해요.',
        ),
        const SizedBox(height: 18),
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(26),
          ),
          child: Column(
            children: [
              Container(
                width: 96,
                height: 96,
                decoration: const BoxDecoration(
                  color: Color(0xFFE1F2FC),
                  shape: BoxShape.circle,
                ),
                child: const Center(
                  child: Text('🐶', style: TextStyle(fontSize: 58)),
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                '달이',
                style: TextStyle(
                  color: ink,
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 5),
              const Text(
                '우리 가족 반려동물',
                style: TextStyle(color: muted),
              ),
              const SizedBox(height: 24),
              const ProfileRow(label: '이름', value: '달이'),
              const ProfileRow(label: '견종', value: '입력 예정'),
              const ProfileRow(label: '나이', value: '입력 예정'),
              const ProfileRow(label: '적정 온도', value: '22–24°C'),
              const ProfileRow(label: '차량 연결', value: '연결 대기'),
            ],
          ),
        ),
        const SizedBox(height: 18),
        const Text(
          '※ 프로필 정보는 디자인 확인을 위한 예시입니다.',
          style: TextStyle(color: muted, fontSize: 12),
        ),
      ],
    );
  }
}

class SectionTitle extends StatelessWidget {
  final String title;
  final String subtitle;

  const SectionTitle({
    super.key,
    required this.title,
    required this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            color: ink,
            fontSize: 19,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 5),
        Text(
          subtitle,
          style: const TextStyle(color: muted, fontSize: 12),
        ),
      ],
    );
  }
}

class InfoCard extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final String value;
  final String detail;
  final VoidCallback onTap;

  const InfoCard({
    super.key,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.value,
    required this.detail,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: iconColor, size: 25),
              const SizedBox(height: 14),
              Text(title, style: const TextStyle(color: muted, fontSize: 12)),
              const SizedBox(height: 6),
              FittedBox(
                fit: BoxFit.scaleDown,
                alignment: Alignment.centerLeft,
                child: Text(
                  value,
                  style: const TextStyle(
                    color: ink,
                    fontSize: 21,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                detail,
                style: const TextStyle(color: muted, fontSize: 11),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ActionTile extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const ActionTile({
    super.key,
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.all(15),
          child: Row(
            children: [
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(15),
                ),
                child: Icon(icon, color: color),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        color: ink,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      subtitle,
                      style: const TextStyle(color: muted, fontSize: 11),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: muted),
            ],
          ),
        ),
      ),
    );
  }
}

class RoundButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const RoundButton({
    super.key,
    required this.icon,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: background,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: SizedBox(
          width: 48,
          height: 48,
          child: Icon(icon, color: ink, size: 25),
        ),
      ),
    );
  }
}

class VoiceItem extends StatelessWidget {
  final String title;
  final String duration;

  const VoiceItem({
    super.key,
    required this.title,
    required this.duration,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(17),
      ),
      child: Row(
        children: [
          const Icon(Icons.play_circle_fill_rounded, color: blue, size: 30),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                color: ink,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          Text(duration, style: const TextStyle(color: muted, fontSize: 12)),
        ],
      ),
    );
  }
}

class ProfileRow extends StatelessWidget {
  final String label;
  final String value;

  const ProfileRow({
    super.key,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 13),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(label, style: const TextStyle(color: muted)),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: ink,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
