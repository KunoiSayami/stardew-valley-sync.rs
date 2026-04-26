import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/save_slot.dart';
import 'server_connect_screen.dart';
import '../services/api_client.dart';
import '../services/saf_service.dart';
import '../widgets/conflict_dialog.dart';

const _kSavesPathKey = 'saves_path';

class SyncScreen extends StatefulWidget {
  final String ip;
  final String port;
  final String pin;

  const SyncScreen({
    super.key,
    required this.ip,
    required this.port,
    required this.pin,
  });

  @override
  State<SyncScreen> createState() => _SyncScreenState();
}

class _SyncScreenState extends State<SyncScreen> with WidgetsBindingObserver {
  late final ApiClient _api;
  final _saf = SafService();

  bool _hasPermission = false;
  String? _savesPath; // null = use default
  List<SaveSlot> _serverSlots = [];
  Set<String> _localSlotIds = {};
  bool _dirMissing = false;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _api = ApiClient(
      baseUrl: 'http://${widget.ip}:${widget.port}',
      pin: widget.pin,
    );
    _init();
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _checkPermission();
  }

  Future<void> _init() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_kSavesPathKey);
    if (mounted) setState(() => _savesPath = saved);
    await _checkPermission();
  }

  Future<void> _checkPermission() async {
    final granted = await _saf.hasPermission();
    if (mounted) setState(() => _hasPermission = granted);
  }

  Future<void> _disconnect() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('server_ip');
    await prefs.remove('server_port');
    await prefs.remove('server_pin');
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const ServerConnectScreen()),
    );
  }

  Future<void> _requestPermission() async {
    final granted = await _saf.checkAndRequestPermission();
    if (mounted) setState(() => _hasPermission = granted);
  }

  Future<void> _editSavesPath() async {
    final defaultPath = await _saf.getDefaultSavesPath();
    if (!mounted) return;

    final controller = TextEditingController(text: _savesPath ?? '');
    final result = await showDialog<String?>(
      context: context,
      builder: (ctx) => _SavesPathDialog(
        controller: controller,
        defaultPath: defaultPath,
        saf: _saf,
      ),
    );

    // result == null → cancelled; result == '' → reset to default
    if (result == null) return;
    final newPath = result.trim().isEmpty ? null : result.trim();
    final prefs = await SharedPreferences.getInstance();
    if (newPath == null) {
      await prefs.remove(_kSavesPathKey);
    } else {
      await prefs.setString(_kSavesPathKey, newPath);
    }
    if (mounted) {
      setState(() => _savesPath = newPath);
      _refresh();
    }
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    try {
      final (serverSlots, dirExists, localSlots) = await (
        _api.listSaves(),
        _saf.savesDirExists(savesPath: _savesPath),
        _saf.listSaves(savesPath: _savesPath),
      ).wait;
      if (mounted) {
        setState(() {
          _serverSlots = serverSlots;
          _dirMissing = !dirExists;
          _localSlotIds = {for (final s in localSlots) s.slotId};
        });
      }
    } catch (e) {
      _showStatus('Failed to load saves: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pull(SaveSlot serverSlot) async {
    if (!_hasPermission) {
      _showStatus('Storage permission required. Tap the lock icon.');
      return;
    }

    final localMs =
        await _saf.getSlotModifiedMs(serverSlot.slotId, savesPath: _savesPath);
    if (localMs > serverSlot.lastModifiedMs) {
      if (!mounted) return;
      final overwrite = await showConflictDialog(
        context,
        slotId: serverSlot.slotId,
        targetTime: DateTime.fromMillisecondsSinceEpoch(localMs),
        sourceTime: serverSlot.lastModified,
        targetLabel: 'Android',
        sourceLabel: 'Server',
      );
      if (!overwrite) return;
    }

    _showStatus('Downloading ${serverSlot.slotId}…');
    try {
      final (:zipBytes, :serverLastModifiedMs) =
          await _api.downloadSave(serverSlot.slotId);
      await _saf.writeSave(serverSlot.slotId, zipBytes, savesPath: _savesPath);
      _showStatus('Downloaded ${serverSlot.slotId}');
    } catch (e) {
      _showStatus('Download failed: $e');
    }
  }

  Future<void> _push(SaveSlot serverSlot) async {
    if (!_hasPermission) {
      _showStatus('Storage permission required. Tap the lock icon.');
      return;
    }

    final localMs =
        await _saf.getSlotModifiedMs(serverSlot.slotId, savesPath: _savesPath);

    if (serverSlot.lastModifiedMs > localMs) {
      if (!mounted) return;
      final overwrite = await showConflictDialog(
        context,
        slotId: serverSlot.slotId,
        targetTime: serverSlot.lastModified,
        sourceTime: DateTime.fromMillisecondsSinceEpoch(localMs),
        targetLabel: 'Server',
        sourceLabel: 'Android',
      );
      if (!overwrite) return;
    }

    _showStatus('Uploading ${serverSlot.slotId}…');
    try {
      final zipBytes =
          await _saf.readSave(serverSlot.slotId, savesPath: _savesPath);
      await _api.uploadSave(
        serverSlot.slotId,
        zipBytes,
        localMs,
        force: serverSlot.lastModifiedMs > localMs,
      );
      _showStatus('Uploaded ${serverSlot.slotId}');
      await _refresh();
    } on ConflictException catch (e) {
      if (!mounted) return;
      final overwrite = await showConflictDialog(
        context,
        slotId: serverSlot.slotId,
        targetTime:
            DateTime.fromMillisecondsSinceEpoch(e.serverLastModifiedMs),
        sourceTime:
            DateTime.fromMillisecondsSinceEpoch(e.clientLastModifiedMs),
        targetLabel: 'Server',
        sourceLabel: 'Android',
      );
      if (overwrite) {
        final zipBytes =
            await _saf.readSave(serverSlot.slotId, savesPath: _savesPath);
        await _api.uploadSave(serverSlot.slotId, zipBytes, localMs,
            force: true);
        _showStatus('Uploaded ${serverSlot.slotId} (forced)');
        await _refresh();
      }
    } catch (e) {
      _showStatus('Upload failed: $e');
    }
  }

  void _showStatus(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg), duration: const Duration(seconds: 3)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('${widget.ip}:${widget.port}'),
        actions: [
          IconButton(
            icon: Icon(
              _hasPermission ? Icons.lock_open : Icons.lock,
              color: _hasPermission ? null : Colors.orange,
            ),
            tooltip: _hasPermission
                ? 'Storage access granted'
                : 'Grant "All files access" permission',
            onPressed: _hasPermission ? null : _requestPermission,
          ),
          IconButton(
            icon: const Icon(Icons.folder_open),
            tooltip: 'Set saves folder path',
            onPressed: _editSavesPath,
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Disconnect',
            onPressed: _disconnect,
          ),
        ],
      ),
      body: Column(
        children: [
          if (!_hasPermission)
            MaterialBanner(
              content: const Text(
                  'Storage permission required to access Stardew Valley saves. '
                  'Tap the lock icon to grant "All files access".'),
              actions: [
                TextButton(
                    onPressed: _requestPermission,
                    child: const Text('Grant access')),
              ],
            ),
          if (_dirMissing)
            MaterialBanner(
              content: Text(
                  'Saves directory not found: ${_savesPath ?? 'default path'}. '
                  'Tap the folder icon to set the correct path.'),
              leading: const Icon(Icons.warning_amber, color: Colors.orange),
              actions: [
                TextButton(
                    onPressed: _editSavesPath,
                    child: const Text('Change path')),
              ],
            ),
          if (_savesPath != null && !_dirMissing)
            Container(
              width: double.infinity,
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              padding:
                  const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: Text(
                'Saves: $_savesPath',
                style: Theme.of(context).textTheme.bodySmall,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          if (_loading) const LinearProgressIndicator(),
          Expanded(
            child: _serverSlots.isEmpty
                ? Center(
                    child: _loading
                        ? const CircularProgressIndicator()
                        : const Text('No save slots found on server.'),
                  )
                : ListView.separated(
                    itemCount: _serverSlots.length,
                    separatorBuilder: (_, __) => const Divider(height: 0),
                    itemBuilder: (ctx, i) {
                      final slot = _serverSlots[i];
                      return ListTile(
                        leading: const Icon(Icons.save),
                        title: Text(slot.displayName),
                        subtitle: Text(
                            '${slot.formattedSize}  •  ${_fmtDate(slot.lastModified)}'),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.download),
                              tooltip: 'Pull from server to Android',
                              onPressed: () => _pull(slot),
                            ),
                            IconButton(
                              icon: const Icon(Icons.upload),
                              tooltip: _localSlotIds.contains(slot.slotId)
                                  ? 'Push from Android to server'
                                  : 'No local save found',
                              onPressed: _localSlotIds.contains(slot.slotId)
                                  ? () => _push(slot)
                                  : null,
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  String _fmtDate(DateTime dt) =>
      '${dt.year}-${_p(dt.month)}-${_p(dt.day)} ${_p(dt.hour)}:${_p(dt.minute)}';

  String _p(int n) => n.toString().padLeft(2, '0');
}

class _SavesPathDialog extends StatefulWidget {
  final TextEditingController controller;
  final String defaultPath;
  final SafService saf;

  const _SavesPathDialog({
    required this.controller,
    required this.defaultPath,
    required this.saf,
  });

  @override
  State<_SavesPathDialog> createState() => _SavesPathDialogState();
}

class _SavesPathDialogState extends State<_SavesPathDialog> {
  bool _picking = false;

  Future<void> _browse() async {
    setState(() => _picking = true);
    final picked = await widget.saf.pickDirectory();
    if (mounted) {
      setState(() => _picking = false);
      if (picked != null) widget.controller.text = picked;
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Saves folder path'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Default: ${widget.defaultPath}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: widget.controller,
            decoration: InputDecoration(
              labelText: 'Custom path (leave empty for default)',
              hintText: '/sdcard/...',
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: _picking
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.folder_open),
                tooltip: 'Browse',
                onPressed: _picking ? null : _browse,
              ),
            ),
            autocorrect: false,
            keyboardType: TextInputType.url,
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(null),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(''),
          child: const Text('Reset to default'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(widget.controller.text),
          child: const Text('Save'),
        ),
      ],
    );
  }
}
