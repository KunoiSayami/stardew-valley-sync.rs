use std::{path::PathBuf, sync::Arc};

use tokio::sync::RwLock;

pub mod delete;
pub mod download;
pub mod federation_push;
pub mod health;
pub mod saves_list;
pub mod upload;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LivePeer {
    pub url: String,
    pub source: PeerSource,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum PeerSource {
    Static,
    Mdns,
}

#[derive(Clone)]
pub struct AppState {
    pub saves_dir: Arc<PathBuf>,
    pub pin: Arc<String>,
    pub version: &'static str,
    pub federation_token: Arc<Option<String>>,
    pub peers: Arc<RwLock<Vec<LivePeer>>>,
    pub http_client: reqwest::Client,
    pub own_port: u16,
}
