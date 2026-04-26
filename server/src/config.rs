use std::path::PathBuf;

use clap::Parser;

#[derive(Parser, Debug, Clone)]
#[command(
    name = "stardew-sync-server",
    about = "Stardew Valley save sync server"
)]
pub struct Config {
    /// Shared PIN (4–8 digits) required by the Android client
    #[arg(long)]
    pub pin: String,

    /// Port to listen on
    #[arg(long, default_value_t = 24742)]
    pub port: u16,

    /// Path to the Stardew Valley Saves directory (auto-detected if omitted)
    #[arg(long)]
    pub saves_dir: Option<PathBuf>,
}

impl Config {
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
