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
    _loadSaved();
    _scan();
  }

  Future<void> _loadSaved() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _ipController.text = prefs.getString('server_ip') ?? '';
      _portController.text = prefs.getString('server_port') ?? '24742';
      _pinController.text = prefs.getString('server_pin') ?? '';
    });
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
