import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:petcare_phone/main.dart';

void main() {
  testWidgets('목소리 들려주기를 누르면 진정 카드로 바뀐다', (tester) async {
    tester.view.physicalSize = const Size(1080, 2400); // 폰 세로 화면
    addTearDown(tester.view.reset);
    await tester.pumpWidget(const PetCareApp());
    expect(find.text('콩이가 3분째 짖고 있어요'), findsOneWidget);
    await tester.tap(find.text('목소리 들려주기'));
    await tester.pumpAndSettle();
    expect(find.text('콩이가 진정했어요'), findsOneWidget);
  });
}
