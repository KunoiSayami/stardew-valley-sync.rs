use std::path::PathBuf;

use clap::Parser;
use serde::Deserialize;

/// Fields that can be supplied via config file (all optional).
#[derive(Deserialize, Default)]
struct FileConfig {
    pin: Option<String>,
    port: Option<u16>,
    saves_dir: Option<PathBuf>,
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
}

/// Resolved configuration (all fields guaranteed to be present).
#[derive(Debug, Clone)]
pub struct Config {
    pub pin: String,
    pub port: u16,
    pub saves_dir: Option<PathBuf>,
}

impl Config {
    /// Parse CLI args and optional config file, with CLI taking precedence.
    pub fn load() -> anyhow::Result<Self> {
        let cli = Cli::parse();

        let file = match &cli.config {
            Some(path) => FileConfig::load(path)?,
            None => {
                // Try the default location silently; missing file is fine.
                let default = default_config_path();
                if default.exists() {
                    FileConfig::load(&default).unwrap_or_default()
                } else {
                    FileConfig::default()
                }
            }
        };

        let pin = cli.pin.or(file.pin).ok_or_else(|| {
            anyhow::anyhow!("PIN is required: pass --pin or set `pin` in the config file")
        })?;

        Ok(Self {
            pin,
            port: cli.port.or(file.port).unwrap_or(24742),
            saves_dir: cli.saves_dir.or(file.saves_dir),
        })
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

fn default_config_path() -> PathBuf {
    dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("stardew-sync-server")
        .join("config.toml")
}
