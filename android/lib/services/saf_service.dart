import 'dart:typed_data';

import 'package:flutter/services.dart';

/// Dart-side wrapper around the Kotlin SAF platform channel.
///
/// The Kotlin plugin (SafPlugin.kt) implements the following method channel:
///   channel: "com.stardewsync/saf"
///   methods:
///     openDirectoryPicker()        -> String? (persisted URI)
///     listSaves(uri)               -> List<Map> [{slotId, lastModifiedMs}]
///     readSave(uri, slotId)        -> Uint8List  (ZIP bytes)
///     writeSave(uri, slotId, data) -> void
///     getSlotModifiedMs(uri, slotId) -> int
class SafService {
  static const _channel = MethodChannel('com.stardewsync/saf');

  /// Prompts the user to choose the Stardew Saves directory.
  /// Returns the persisted URI string, or null if cancelled.
  Future<String?> openDirectoryPicker() async {
    return await _channel.invokeMethod<String>('openDirectoryPicker');
  }

  /// Lists save slots under the previously granted URI.
  Future<List<({String slotId, int lastModifiedMs})>> listSaves(
      String treeUri) async {
    final raw = await _channel
        .invokeListMethod<Map>('listSaves', {'uri': treeUri});
    return (raw ?? [])
        .map((m) => (
              slotId: m['slotId'] as String,
              lastModifiedMs: (m['lastModifiedMs'] as num).toInt(),
            ))
        .toList();
  }

  /// Builds a ZIP of the two save files and returns the raw bytes.
  Future<Uint8List> readSave(String treeUri, String slotId) async {
    final bytes = await _channel
        .invokeMethod<Uint8List>('readSave', {'uri': treeUri, 'slotId': slotId});
    return bytes!;
  }

  /// Extracts a ZIP and writes the save files, creating a .bak first.
  Future<void> writeSave(
      String treeUri, String slotId, Uint8List zipBytes) async {
    await _channel.invokeMethod<void>('writeSave', {
      'uri': treeUri,
      'slotId': slotId,
      'data': zipBytes,
    });
  }

  /// Returns the last-modified timestamp of a local save slot (ms since epoch).
  Future<int> getSlotModifiedMs(String treeUri, String slotId) async {
    final ms = await _channel.invokeMethod<int>(
        'getSlotModifiedMs', {'uri': treeUri, 'slotId': slotId});
    return ms ?? 0;
  }
}
