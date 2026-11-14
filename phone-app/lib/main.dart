import 'package:flutter/material.dart';

import 'home.dart';
import 'profiles.dart';
import 'theme.dart';
import 'services/firebase_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await FirebaseService.instance.init();
  runApp(const PetCareApp());
}

class PetCareApp extends StatelessWidget {
  const PetCareApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '펫케어 드라이브',
      debugShowCheckedModeBanner: false,
      theme: appTheme(),
      home: const Shell(),
    );
  }
}

// 하단 메뉴: 홈 · 리포트 · 프로필
class Shell extends StatefulWidget {
  const Shell({super.key});

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: switch (tab) {
          0 => const HomePage(),
          1 => const _ReportPlaceholder(),
          _ => const ProfilesPage(),
        },
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => setState(() => tab = i),
        backgroundColor: C.card,
        indicatorColor: C.tint,
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home, color: C.primary), label: '홈'),
          NavigationDestination(icon: Icon(Icons.bar_chart_outlined), selectedIcon: Icon(Icons.bar_chart, color: C.primary), label: '리포트'),
          NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person, color: C.primary), label: '프로필'),
        ],
      ),
    );
  }
}

// ponytail: 분리불안 리포트는 2차 개발 범위. 화면이 생기면 교체
class _ReportPlaceholder extends StatelessWidget {
  const _ReportPlaceholder();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(32),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          IconTile(Icons.bar_chart, size: 64),
          SizedBox(height: 16),
          Text('분리불안 리포트는 준비 중이에요', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
          SizedBox(height: 6),
          Text('짖음 횟수와 진정 시간이 날짜별로 쌓여요', style: TextStyle(color: C.muted)),
        ]),
      ),
    );
  }
}
