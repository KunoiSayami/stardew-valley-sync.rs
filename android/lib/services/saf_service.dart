import 'package:flutter/services.dart';

/// Dart-side wrapper around the Kotlin file-access platform channel.
///
/// All file access uses direct java.io.File with MANAGE_EXTERNAL_STORAGE.
/// listDirectory() is used by the in-app directory browser.
///
/// channel: "com.stardewsync/saf"
/// methods:
///   checkAndRequestPermission()             -> bool
///   hasPermission()                         -> bool
///   getDefaultSavesPath()                   -> String
///   listDirectory({path})                   -> List<Map> [{name, path}]
///   savesDirExists({savesPath?})            -> bool
///   listSaves({savesPath?})                 -> List<Map> [{slotId, lastModifiedMs}]
///   readSave({slotId, savesPath?})          -> Uint8List
///   writeSave({slotId, data, savesPath?})   -> void
///   getSlotModifiedMs({slotId, savesPath?}) -> int
class SafService {
  static const _channel = MethodChannel('com.stardewsync/saf');

  Future<bool> hasPermission() async =>
      await _channel.invokeMethod<bool>('hasPermission') ?? false;

  Future<bool> checkAndRequestPermission() async =>
      await _channel.invokeMethod<bool>('checkAndRequestPermission') ?? false;

  Future<String> getDefaultSavesPath() async =>
      await _channel.invokeMethod<String>('getDefaultSavesPath') ?? '';

  /// Lists subdirectories of [path] for the in-app directory browser.
  Future<List<({String name, String path})>> listDirectory(String path) async {
    final raw =
        await _channel.invokeListMethod<Map>('listDirectory', {'path': path});
    return (raw ?? [])
        .map((m) => (name: m['name'] as String, path: m['path'] as String))
        .toList();
  }

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

  Future<String> getFileAccessMode() async =>
      await _channel.invokeMethod<String>('getFileAccessMode') ?? 'MANAGE_STORAGE';

  Future<void> setFileAccessMode(String mode) async =>
      await _channel.invokeMethod<void>('setFileAccessMode', {'mode': mode});

  Future<bool> isShizukuAvailable() async =>
      await _channel.invokeMethod<bool>('isShizukuAvailable') ?? false;
}
