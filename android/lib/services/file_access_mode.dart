enum FileAccessMode { manageStorage, shizuku }

extension FileAccessModeX on FileAccessMode {
  String toChannelValue() => switch (this) {
        FileAccessMode.manageStorage => 'MANAGE_STORAGE',
        FileAccessMode.shizuku => 'SHIZUKU',
      };

  String get displayName => switch (this) {
        FileAccessMode.manageStorage => 'All Files Access',
        FileAccessMode.shizuku => 'Shizuku',
      };

  String get description => switch (this) {
        FileAccessMode.manageStorage =>
          'Requires granting "All Files Access" in system settings.',
        FileAccessMode.shizuku =>
          'Uses Shizuku for privileged file access. No system permission needed.',
      };
}

FileAccessMode fileAccessModeFromChannel(String v) =>
    v == 'SHIZUKU' ? FileAccessMode.shizuku : FileAccessMode.manageStorage;
