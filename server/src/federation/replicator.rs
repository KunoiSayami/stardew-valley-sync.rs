use std::{path::PathBuf, sync::Arc};

use tracing::{info, warn};

use crate::{routes::AppState, saves};

/// Called after any successful save write. Spawns fire-and-forget replication
/// tasks to all known peers, excluding `exclude_url` (the sender, if this write
/// was itself triggered by a federation push).
pub fn spawn_replication(
    slot_id: String,
    saves_dir: Arc<PathBuf>,
    state: AppState,
    client_ms: Option<i64>,
    exclude_url: Option<String>,
) {
    let token = match state.federation_token.as_ref() {
        Some(t) => t.clone(),
        None => return,
    };

    let peers_lock = state.peers.clone();
    let http_client = state.http_client.clone();
    let own_url = format!("http://127.0.0.1:{}", state.own_port);

    tokio::spawn(async move {
        let peers: Vec<String> = {
            let list = peers_lock.read().await;
            list.iter()
                .map(|p| p.url.clone())
                .filter(|url| exclude_url.as_deref() != Some(url.as_str()))
                .collect()
        };

        if peers.is_empty() {
            return;
        }

        let slot_dir = saves_dir.join(&slot_id);
        let zip_bytes = match tokio::task::spawn_blocking({
            let slot_id = slot_id.clone();
            move || saves::build_zip(slot_dir, slot_id)
        })
        .await
        {
            Ok(Ok(b)) => Arc::new(b),
            Ok(Err(e)) => {
                warn!("federation: failed to build ZIP for {slot_id}: {e}");
                return;
            }
            Err(e) => {
                warn!("federation: spawn_blocking panicked for {slot_id}: {e}");
                return;
            }
        };

        let mut handles = Vec::with_capacity(peers.len());
        for peer_url in peers {
            let zip = zip_bytes.clone();
            let token = token.clone();
            let client = http_client.clone();
            let sid = slot_id.clone();
            let own = own_url.clone();
            handles.push(tokio::spawn(async move {
                push_to_peer(&client, &peer_url, &sid, &token, &own, &zip, client_ms).await;
            }));
        }
        for h in handles {
            let _ = h.await;
        }
    });
}

async fn push_to_peer(
    client: &reqwest::Client,
    peer_url: &str,
    slot_id: &str,
    token: &str,
    own_url: &str,
    zip_bytes: &[u8],
    client_ms: Option<i64>,
) {
    let url = format!("{peer_url}/api/v1/federation/push/{slot_id}");
    let mut builder = client
        .post(&url)
        .header("x-federation-token", token)
        .header("x-federation-source", own_url)
        .header("content-type", "application/zip")
        .body(zip_bytes.to_vec());

    if let Some(ms) = client_ms {
        builder = builder.header("x-client-last-modified-ms", ms.to_string());
    }

    match builder.send().await {
        Ok(resp) if resp.status().is_success() => {
            info!(
                "federation: replicated {slot_id} to {peer_url} ({})",
                resp.status()
            );
        }
        Ok(resp) => {
            warn!(
                "federation: peer {peer_url} rejected push for {slot_id}: HTTP {}",
                resp.status()
            );
        }
        Err(e) => {
            warn!("federation: failed to reach {peer_url} for {slot_id}: {e}");
        }
    }
}
