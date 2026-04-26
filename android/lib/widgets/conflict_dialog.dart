import 'package:flutter/material.dart';

/// Shows a dialog when the target save is newer than the source.
/// Returns true if the user confirms overwrite, false if they cancel.
Future<bool> showConflictDialog(
  BuildContext context, {
  required String slotId,
  required DateTime targetTime,
  required DateTime sourceTime,
  required String targetLabel,
  required String sourceLabel,
}) async {
  final result = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => AlertDialog(
      title: const Text('Newer save detected'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Slot: $slotId'),
          const SizedBox(height: 8),
          Text('$targetLabel (destination): ${_fmt(targetTime)}',
              style: const TextStyle(fontWeight: FontWeight.bold)),
          Text('$sourceLabel (source): ${_fmt(sourceTime)}'),
          const SizedBox(height: 12),
          const Text(
            'The destination is newer. Overwriting it may lose progress. '
            'Continue anyway?',
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(ctx).pop(false),
          child: const Text('Cancel'),
        ),
        TextButton(
          style: TextButton.styleFrom(foregroundColor: Colors.red),
          onPressed: () => Navigator.of(ctx).pop(true),
          child: const Text('Overwrite'),
        ),
      ],
    ),
  );
  return result ?? false;
}

String _fmt(DateTime dt) =>
    '${dt.year}-${_p(dt.month)}-${_p(dt.day)} '
    '${_p(dt.hour)}:${_p(dt.minute)}:${_p(dt.second)}';

String _p(int n) => n.toString().padLeft(2, '0');
