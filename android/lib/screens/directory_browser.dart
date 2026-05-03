import 'package:flutter/material.dart';

import '../services/saf_service.dart';

/// Full-screen directory browser that returns the selected folder path.
/// Navigates using direct File access (requires MANAGE_EXTERNAL_STORAGE).
class DirectoryBrowser extends StatefulWidget {
  final SafService saf;
  final String initialPath;

  const DirectoryBrowser({
    super.key,
    required this.saf,
    required this.initialPath,
  });

  @override
  State<DirectoryBrowser> createState() => _DirectoryBrowserState();
}

class _DirectoryBrowserState extends State<DirectoryBrowser> {
  late String _currentPath;
  List<({String name, String path})> _entries = [];
  bool _loading = false;
  String? _error;

  // Breadcrumb stack: list of (label, path) pairs.
  final List<({String label, String path})> _stack = [];

  @override
  void initState() {
    super.initState();
    _currentPath = widget.initialPath;
    _load(_currentPath, label: _lastName(widget.initialPath));
  }

  String _lastName(String path) {
    final parts = path.split('/');
    return parts.lastWhere((p) => p.isNotEmpty, orElse: () => path);
  }

  Future<void> _load(String path, {required String label}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final entries = await widget.saf.listDirectory(path);
      if (!mounted) return;
      setState(() {
        _currentPath = path;
        _entries = entries;
        _loading = false;
      });
      // Push to breadcrumb if navigating forward.
      if (_stack.isEmpty || _stack.last.path != path) {
        _stack.add((label: label, path: path));
      }
    } catch (e) {
      if (mounted) setState(() { _loading = false; _error = e.toString(); });
    }
  }

  void _navigateTo(String path, String name) {
    _load(path, label: name);
  }

  void _navigateBreadcrumb(int index) {
    final target = _stack[index];
    _stack.removeRange(index + 1, _stack.length);
    _load(target.path, label: target.label);
  }

  void _goUp() {
    if (_stack.length <= 1) return;
    _stack.removeLast();
    final parent = _stack.last;
    _stack.removeLast(); // _load re-adds it
    _load(parent.path, label: parent.label);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Choose folder'),
        leading: _stack.length > 1
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: _goUp,
              )
            : null,
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(_currentPath),
            child: const Text('Select'),
          ),
        ],
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Breadcrumb bar
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Row(
              children: [
                for (int i = 0; i < _stack.length; i++) ...[
                  if (i > 0)
                    const Icon(Icons.chevron_right, size: 16),
                  GestureDetector(
                    onTap: i < _stack.length - 1
                        ? () => _navigateBreadcrumb(i)
                        : null,
                    child: Text(
                      _stack[i].label,
                      style: TextStyle(
                        fontSize: 13,
                        color: i < _stack.length - 1
                            ? Theme.of(context).colorScheme.primary
                            : null,
                        fontWeight: i == _stack.length - 1
                            ? FontWeight.bold
                            : FontWeight.normal,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const Divider(height: 1),
          if (_loading)
            const LinearProgressIndicator()
          else if (_error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('Error: $_error',
                  style: TextStyle(color: Theme.of(context).colorScheme.error)),
            )
          else if (_entries.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('No subdirectories here.\nTap "Select" to choose this folder.'),
            )
          else
            Expanded(
              child: ListView.builder(
                itemCount: _entries.length,
                itemBuilder: (ctx, i) {
                  final entry = _entries[i];
                  return ListTile(
                    leading: const Icon(Icons.folder),
                    title: Text(entry.name),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _navigateTo(entry.path, entry.name),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}
