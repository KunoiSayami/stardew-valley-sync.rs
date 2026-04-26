import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/discovery.dart';

class ServerConnectScreen extends StatefulWidget {
  const ServerConnectScreen({super.key});

  @override
  State<ServerConnectScreen> createState() => _ServerConnectScreenState();
}

class _ServerConnectScreenState extends State<ServerConnectScreen> {
  final _ipController = TextEditingController();
  final _portController = TextEditingController(text: '24742');
  final _pinController = TextEditingController();
  List<DiscoveredServer> _discovered = [];
  bool _scanning = false;
  bool _connecting = false;

  @override
  void initState() {
    super.initState();
    _loadSaved(); // handles scanning internally when saved credentials exist
  }

  Future<void> _loadSaved() async {
    final prefs = await SharedPreferences.getInstance();
    final ip = prefs.getString('server_ip') ?? '';
    final port = prefs.getString('server_port') ?? '24742';
    final pin = prefs.getString('server_pin') ?? '';
    setState(() {
      _ipController.text = ip;
      _portController.text = port;
      _pinController.text = pin;
    });
    if (ip.isNotEmpty && pin.isNotEmpty) {
      // Saved credentials exist: scan first, but auto-connect if nothing found.
      setState(() => _scanning = true);
      final servers = await discoverServers();
      if (!mounted) return;
      setState(() => _scanning = false);
      if (servers.isEmpty) {
        _connect(ip, port, pin);
      } else {
        setState(() => _discovered = servers);
      }
    } else {
      // No saved credentials: just scan normally.
      _scan();
    }
  }

  Future<void> _scan() async {
    setState(() {
      _scanning = true;
      _discovered = [];
    });
    final servers = await discoverServers();
    setState(() {
      _discovered = servers;
      _scanning = false;
    });
  }

  Future<void> _connect(String ip, String port, String pin) async {
    if (ip.isEmpty || pin.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter IP and PIN')),
      );
      return;
    }
    setState(() => _connecting = true);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('server_ip', ip);
    await prefs.setString('server_port', port);
    await prefs.setString('server_pin', pin);
    if (!mounted) return;
    setState(() => _connecting = false);
    Navigator.of(context).pushReplacementNamed('/sync',
        arguments: {'ip': ip, 'port': port, 'pin': pin});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Connect to PC Server')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_scanning)
            const ListTile(
              leading: CircularProgressIndicator(),
              title: Text('Scanning for servers…'),
            )
          else if (_discovered.isEmpty)
            ListTile(
              leading: const Icon(Icons.search_off),
              title: const Text('No servers found automatically'),
              trailing: IconButton(
                icon: const Icon(Icons.refresh),
                onPressed: _scan,
              ),
            )
          else
            ..._discovered.map(
              (s) => ListTile(
                leading: const Icon(Icons.computer),
                title: Text(s.toString()),
                subtitle: const Text('Tap to use this server'),
                onTap: () {
                  _ipController.text = s.host;
                  _portController.text = s.port.toString();
                },
              ),
            ),
          const Divider(),
          const Text('Manual entry',
              style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          TextField(
            controller: _ipController,
            decoration: const InputDecoration(
              labelText: 'Server IP',
              hintText: '192.168.1.x',
              border: OutlineInputBorder(),
            ),
            keyboardType: TextInputType.url,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _portController,
            decoration: const InputDecoration(
              labelText: 'Port',
              border: OutlineInputBorder(),
            ),
            keyboardType: TextInputType.number,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _pinController,
            decoration: const InputDecoration(
              labelText: 'PIN',
              border: OutlineInputBorder(),
            ),
            obscureText: true,
            keyboardType: TextInputType.number,
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _connecting
                ? null
                : () => _connect(
                      _ipController.text.trim(),
                      _portController.text.trim(),
                      _pinController.text.trim(),
                    ),
            child: _connecting
                ? const CircularProgressIndicator()
                : const Text('Connect'),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _ipController.dispose();
    _portController.dispose();
    _pinController.dispose();
    super.dispose();
  }
}
