import 'package:flutter/material.dart';

// 디자인 시안과 같은 색. 차량 앱(car-app/res/values/colors.xml)과 값이 같아야 한다.
class C {
  static const bg = Color(0xFFF2F7FB);
  static const card = Colors.white;
  static const ink = Color(0xFF0E2A47);
  static const muted = Color(0xFF51667C);
  static const primary = Color(0xFF0B75B3);
  static const accent = Color(0xFF00AAD2);
  static const tint = Color(0xFFE1F3FA);
  static const tintStrong = Color(0xFFBFE6F4);
  static const line = Color(0xFFD8E6F0);
}

ThemeData appTheme() => ThemeData(
      useMaterial3: true,
      scaffoldBackgroundColor: C.bg,
      colorScheme: ColorScheme.fromSeed(seedColor: C.primary, primary: C.primary, surface: C.bg),
      textTheme: const TextTheme().apply(bodyColor: C.ink, displayColor: C.ink),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );

// 모든 화면 공통: 왼쪽 위 상태 표시. strong이면 진한 파랑(진정 중).
class StatusPill extends StatelessWidget {
  const StatusPill(this.label, {super.key, this.strong = false});

  final String label;
  final bool strong;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(color: strong ? C.primary : C.tint, borderRadius: BorderRadius.circular(999)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: strong ? Colors.white : C.accent, shape: BoxShape.circle),
        ),
        const SizedBox(width: 8),
        Text(label,
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: strong ? Colors.white : C.primary)),
      ]),
    );
  }
}

// 모든 화면 공통: 동그란 반려견 사진 + 연한 파랑 테두리
class DogPhoto extends StatelessWidget {
  const DogPhoto(this.asset, {super.key, required this.size, this.ring = 4, this.strongRing = false});

  final String? asset;
  final double size;
  final double ring;
  final bool strongRing;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      padding: EdgeInsets.all(ring),
      decoration: BoxDecoration(color: strongRing ? C.tintStrong : C.tint, shape: BoxShape.circle),
      child: ClipOval(
        child: asset == null
            ? Container(color: C.tint, child: Icon(Icons.pets, color: C.muted, size: size * 0.4))
            : Image.asset(asset!, fit: BoxFit.cover),
      ),
    );
  }
}

// 연한 파랑 칸 안의 아이콘 (카드 왼쪽 위, 동작 목록 앞)
class IconTile extends StatelessWidget {
  const IconTile(this.icon, {super.key, this.size = 36});

  final IconData icon;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: C.tint, borderRadius: BorderRadius.circular(size / 3)),
      child: Icon(icon, size: size * 0.56, color: C.primary),
    );
  }
}

class CardBox extends StatelessWidget {
  const CardBox({super.key, required this.child, this.padding = const EdgeInsets.all(16)});

  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(color: C.card, borderRadius: BorderRadius.circular(18)),
      child: child,
    );
  }
}

class PrimaryButton extends StatelessWidget {
  const PrimaryButton(this.label, {super.key, this.icon, required this.onPressed});

  final String label;
  final IconData? icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 56,
      child: FilledButton.icon(
        onPressed: onPressed,
        icon: icon == null ? const SizedBox.shrink() : Icon(icon),
        label: Text(label, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        style: FilledButton.styleFrom(
            backgroundColor: C.primary, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
      ),
    );
  }
}

class OutlineButton extends StatelessWidget {
  const OutlineButton(this.label, {super.key, required this.onPressed});

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 52,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: C.primary,
          backgroundColor: C.card,
          side: const BorderSide(color: C.primary, width: 2),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        ),
        child: Text(label, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
      ),
    );
  }
}

void toast(BuildContext context, String message) =>
    ScaffoldMessenger.of(context)..hideCurrentSnackBar()..showSnackBar(SnackBar(content: Text(message)));
