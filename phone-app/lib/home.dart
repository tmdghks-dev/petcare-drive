import 'package:flutter/material.dart';

import 'alert.dart';
import 'data.dart';
import 'services/firebase_service.dart';
import 'theme.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final dog = dogs.first;
    return StreamBuilder<CarStatus>(
      stream: FirebaseService.instance.streamCarStatus(),
      initialData: demoStatus,
      builder: (context, statusSnap) {
        final s = statusSnap.data ?? demoStatus;
        return StreamBuilder<List<LogEntry>>(
          stream: FirebaseService.instance.streamLogs(),
          initialData: demoLog,
          builder: (context, logSnap) {
            final logList = logSnap.data ?? demoLog;
            return ListView(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
              children: [
                const Row(children: [
                  StatusPill('펫케어 대기 중'),
                  Spacer(),
                  Text('14:15 시작', style: TextStyle(fontSize: 13, color: C.muted)),
                ]),
                const SizedBox(height: 14),
                Row(children: [
                  DogPhoto(dog.photo, size: 72),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('${josa(dog.name, '은', '는')} 편안해요', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
                      const SizedBox(height: 2),
                      const Text('혼자 있은 지 25분', style: TextStyle(fontSize: 14, color: C.muted)),
                    ]),
                  ),
                ]),
                const SizedBox(height: 18),
                GridView.count(
                  crossAxisCount: 2,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  childAspectRatio: 1.2,
                  children: [
                    _Stat(Icons.thermostat, '실내 온도', '${s.temp.toStringAsFixed(1)}℃'),
                    _Stat(Icons.battery_full, '배터리 · 약 ${s.careHours}시간', '${s.battery}%'),
                    _Stat(Icons.lock_outline, '도어', s.locked ? '잠김' : '열림'),
                    _Stat(Icons.place_outlined, '위치', s.place, small: true),
                  ],
                ),
                const SizedBox(height: 12),
                CardBox(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('오늘 기록', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    for (final e in logList)
                      InkWell(
                        onTap: e.bark ? () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AlertPage())) : null,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          child: Row(children: [
                            Expanded(child: Text('${e.time} ${e.text}', style: const TextStyle(fontSize: 13))),
                            Text(e.value, style: const TextStyle(fontSize: 13, color: C.muted)),
                            if (e.bark) const Icon(Icons.chevron_right, size: 18, color: C.muted),
                          ]),
                        ),
                      ),
                    const SizedBox(height: 4),
                    const Text('Firebase 실시간 연동 중', style: TextStyle(fontSize: 11, color: C.muted)),
                  ]),
                ),
                const SizedBox(height: 14),
                PrimaryButton('내 목소리 들려주기', icon: Icons.mic_none, onPressed: () async {
                  final ok = await FirebaseService.instance.triggerVoicePlayback();
                  if (context.mounted) {
                    toast(context, ok ? '차량으로 목소리 재생 명령을 전달했어요' : '차량 연결 후 목소리가 바로 재생돼요');
                  }
                }),
              ],
            );
          },
        );
      },
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat(this.icon, this.label, this.value, {this.small = false});

  final IconData icon;
  final String label, value;
  final bool small;

  @override
  Widget build(BuildContext context) {
    return CardBox(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        IconTile(icon),
        Text(label, style: const TextStyle(fontSize: 12, color: C.muted)),
        Text(value, style: TextStyle(fontSize: small ? 18 : 22, fontWeight: FontWeight.w900), maxLines: 1, overflow: TextOverflow.ellipsis),
      ]),
    );
  }
}
