import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../debug_config.dart';
import 'server_connect_screen.dart';
import 'sync_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _checked = false;

  @override
  void initState() {
    super.initState();
    _tryRestore();
  }

  Future<void> _tryRestore() async {
    final prefs = await SharedPreferences.getInstance();
    final ip = prefs.getString('server_ip') ??
        (kDebugMode ? kDebugServerIp : '');
    final port = prefs.getString('server_port') ??
        (kDebugMode ? kDebugServerPort : '24742');
    final pin = prefs.getString('server_pin') ??
        (kDebugMode ? kDebugServerPin : '');
    if (!mounted) return;
    setState(() => _checked = true);
    if (ip.isNotEmpty && pin.isNotEmpty) {
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => SyncScreen(ip: ip, port: port, pin: pin),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_checked) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return const ServerConnectScreen();
  }
}
