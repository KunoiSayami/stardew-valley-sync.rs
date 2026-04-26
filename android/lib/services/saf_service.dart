import 'package:flutter/services.dart';

/// Dart-side wrapper around the Kotlin file-access platform channel.
///
/// On Android 11+ the Stardew save directory is inside Android/data/ which
/// requires MANAGE_EXTERNAL_STORAGE ("All files access").  The Kotlin side
/// uses direct java.io.File access once that permission is granted.
///
/// All file methods accept an optional [savesPath]. When null or empty the
/// Kotlin side falls back to the standard path:
///   Android/data/com.chucklefish.stardewvalley/files/Saves
///
/// channel: "com.stardewsync/saf"
/// methods:
///   checkAndRequestPermission()          -> bool
///   hasPermission()                      -> bool
///   listSaves({savesPath?})              -> List<Map> [{slotId, lastModifiedMs}]
///   readSave({slotId, savesPath?})       -> Uint8List  (ZIP bytes)
///   writeSave({slotId, data, savesPath?})-> void
///   getSlotModifiedMs({slotId, savesPath?}) -> int
class SafService {
  static const _channel = MethodChannel('com.stardewsync/saf');

  Future<bool> hasPermission() async =>
      await _channel.invokeMethod<bool>('hasPermission') ?? false;

  Future<bool> checkAndRequestPermission() async =>
      await _channel.invokeMethod<bool>('checkAndRequestPermission') ?? false;

  Future<String> getDefaultSavesPath() async =>
      await _channel.invokeMethod<String>('getDefaultSavesPath') ?? '';

  Future<String?> pickDirectory() async =>
      await _channel.invokeMethod<String>('pickDirectory');

  Future<bool> savesDirExists({String? savesPath}) async =>
      await _channel.invokeMethod<bool>(
          'savesDirExists', {'savesPath': savesPath}) ??
      false;

  Future<List<({String slotId, int lastModifiedMs})>> listSaves(
      {String? savesPath}) async {
    final raw = await _channel
        .invokeListMethod<Map>('listSaves', {'savesPath': savesPath});
    return (raw ?? [])
        .map((m) => (
              slotId: m['slotId'] as String,
              lastModifiedMs: (m['lastModifiedMs'] as num).toInt(),
            ))
        .toList();
  }

  Future<Uint8List> readSave(String slotId, {String? savesPath}) async {
    final bytes = await _channel.invokeMethod<Uint8List>(
        'readSave', {'slotId': slotId, 'savesPath': savesPath});
    return bytes!;
  }

  Future<void> writeSave(String slotId, Uint8List zipBytes,
      {String? savesPath}) async {
    await _channel.invokeMethod<void>('writeSave', {
      'slotId': slotId,
      'data': zipBytes,
      'savesPath': savesPath,
    });
  }

  Future<int> getSlotModifiedMs(String slotId, {String? savesPath}) async {
    final ms = await _channel.invokeMethod<int>(
        'getSlotModifiedMs', {'slotId': slotId, 'savesPath': savesPath});
    return ms ?? 0;
  }
}
