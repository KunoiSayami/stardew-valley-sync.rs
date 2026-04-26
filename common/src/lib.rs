use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SaveSlotInfo {
    pub slot_id: String,
    pub display_name: String,
    pub last_modified_ms: i64,
    pub size_bytes: u64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SaveListResponse {
    pub slots: Vec<SaveSlotInfo>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct UploadResponse {
    pub slot_id: String,
    pub server_last_modified_ms: i64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ConflictResponse {
    pub error: String,
    pub server_last_modified_ms: i64,
    pub client_last_modified_ms: i64,
    pub detail: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct HealthResponse {
    pub ok: bool,
    pub version: String,
    pub platform: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ApiErrorResponse {
    pub error: String,
    pub detail: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DeleteResponse {
    pub deleted: String,
}
