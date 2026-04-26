import 'dart:async';

import 'package:multicast_dns/multicast_dns.dart';

class DiscoveredServer {
  final String host;
  final int port;
  DiscoveredServer(this.host, this.port);

  String get baseUrl => 'http://$host:$port';

  @override
  String toString() => '$host:$port';
}

/// Scans the LAN for StardewSync servers via mDNS.
/// Calls [onFound] for each server discovered; completes when the timeout elapses.
Future<List<DiscoveredServer>> discoverServers({
  Duration timeout = const Duration(seconds: 5),
}) async {
  const serviceType = '_stardewsync._tcp';
  final results = <DiscoveredServer>[];
  final seen = <String>{};

  final client = MDnsClient();
  await client.start();

  try {
    final completer = Completer<void>();
    Timer(timeout, () {
      if (!completer.isCompleted) completer.complete();
    });

    await for (final PtrResourceRecord ptr in client
        .lookup<PtrResourceRecord>(ResourceRecordQuery.serverPointer(serviceType))
        .timeout(timeout, onTimeout: (sink) => sink.close())) {
      await for (final SrvResourceRecord srv in client
          .lookup<SrvResourceRecord>(
              ResourceRecordQuery.service(ptr.domainName))
          .timeout(const Duration(seconds: 2), onTimeout: (sink) => sink.close())) {
        await for (final IPAddressResourceRecord ip in client
            .lookup<IPAddressResourceRecord>(
                ResourceRecordQuery.addressIPv4(srv.target))
            .timeout(const Duration(seconds: 2),
                onTimeout: (sink) => sink.close())) {
          final key = '${ip.address.address}:${srv.port}';
          if (!seen.contains(key)) {
            seen.add(key);
            results.add(DiscoveredServer(ip.address.address, srv.port));
          }
        }
      }
    }
  } finally {
    client.stop();
  }

  return results;
}
