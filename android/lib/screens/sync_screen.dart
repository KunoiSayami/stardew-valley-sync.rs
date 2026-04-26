import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/save_slot.dart';
import '../services/api_client.dart';
import '../services/saf_service.dart';
import '../widgets/conflict_dialog.dart';

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

class _SyncScreenState extends State<SyncScreen> {
  late final ApiClient _api;
  final _saf = SafService();
  String? _treeUri;

  List<SaveSlot> _serverSlots = [];
  bool _loading = false;
  String? _statusMessage;

  @override
  void initState() {
    super.initState();
    _api = ApiClient(
      baseUrl: 'http://${widget.ip}:${widget.port}',
      pin: widget.pin,
    );
    _initSaf();
    _refresh();
  }

  Future<void> _initSaf() async {
    final prefs = await SharedPreferences.getInstance();
    String? uri = prefs.getString('saf_tree_uri');
    if (uri == null) {
      uri = await _saf.openDirectoryPicker();
      if (uri != null) {
        await prefs.setString('saf_tree_uri', uri);
      }
    }
    setState(() => _treeUri = uri);
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _statusMessage = null;
    });
    try {
      final slots = await _api.listSaves();
      setState(() => _serverSlots = slots);
    } catch (e) {
      setState(() => _statusMessage = 'Failed to load saves: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  /// Pull: download from server → write to Android.
  Future<void> _pull(SaveSlot serverSlot) async {
    final uri = _treeUri;
    if (uri == null) {
      _showStatus('No save folder selected. Tap the folder icon.');
      return;
    }

    // Check local timestamp for conflict
    final localMs = await _saf.getSlotModifiedMs(uri, serverSlot.slotId);
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
      await _saf.writeSave(uri, serverSlot.slotId, zipBytes);
      _showStatus('Downloaded ${serverSlot.slotId}');
    } catch (e) {
      _showStatus('Download failed: $e');
    }
  }

  /// Push: read from Android → upload to server.
  Future<void> _push(SaveSlot serverSlot) async {
    final uri = _treeUri;
    if (uri == null) {
      _showStatus('No save folder selected.');
      return;
    }

    final localMs = await _saf.getSlotModifiedMs(uri, serverSlot.slotId);

    // Check if server is newer
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
      final zipBytes = await _saf.readSave(uri, serverSlot.slotId);
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
        targetTime: DateTime.fromMillisecondsSinceEpoch(e.serverLastModifiedMs),
        sourceTime: DateTime.fromMillisecondsSinceEpoch(e.clientLastModifiedMs),
        targetLabel: 'Server',
        sourceLabel: 'Android',
      );
      if (overwrite) {
        final zipBytes = await _saf.readSave(uri, serverSlot.slotId);
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
    setState(() => _statusMessage = msg);
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
            icon: const Icon(Icons.folder_open),
            tooltip: 'Change saves folder',
            onPressed: () async {
              final uri = await _saf.openDirectoryPicker();
              if (uri != null) {
                final prefs = await SharedPreferences.getInstance();
                await prefs.setString('saf_tree_uri', uri);
                setState(() => _treeUri = uri);
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
        ],
      ),
      body: Column(
        children: [
          if (_treeUri == null)
            MaterialBanner(
              content: const Text(
                  'No save folder selected. Tap the folder icon to choose your Stardew Valley saves directory.'),
              actions: [
                TextButton(
                    onPressed: _initSaf, child: const Text('Choose folder'))
              ],
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
                              tooltip: 'Push from Android to server',
                              onPressed: () => _push(slot),
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
