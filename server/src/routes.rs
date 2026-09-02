use std::{
    path::PathBuf,
    sync::{Arc, atomic::AtomicI64},
};

use tokio::sync::RwLock;

pub mod backups;
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
    /// 0 = disabled; >0 = delete backups older than this many days.
    pub backup_max_age_days: Arc<AtomicI64>,
    /// Path to the loaded config file, used to persist runtime changes.
    pub config_path: Arc<Option<PathBuf>>,
}
