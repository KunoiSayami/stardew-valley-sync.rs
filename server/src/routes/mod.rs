use std::{path::PathBuf, sync::Arc};

pub mod delete;
pub mod download;
pub mod health;
pub mod saves_list;
pub mod upload;

#[derive(Clone)]
#[allow(dead_code)]
pub struct AppState {
    pub saves_dir: Arc<PathBuf>,
    pub pin: Arc<String>,
    pub version: &'static str,
}
