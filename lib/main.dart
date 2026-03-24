import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'TEPRA Print Helper',
      home: Scaffold(
        body: Center(
          child: Text(
            'tepra-print:// URL スキームで起動してください',
            textAlign: TextAlign.center,
          ),
        ),
      ),
    );
  }
}
