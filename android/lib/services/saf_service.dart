import 'package:flutter/services.dart';

/// Dart-side wrapper around the Kotlin file-access platform channel.
///
/// On Android 11+ the Stardew save directory is inside Android/data/ which
/// requires MANAGE_EXTERNAL_STORAGE ("All files access").  The Kotlin side
/// uses direct java.io.File access once that permission is granted.
///
/// channel: "com.stardewsync/saf"
/// methods:
///   checkAndRequestPermission() -> bool  (opens Settings if needed)
///   hasPermission()             -> bool
///   listSaves()                 -> List<Map> [{slotId, lastModifiedMs}]
///   readSave(slotId)            -> Uint8List  (ZIP bytes)
///   writeSave(slotId, data)     -> void
///   getSlotModifiedMs(slotId)   -> int
class SafService {
  static const _channel = MethodChannel('com.stardewsync/saf');

  /// Returns true if MANAGE_EXTERNAL_STORAGE is already granted.
  Future<bool> hasPermission() async {
    return await _channel.invokeMethod<bool>('hasPermission') ?? false;
  }

  /// Checks permission; opens the system "All files access" settings page if
  /// not yet granted and waits for the user to return.
  /// Returns true if permission is now granted.
  Future<bool> checkAndRequestPermission() async {
    return await _channel.invokeMethod<bool>('checkAndRequestPermission') ?? false;
  }

  /// Lists save slots in Android/data/com.chucklefish.stardewvalley/files/Saves.
  Future<List<({String slotId, int lastModifiedMs})>> listSaves() async {
    final raw = await _channel.invokeListMethod<Map>('listSaves');
    return (raw ?? [])
        .map((m) => (
              slotId: m['slotId'] as String,
              lastModifiedMs: (m['lastModifiedMs'] as num).toInt(),
            ))
        .toList();
  }

  /// Builds a ZIP of the two save files and returns the raw bytes.
  Future<Uint8List> readSave(String slotId) async {
    final bytes = await _channel.invokeMethod<Uint8List>('readSave', {'slotId': slotId});
    return bytes!;
  }

  /// Extracts a ZIP and writes the save files, creating a .bak first.
  Future<void> writeSave(String slotId, Uint8List zipBytes) async {
    await _channel.invokeMethod<void>('writeSave', {
      'slotId': slotId,
      'data': zipBytes,
    });
  }

  /// Returns the last-modified timestamp of a local save slot (ms since epoch).
  Future<int> getSlotModifiedMs(String slotId) async {
    final ms = await _channel.invokeMethod<int>('getSlotModifiedMs', {'slotId': slotId});
    return ms ?? 0;
  }
}
