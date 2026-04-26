class SaveSlot {
  final String slotId;
  final String displayName;
  final int lastModifiedMs;
  final int sizeBytes;

  const SaveSlot({
    required this.slotId,
    required this.displayName,
    required this.lastModifiedMs,
    required this.sizeBytes,
  });

  factory SaveSlot.fromJson(Map<String, dynamic> json) => SaveSlot(
        slotId: json['slot_id'] as String,
        displayName: json['display_name'] as String,
        lastModifiedMs: json['last_modified_ms'] as int,
        sizeBytes: json['size_bytes'] as int,
      );

  DateTime get lastModified =>
      DateTime.fromMillisecondsSinceEpoch(lastModifiedMs);

  String get formattedSize {
    if (sizeBytes < 1024) return '$sizeBytes B';
    if (sizeBytes < 1024 * 1024) return '${(sizeBytes / 1024).toStringAsFixed(1)} KB';
    return '${(sizeBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}
