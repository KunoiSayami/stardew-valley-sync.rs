import 'package:flutter/material.dart';

import '../services/file_access_mode.dart';
import '../services/saf_service.dart';

class FileAccessSettingsScreen extends StatefulWidget {
  final SafService saf;

  const FileAccessSettingsScreen({super.key, required this.saf});

  @override
  State<FileAccessSettingsScreen> createState() => _FileAccessSettingsScreenState();
}

class _FileAccessSettingsScreenState extends State<FileAccessSettingsScreen> {
  FileAccessMode _mode = FileAccessMode.manageStorage;
  bool _shizukuAvailable = false;
  bool _hasPermission = false;
  bool _loading = true;
  bool _requesting = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final mode = fileAccessModeFromChannel(await widget.saf.getFileAccessMode());
    final shizukuAvail = await widget.saf.isShizukuAvailable();
    final hasPerm = await widget.saf.hasPermission();
    if (mounted) {
      setState(() {
        _mode = mode;
        _shizukuAvailable = shizukuAvail;
        _hasPermission = hasPerm;
        _loading = false;
      });
    }
  }

  Future<void> _selectMode(FileAccessMode mode) async {
    await widget.saf.setFileAccessMode(mode.toChannelValue());
    final hasPerm = await widget.saf.hasPermission();
    if (mounted) setState(() { _mode = mode; _hasPermission = hasPerm; });
  }

  Future<void> _requestPermission() async {
    setState(() => _requesting = true);
    final granted = await widget.saf.checkAndRequestPermission();
    if (mounted) setState(() { _hasPermission = granted; _requesting = false; });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('File Access Method')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                RadioGroup<FileAccessMode>(
                  groupValue: _mode,
                  onChanged: (v) => _selectMode(v!),
                  child: Column(
                    children: [
                      _buildTile(
                        mode: FileAccessMode.manageStorage,
                        enabled: true,
                      ),
                      const Divider(height: 0),
                      _buildTile(
                        mode: FileAccessMode.shizuku,
                        enabled: _shizukuAvailable,
                        unavailableNote: _shizukuAvailable ? null : 'Shizuku is not installed or not running',
                      ),
                    ],
                  ),
                ),
                if (_mode == FileAccessMode.manageStorage || _shizukuAvailable)
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: _permissionRow(),
                  ),
              ],
            ),
    );
  }

  Widget _buildTile({
    required FileAccessMode mode,
    required bool enabled,
    String? unavailableNote,
  }) {
    return RadioListTile<FileAccessMode>(
      value: mode,
      enabled: enabled,
      title: Text(mode.displayName),
      subtitle: Text(unavailableNote ?? mode.description),
    );
  }

  Widget _permissionRow() {
    if (_hasPermission) {
      return Row(
        children: [
          Icon(Icons.check_circle, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          const Text('Permission granted'),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.warning_amber, color: Theme.of(context).colorScheme.error),
            const SizedBox(width: 8),
            const Text('Permission not granted'),
          ],
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: _requesting ? null : _requestPermission,
          icon: _requesting
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.lock_open),
          label: const Text('Grant access'),
        ),
      ],
    );
  }
}
