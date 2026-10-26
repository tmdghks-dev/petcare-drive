import 'package:flutter/material.dart';

import 'data.dart';
import 'theme.dart';

// 짖음 알림을 누르면 열리는 화면. 차에서 동시에 실행 중인 진정 동작과 보호자 대응 버튼.
// ponytail: 지금은 홈의 "짖음 감지" 기록을 눌러 들어옴. FCM 푸시가 붙으면 알림 탭으로 이 화면을 연다
class AlertPage extends StatelessWidget {
  const AlertPage({super.key});

  @override
  Widget build(BuildContext context) {
    final dog = dogs.first;
    const s = demoStatus;
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Row(children: [
              IconButton(
                tooltip: '뒤로',
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.arrow_back_ios_new, size: 20, color: C.ink),
              ),
              const Spacer(),
              const StatusPill('짖음 감지 · 진정 중', strong: true),
            ]),
            const SizedBox(height: 12),
            Center(child: DogPhoto(dog.photo, size: 160, ring: 12, strongRing: true)),
            const SizedBox(height: 12),
            Text('${josa(dog.name, '이', '가')} 짖고 있어요',
                textAlign: TextAlign.center, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w900)),
            const SizedBox(height: 4),
            const Text('14:21부터 3분째 · 차에서 진정을 시작했어요',
                textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: C.muted)),
            const SizedBox(height: 20),
            CardBox(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('차에서 동시에 실행 중', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                const SizedBox(height: 12),
                const _Row(Icons.lightbulb_outline, '실내 조명 은은한 블루로 낮춤'),
                const _Row(Icons.music_note_outlined, '안정 음악 재생 중'),
                _Row(Icons.thermostat, '실내 ${s.temp.toStringAsFixed(1)}℃ · 배터리 ${s.battery}%'),
              ]),
            ),
            const Spacer(),
            PrimaryButton('내 목소리 들려주기',
                icon: Icons.mic_none, onPressed: () => toast(context, '차량 연결 후 목소리가 바로 재생돼요')),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: OutlineButton('차량과 통화', onPressed: () => toast(context, '통화 기능은 준비 중이에요'))),
              const SizedBox(width: 10),
              Expanded(child: OutlineButton('온도 조절', onPressed: () => toast(context, '원격 온도 조절은 준비 중이에요'))),
            ]),
          ]),
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row(this.icon, this.text);

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(children: [
        IconTile(icon),
        const SizedBox(width: 12),
        Expanded(child: Text(text, style: const TextStyle(fontSize: 14))),
      ]),
    );
  }
}
