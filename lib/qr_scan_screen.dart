
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

class QrScanScreen extends StatefulWidget {
  const QrScanScreen({super.key});

  @override
  State<QrScanScreen> createState() => _QrScanScreenState();
}

class _QrScanScreenState extends State<QrScanScreen> {
  String? scannedCode;
  bool handled = false;

  void onDetect(BarcodeCapture capture) {
    if (handled) return;

    for (final barcode in capture.barcodes) {
      final value = barcode.rawValue;

      if (value != null && value.isNotEmpty) {
        setState(() {
          handled = true;
          scannedCode = value;
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
            const Text(
              'QR 코드 인식 성공!',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: SelectableText(scannedCode!),
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
