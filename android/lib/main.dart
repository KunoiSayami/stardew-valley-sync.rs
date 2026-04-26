import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'screens/sync_screen.dart';

void main() {
  runApp(const StardewSyncApp());
}

class StardewSyncApp extends StatelessWidget {
  const StardewSyncApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Stardew Sync',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF5B7C3E),
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
      onGenerateRoute: (settings) {
        if (settings.name == '/sync') {
          final args = settings.arguments as Map<String, String>;
          return MaterialPageRoute(
            builder: (_) => SyncScreen(
              ip: args['ip']!,
              port: args['port']!,
              pin: args['pin']!,
            ),
          );
        }
        return null;
      },
    );
  }
}
