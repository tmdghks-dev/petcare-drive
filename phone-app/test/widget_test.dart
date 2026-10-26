import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:petcare_phone/data.dart';
import 'package:petcare_phone/main.dart';

void main() {
  testWidgets('홈 → 짖음 기록을 누르면 알림 화면이 열린다', (tester) async {
    await _phone(tester);
    expect(find.text('달이는 편안해요'), findsOneWidget);
    await tester.tap(find.textContaining('짖음 감지'));
    await tester.pumpAndSettle();
    expect(find.text('달이가 짖고 있어요'), findsOneWidget);
  });

  testWidgets('프로필에서 아이를 추가하면 2마리가 된다', (tester) async {
    await _phone(tester);
    await tester.tap(find.text('프로필'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('+ 아이 추가'));
    await tester.pumpAndSettle();
    expect(find.text('반려견 2마리'), findsOneWidget);
    expect(dogs.length, 2);
  });

  test('조사', () {
    expect(josa('달이', '은', '는'), '달이는');
    expect(josa('별', '은', '는'), '별은');
    expect(josa('달이', '이', '가'), '달이가');
  });
}

// 일반 안드로이드 폰 세로 화면 (약 390×844)
Future<void> _phone(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1080, 2340);
  tester.view.devicePixelRatio = 2.77;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(const PetCareApp());
}
