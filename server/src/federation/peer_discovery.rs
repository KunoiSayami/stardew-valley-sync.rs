use std::sync::Arc;

use mdns_sd::{ServiceDaemon, ServiceEvent};
use tokio::sync::RwLock;
use tracing::{debug, info, warn};

use crate::{
    config::PeerConfig,
    routes::{LivePeer, PeerSource},
};

const MDNS_SERVICE_TYPE: &str = "_stardewsync._tcp.local.";

/// Seeds the peer list from static config, then runs an mDNS browser to add
/// and remove discovered peers. Never returns — run inside `tokio::spawn`.
pub async fn run_peer_discovery(
    static_peers: Vec<PeerConfig>,
    peers: Arc<RwLock<Vec<LivePeer>>>,
    own_port: u16,
) {
    {
        let mut list = peers.write().await;
        for p in &static_peers {
            if !list.iter().any(|x| x.url == p.url) {
                list.push(LivePeer {
                    url: p.url.clone(),
                    source: PeerSource::Static,
                });
                info!("federation: static peer added: {}", p.url);
            }
        }
    }

    let peers_clone = peers.clone();
    tokio::task::spawn_blocking(move || {
        browse_mdns(peers_clone, own_port, static_peers);
    })
    .await
    .unwrap_or_else(|e| warn!("federation: mDNS browser task panicked: {e}"));
}

fn browse_mdns(peers: Arc<RwLock<Vec<LivePeer>>>, own_port: u16, static_peers: Vec<PeerConfig>) {
    let daemon = match ServiceDaemon::new() {
        Ok(d) => d,
        Err(e) => {
            warn!("federation: mDNS daemon failed to start: {e}");
            return;
        }
    };

    let receiver = match daemon.browse(MDNS_SERVICE_TYPE) {
        Ok(r) => r,
        Err(e) => {
            warn!("federation: mDNS browse failed: {e}");
            return;
        }
    };

    let rt = tokio::runtime::Handle::current();

    loop {
        match receiver.recv() {
            Ok(ServiceEvent::ServiceResolved(info)) => {
                let port = info.get_port();
                if port == own_port {
                    debug!("federation: mDNS discovered self, skipping");
                    continue;
                }

                let addr = info.get_addresses_v4().into_iter().next();
                let url = match addr {
                    Some(ip) => format!("http://{ip}:{port}"),
                    None => {
                        warn!("federation: mDNS service resolved but has no IPv4 address");
                        continue;
                    }
                };

                let is_static = static_peers.iter().any(|p| p.url == url);

                rt.block_on(async {
                    let mut list = peers.write().await;
                    if !list.iter().any(|p| p.url == url) {
                        list.push(LivePeer {
                            url: url.clone(),
                            source: if is_static {
                                PeerSource::Static
                            } else {
                                PeerSource::Mdns
                            },
                        });
                        info!("federation: mDNS peer added: {url}");
                    }
                });
            }

            Ok(ServiceEvent::ServiceRemoved(_type, fullname)) => {
                rt.block_on(async {
                    let mut list = peers.write().await;
                    let before = list.len();
                    list.retain(|p| {
                        !(p.source == PeerSource::Mdns && fullname.contains(p.url.as_str()))
                    });
                    if list.len() < before {
                        info!("federation: mDNS peer removed (fullname={fullname})");
                    }
                });
            }

            Ok(_) => {}
            Err(e) => {
                warn!("federation: mDNS browser channel error: {e}");
                break;
            }
        }
    }
}
