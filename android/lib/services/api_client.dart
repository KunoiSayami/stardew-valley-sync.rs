import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../models/save_slot.dart';

class ConflictException implements Exception {
  final int serverLastModifiedMs;
  final int clientLastModifiedMs;
  ConflictException(this.serverLastModifiedMs, this.clientLastModifiedMs);
}

class ApiClient {
  final Dio _dio;
  final String pin;

  ApiClient({required String baseUrl, required this.pin})
      : _dio = Dio(
          BaseOptions(
            baseUrl: baseUrl,
            connectTimeout: const Duration(seconds: 5),
            receiveTimeout: const Duration(seconds: 30),
            headers: {'x-sync-pin': pin},
          ),
        );

  Future<Map<String, dynamic>> health() async {
    final resp = await Dio().get('${_dio.options.baseUrl}/health');
    return resp.data as Map<String, dynamic>;
  }

  Future<List<SaveSlot>> listSaves() async {
    final resp = await _dio.get('/api/v1/saves');
    final slots = (resp.data['slots'] as List)
        .map((e) => SaveSlot.fromJson(e as Map<String, dynamic>))
        .toList();
    return slots;
  }

  /// Downloads a save slot and returns the raw ZIP bytes + server timestamp.
  Future<({Uint8List zipBytes, int serverLastModifiedMs})> downloadSave(
      String slotId) async {
    final resp = await _dio.get(
      '/api/v1/saves/$slotId/download',
      options: Options(responseType: ResponseType.bytes),
    );
    final serverMs = int.tryParse(
            resp.headers.value('x-slot-last-modified-ms') ?? '') ??
        0;
    return (
      zipBytes: Uint8List.fromList(resp.data as List<int>),
      serverLastModifiedMs: serverMs,
    );
  }

  /// Uploads ZIP bytes for [slotId].
  ///
  /// Throws [ConflictException] if the server copy is newer and [force] is false.
  Future<void> uploadSave(
    String slotId,
    Uint8List zipBytes,
    int clientLastModifiedMs, {
    bool force = false,
  }) async {
    try {
      await _dio.post(
        '/api/v1/saves/$slotId/upload${force ? '?force=true' : ''}',
        data: zipBytes,
        options: Options(
          headers: {
            'content-type': 'application/zip',
            'x-client-last-modified-ms': clientLastModifiedMs.toString(),
          },
        ),
      );
    } on DioException catch (e) {
      if (e.response?.statusCode == 409) {
        final body = e.response?.data as Map<String, dynamic>?;
        throw ConflictException(
          (body?['server_last_modified_ms'] as num?)?.toInt() ?? 0,
          clientLastModifiedMs,
        );
      }
      rethrow;
    }
  }

  Future<void> deleteSlot(String slotId) async {
    await _dio.delete('/api/v1/saves/$slotId');
  }
}
