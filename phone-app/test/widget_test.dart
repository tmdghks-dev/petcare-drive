import 'package:flutter_test/flutter_test.dart';

import 'package:petcare_phone/main.dart';
import 'package:petcare_phone/qr_scan_screen.dart';

void main() {
  testWidgets('첫 화면이 뜬다', (tester) async {
    await tester.pumpWidget(const PetCareApp());
    expect(find.text('펫케어 드라이브'), findsWidgets);
  });

  test('차 QR에서 차 코드를 꺼낸다', () {
    expect(carIdFromQr('petcare://pair?car=car_90fa8b49'), 'car_90fa8b49');
    expect(carIdFromQr('https://example.com'), isNull);
    expect(carIdFromQr('petcare://pair?car=E8D9DDA8'), 'car_e8d9dda8');
  });
}
