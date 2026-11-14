import 'package:flutter/material.dart';

import 'data.dart';
import 'services/firebase_service.dart';
import 'theme.dart';

// 반려견 여러 마리를 등록하고 고르는 화면. 저장하면 차량 앱의 "함께 탈 아이 선택"에 나타난다.
class ProfilesPage extends StatefulWidget {
  const ProfilesPage({super.key});

  @override
  State<ProfilesPage> createState() => _ProfilesPageState();
}

class _ProfilesPageState extends State<ProfilesPage> {
  int current = 0;
  final name = TextEditingController();
  final breed = TextEditingController();
  final age = TextEditingController();

  Dog get dog => dogs[current];

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    name.text = dog.name;
    breed.text = dog.breed;
    age.text = dog.age;
  }

  void _select(int i) => setState(() {
        current = i;
        _load();
      });

  void _add() {
    dogs.add(Dog(name: '새 아이'));
    _select(dogs.length - 1);
  }

  void _save() async {
    setState(() {
      dog
        ..name = name.text.trim().isEmpty ? dog.name : name.text.trim()
        ..breed = breed.text.trim()
        ..age = age.text.trim();
    });
    await FirebaseService.instance.saveDogProfiles(dogs);
    if (mounted) {
      toast(context, '저장했어요 · 차량에 동기화되었어요');
    }
  }

  @override
  void dispose() {
    name.dispose();
    breed.dispose();
    age.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
      children: [
        Row(children: [
          StatusPill('반려견 ${dogs.length}마리'),
          const Spacer(),
          OutlinedButton(
            onPressed: _add,
            style: OutlinedButton.styleFrom(
                foregroundColor: C.primary, side: const BorderSide(color: C.primary, width: 2), minimumSize: const Size(0, 44)),
            child: const Text('+ 아이 추가', style: TextStyle(fontWeight: FontWeight.w700)),
          ),
        ]),
        const SizedBox(height: 18),
        const Text('우리 아이들', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
        const SizedBox(height: 12),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(children: [
            for (var i = 0; i < dogs.length; i++)
              Padding(
                padding: const EdgeInsets.only(right: 10),
                child: _DogChip(dogs[i], selected: i == current, onTap: () => _select(i)),
              ),
          ]),
        ),
        const SizedBox(height: 16),
        CardBox(
          padding: const EdgeInsets.all(18),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Center(child: DogPhoto(dog.photo, size: 112)),
            const SizedBox(height: 14),
            _Field('이름', name),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: _Field('견종', breed, hint: '[견종]')),
              const SizedBox(width: 10),
              Expanded(child: _Field('나이', age, hint: '[나이]')),
            ]),
            const SizedBox(height: 14),
            _Line('적정 온도', '${dog.tempMin} ~ ${dog.tempMax}℃'),
            Row(children: [
              const Text('분리불안이 있어요', style: TextStyle(fontSize: 15)),
              const Spacer(),
              Switch(
                value: dog.anxious,
                activeThumbColor: C.primary,
                onChanged: (v) => setState(() => dog.anxious = v),
              ),
            ]),
            _Line('보호자 목소리', dog.voiceCount == 0 ? '녹음 없음' : '녹음 ${dog.voiceCount}개'),
          ]),
        ),
        const SizedBox(height: 16),
        PrimaryButton('저장하고 차량에 보내기', onPressed: _save),
      ],
    );
  }
}

class _DogChip extends StatelessWidget {
  const _DogChip(this.dog, {required this.selected, required this.onTap});

  final Dog dog;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: C.card,
      shape: StadiumBorder(side: BorderSide(color: selected ? C.primary : C.line, width: 2)),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(4, 4, 16, 4),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            DogPhoto(dog.photo, size: 36, ring: 0),
            const SizedBox(width: 8),
            Text(dog.name,
                style: TextStyle(
                    fontSize: 15, fontWeight: selected ? FontWeight.w700 : FontWeight.w400, color: selected ? C.primary : C.muted)),
          ]),
        ),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field(this.label, this.controller, {this.hint});

  final String label;
  final TextEditingController controller;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.line)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.line)),
        isDense: true,
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line(this.label, this.value);

  final String label, value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(children: [
        Text(label, style: const TextStyle(fontSize: 15)),
        const Spacer(),
        Text(value, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: C.primary)),
      ]),
    );
  }
}
