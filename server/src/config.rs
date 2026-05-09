use std::path::PathBuf;

use clap::Parser;
use serde::Deserialize;

/// A peer server in the federation.
#[derive(Deserialize, Clone, Debug)]
pub struct PeerConfig {
    pub url: String,
}

/// Fields that can be supplied via config file (all optional).
#[derive(Deserialize, Default)]
struct FileConfig {
    pin: Option<String>,
    port: Option<u16>,
    saves_dir: Option<PathBuf>,
    log_filter: Option<String>,
    federation_token: Option<String>,
    #[serde(default)]
    peers: Vec<PeerConfig>,
}

impl FileConfig {
    fn load(path: &PathBuf) -> anyhow::Result<Self> {
        let text = std::fs::read_to_string(path)?;
        Ok(toml::from_str(&text)?)
    }
}

#[derive(Parser, Debug, Clone)]
#[command(
    name = "stardew-sync-server",
    about = "Stardew Valley save sync server"
)]
struct Cli {
    /// Path to a TOML config file
    #[arg(long, value_name = "FILE")]
    config: Option<PathBuf>,

    /// Shared PIN (4–8 digits) required by the Android client
    #[arg(long)]
    pin: Option<String>,

    /// Port to listen on
    #[arg(long)]
    port: Option<u16>,

    /// Path to the Stardew Valley Saves directory (auto-detected if omitted)
    #[arg(long)]
    saves_dir: Option<PathBuf>,

    /// Write logs to stdout instead of the default log file
    #[arg(long, default_value_t = false)]
    log_stdout: bool,
}

/// Resolved configuration (all fields guaranteed to be present).
#[derive(Debug, Clone)]
pub struct Config {
    pub pin: String,
    pub port: u16,
    pub saves_dir: Option<PathBuf>,
    pub log_filter: Option<String>,
    pub log_stdout: bool,
    pub federation_token: Option<String>,
    pub static_peers: Vec<PeerConfig>,
}

impl Config {
    /// Parse CLI args and optional config file, with CLI taking precedence.
    pub fn load() -> anyhow::Result<(Self, Option<PathBuf>)> {
        let cli = Cli::parse();

        let (file, config_path) = match &cli.config {
            Some(path) => (FileConfig::load(path)?, Some(path.clone())),
            None => {
                // 1. config.toml next to the binary / in the working directory
                // 2. platform config dir (~/.config/stardew-sync-server/config.toml)
                let candidates = [PathBuf::from("config.toml"), default_config_path()];
                match candidates.iter().find(|p| p.exists()) {
                    Some(path) => (
                        FileConfig::load(path).unwrap_or_default(),
                        Some(path.clone()),
                    ),
                    None => (FileConfig::default(), None),
                }
            }
        };

        let pin = cli.pin.or(file.pin).ok_or_else(|| {
            anyhow::anyhow!("PIN is required: pass --pin or set `pin` in the config file")
        })?;

        Ok((
            Self {
                pin,
                port: cli.port.or(file.port).unwrap_or(24742),
                saves_dir: cli.saves_dir.or(file.saves_dir),
                log_filter: file.log_filter,
                log_stdout: cli.log_stdout,
                federation_token: file.federation_token,
                static_peers: file.peers,
            },
            config_path,
        ))
    }

    pub fn saves_dir_resolved(&self) -> PathBuf {
        if let Some(ref dir) = self.saves_dir {
            return dir.clone();
        }
        detect_saves_dir()
    }
}

pub fn detect_saves_dir() -> PathBuf {
    // Windows: %APPDATA%\StardewValley\Saves
    // Linux/macOS: ~/.config/StardewValley/Saves
    let base = if cfg!(target_os = "windows") {
        dirs::data_dir()
    } else {
        dirs::config_dir()
    };
    base.unwrap_or_else(|| PathBuf::from("."))
        .join("StardewValley")
        .join("Saves")
}

pub fn default_config_path() -> PathBuf {
    dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("stardew-sync-server")
        .join("config.toml")
}
