use std::{
    fs,
    io::{self, Write},
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};


use anyhow::{Context, anyhow};
use common::SaveSlotInfo;
use regex::Regex;
use tempfile::TempDir;

static SLOT_ID_RE: std::sync::OnceLock<Regex> = std::sync::OnceLock::new();

pub fn slot_id_regex() -> &'static Regex {
    SLOT_ID_RE.get_or_init(|| Regex::new(r"^[A-Za-z0-9_\-]{1,64}$").unwrap())
}

pub fn validate_slot_id(slot_id: &str) -> bool {
    slot_id_regex().is_match(slot_id)
}

fn system_time_to_ms(t: SystemTime) -> i64 {
    t.duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Returns mtime of the most-recently modified file in the slot dir.
pub fn get_slot_mtime(slot_dir: &Path, slot_id: &str) -> anyhow::Result<i64> {
    let main_file = slot_dir.join(slot_id);
    let info_file = slot_dir.join("SaveGameInfo");

    let mt_main = fs::metadata(&main_file)
        .and_then(|m| m.modified())
        .unwrap_or(UNIX_EPOCH);
    let mt_info = fs::metadata(&info_file)
        .and_then(|m| m.modified())
        .unwrap_or(UNIX_EPOCH);

    Ok(system_time_to_ms(mt_main.max(mt_info)))
}

/// Checks whether a directory contains a valid Stardew save slot.
fn is_valid_slot(dir: &Path) -> bool {
    let name = match dir.file_name().and_then(|n| n.to_str()) {
        Some(n) => n,
        None => return false,
    };
    dir.join(name).exists() && dir.join("SaveGameInfo").exists()
}

/// Lists all valid save slots in `saves_dir`.
pub fn list_slots(saves_dir: &Path) -> anyhow::Result<Vec<SaveSlotInfo>> {
    let mut slots = Vec::new();
    for entry in fs::read_dir(saves_dir).context("reading saves directory")? {
        let entry = entry?;
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        if !is_valid_slot(&path) {
            continue;
        }
        let slot_id = path.file_name().unwrap().to_string_lossy().to_string();
        if !validate_slot_id(&slot_id) {
            continue;
        }

        let display_name = slot_id
            .rsplitn(2, '_')
            .nth(1)
            .unwrap_or(&slot_id)
            .to_string();

        let last_modified_ms = get_slot_mtime(&path, &slot_id).unwrap_or(0);

        let size_bytes = {
            let main_size = fs::metadata(path.join(&slot_id))
                .map(|m| m.len())
                .unwrap_or(0);
            let info_size = fs::metadata(path.join("SaveGameInfo"))
                .map(|m| m.len())
                .unwrap_or(0);
            main_size + info_size
        };

        slots.push(SaveSlotInfo {
            slot_id,
            display_name,
            last_modified_ms,
            size_bytes,
        });
    }
    slots.sort_by(|a, b| b.last_modified_ms.cmp(&a.last_modified_ms));
    Ok(slots)
}

/// Builds a ZIP archive containing the two save files for `slot_id`.
/// Returns the raw bytes of the ZIP.
pub fn build_zip(slot_dir: PathBuf, slot_id: String) -> anyhow::Result<Vec<u8>> {
    let buf = Vec::new();
    let cursor = io::Cursor::new(buf);
    let mut zip = zip::ZipWriter::new(cursor);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);

    // Main save file
    let main_path = slot_dir.join(&slot_id);
    let main_bytes = fs::read(&main_path)
        .with_context(|| format!("reading main save file: {}", main_path.display()))?;
    zip.start_file(&slot_id, options)?;
    zip.write_all(&main_bytes)?;

    // SaveGameInfo
    let info_path = slot_dir.join("SaveGameInfo");
    let info_bytes = fs::read(&info_path)
        .with_context(|| format!("reading SaveGameInfo: {}", info_path.display()))?;
    zip.start_file("SaveGameInfo", options)?;
    zip.write_all(&info_bytes)?;

    let cursor = zip.finish()?;
    Ok(cursor.into_inner())
}

/// Creates a timestamped backup of an existing slot dir, then writes a new slot
/// from the ZIP bytes. Returns the mtime of the newly written slot (ms).
///
/// If `client_ms` is `Some`, the extracted files' mtimes are set to that
/// timestamp so the server preserves the original save timestamp.
pub fn extract_and_write_zip(
    saves_dir: &Path,
    slot_id: &str,
    zip_bytes: &[u8],
    client_ms: Option<i64>,
) -> anyhow::Result<i64> {
    let slot_dir = saves_dir.join(slot_id);

    // --- Validate ZIP before touching the FS ---
    let cursor = io::Cursor::new(zip_bytes);
    let mut archive = zip::ZipArchive::new(cursor)?;

    let has_main = (0..archive.len()).any(|i| {
        archive
            .by_index(i)
            .map(|f| f.name() == slot_id)
            .unwrap_or(false)
    });
    let has_info = (0..archive.len()).any(|i| {
        archive
            .by_index(i)
            .map(|f| f.name() == "SaveGameInfo")
            .unwrap_or(false)
    });

    if !has_main || !has_info {
        return Err(anyhow!("ZIP must contain '{}' and 'SaveGameInfo'", slot_id));
    }

    // --- Backup existing slot if present ---
    if slot_dir.exists() {
        let now_ms = system_time_to_ms(SystemTime::now());
        let backup_dir = saves_dir.join(format!("{slot_id}.bak.{now_ms}"));
        fs::rename(&slot_dir, &backup_dir)
            .with_context(|| format!("backing up slot to {}", backup_dir.display()))?;
    }

    // --- Extract into a temp dir, then rename into place ---
    let tmp = TempDir::new_in(saves_dir).context("creating temp dir")?;
    let tmp_slot = tmp.path().join(slot_id);
    fs::create_dir_all(&tmp_slot)?;

    let cursor = io::Cursor::new(zip_bytes);
    let mut archive = zip::ZipArchive::new(cursor)?;
    for i in 0..archive.len() {
        let mut file = archive.by_index(i)?;
        let dest = tmp_slot.join(file.name());
        let mut out = fs::File::create(&dest)?;
        io::copy(&mut file, &mut out)?;
    }

    // Atomically move temp slot into saves_dir
    let final_dir = saves_dir.join(slot_id);
    fs::rename(&tmp_slot, &final_dir)
        .with_context(|| format!("moving temp slot to {}", final_dir.display()))?;
    // Let TempDir clean up the (now empty) wrapper dir on drop.

    if let Some(ms) = client_ms {
        let ft = filetime::FileTime::from_unix_time(ms / 1000, ((ms % 1000) * 1_000_000) as u32);
        let main_file = final_dir.join(slot_id);
        let info_file = final_dir.join("SaveGameInfo");
        filetime::set_file_mtime(&main_file, ft)
            .with_context(|| format!("setting mtime on {}", main_file.display()))?;
        filetime::set_file_mtime(&info_file, ft)
            .with_context(|| format!("setting mtime on {}", info_file.display()))?;
    }

    get_slot_mtime(&final_dir, slot_id)
}

/// Deletes a slot directory from saves_dir.
pub fn delete_slot(saves_dir: &Path, slot_id: &str) -> anyhow::Result<()> {
    let slot_dir = saves_dir.join(slot_id);
    if !slot_dir.exists() {
        return Err(anyhow!("slot not found: {slot_id}"));
    }
    fs::remove_dir_all(&slot_dir)
        .with_context(|| format!("deleting slot dir: {}", slot_dir.display()))
}
