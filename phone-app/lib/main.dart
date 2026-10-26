import 'package:flutter/material.dart';

void main() => runApp(const PetCareApp());

class PetCareApp extends StatelessWidget {
  const PetCareApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '펫케어 드라이브',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: const Color(0xFFD98A1E), useMaterial3: true),
      home: const DashboardPage(),
    );
  }
}

// ponytail: 지금은 데모 데이터. Firebase 연결 후 vehicles/{carId}/status, events 구독으로 교체
class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  bool barking = true;

  void _sendVoice() {
    setState(() => barking = false);
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('차량에서 보호자 목소리를 재생했어요')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: const Color(0xFFF4F1EC),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF4F1EC),
        title: const Text('콩이 펫케어', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 16),
            child: Chip(label: Text('대기 모드 25분째')),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 300),
            child: barking
                ? Card(
                    key: const ValueKey('alert'),
                    color: const Color(0xFFE0573B),
                    child: ListTile(
                      contentPadding: const EdgeInsets.all(16),
                      leading: const Icon(Icons.campaign, color: Colors.white, size: 36),
                      title: const Text('콩이가 3분째 짖고 있어요',
                          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
                      subtitle: const Text('차량이 2단계 진정을 진행 중이에요',
                          style: TextStyle(color: Colors.white70)),
                    ),
                  )
                : Card(
                    key: const ValueKey('ok'),
                    color: const Color(0xFF3E9B6A),
                    child: const ListTile(
                      contentPadding: EdgeInsets.all(16),
                      leading: Icon(Icons.favorite, color: Colors.white, size: 36),
                      title: Text('콩이가 진정했어요',
                          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
                      subtitle: Text('짖음 3회 · 1분 40초 만에 안정', style: TextStyle(color: Colors.white70)),
                    ),
                  ),
          ),
          const SizedBox(height: 16),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
            childAspectRatio: 1.3,
            children: const [
              _Stat(icon: Icons.thermostat, label: '실내 온도', value: '23.0℃', note: '적정 범위'),
              _Stat(icon: Icons.battery_charging_full, label: '배터리', value: '82%', note: '약 42시간 유지'),
              _Stat(icon: Icons.lock, label: '도어', value: '잠김', note: '4개 문 모두'),
              _Stat(icon: Icons.place, label: '위치', value: '안성휴게소', note: '하행선 주차장'),
            ],
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _sendVoice,
            icon: const Icon(Icons.mic),
            label: const Padding(
              padding: EdgeInsets.symmetric(vertical: 14),
              child: Text('목소리 들려주기', style: TextStyle(fontSize: 17)),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () {},
            icon: const Icon(Icons.call),
            label: const Padding(
              padding: EdgeInsets.symmetric(vertical: 14),
              child: Text('차량과 통화하기', style: TextStyle(fontSize: 17)),
            ),
          ),
          const SizedBox(height: 12),
          Text('데모 데이터로 표시 중', textAlign: TextAlign.center, style: TextStyle(color: scheme.outline)),
        ],
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.icon, required this.label, required this.value, required this.note});

  final IconData icon;
  final String label, value, note;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.white,
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(children: [
              Icon(icon, size: 20, color: Colors.black54),
              const SizedBox(width: 6),
              Text(label, style: const TextStyle(color: Colors.black54)),
            ]),
            Text(value, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
            Text(note, style: const TextStyle(color: Colors.black45, fontSize: 12)),
          ],
        ),
      ),
    );
  }
}
