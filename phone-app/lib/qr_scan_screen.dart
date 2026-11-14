
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import 'services/firebase_service.dart';

// 차 QR은 petcare://pair?car=car_xxxx. 다른 QR이면 null
String? carIdFromQr(String value) {
  final uri = Uri.tryParse(value);
  if (uri == null || uri.scheme != 'petcare' || uri.host != 'pair') return null;
  final car = uri.queryParameters['car']?.trim().toLowerCase();
  if (car == null || car.isEmpty) return null;
  return car.startsWith('car_') ? car : 'car_$car'; // 차 화면의 코드(E8D9DDA8)만 넣어도 된다
}

class QrScanScreen extends StatefulWidget {
  const QrScanScreen({super.key});

  @override
  State<QrScanScreen> createState() => _QrScanScreenState();
}

class _QrScanScreenState extends State<QrScanScreen> {
  String? scannedCode;
  bool handled = false;
  bool? paired; // null: 연결 중

  void onDetect(BarcodeCapture capture) {
    if (handled) return;

    for (final barcode in capture.barcodes) {
      final value = barcode.rawValue;

      if (value != null && value.isNotEmpty) {
        final carId = carIdFromQr(value);
        if (carId == null) return; // 펫케어 차 QR이 아니면 계속 찾는다
        setState(() {
          handled = true;
          scannedCode = carId;
          paired = null;
        });
        FirebaseService.instance.pairCar(carId).then((ok) {
          if (!mounted) return;
          setState(() => paired = ok);
          // 연결되면 잠깐 보여 주고 홈으로 돌아간다 (홈에 '차량 연결됨'이 뜬다)
          if (ok) {
            Future.delayed(const Duration(milliseconds: 1500), () {
              if (mounted) Navigator.pop(context);
            });
          }
        });
        break;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('자동차 QR 연결'),
      ),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.all(20),
            child: Text(
              '자동차 화면의 QR 코드를\n네모 안에 맞춰 주세요.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 17),
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(20),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    MobileScanner(onDetect: onDetect),
                    Center(
                      child: Container(
                        width: 240,
                        height: 240,
                        decoration: BoxDecoration(
                          border: Border.all(
                            color: Colors.white,
                            width: 4,
                          ),
                          borderRadius: BorderRadius.circular(20),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          if (scannedCode != null) ...[
            Text(
              paired == null ? '차와 연결하는 중…' : paired! ? '차와 연결됐어요!' : '연결하지 못했어요. 다시 찍어 주세요',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('차 코드 ${scannedCode!.replaceFirst('car_', '').toUpperCase()}'),
            ),
            ElevatedButton(
              onPressed: () {
                setState(() {
                  scannedCode = null;
                  handled = false;
                });
              },
              child: const Text('다시 스캔하기'),
            ),
          ] else
            const Padding(
              padding: EdgeInsets.all(20),
              child: Text('QR 코드를 스캔할 준비가 되었어요.'),
            ),
        ],
      ),
    );
  }
}
