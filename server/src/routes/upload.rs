use axum::{
    body::Bytes,
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use common::{ConflictResponse, UploadResponse};
use serde::Deserialize;

use crate::{error::AppError, routes::AppState, saves};

#[derive(Deserialize)]
pub struct UploadQuery {
    #[serde(default)]
    pub force: bool,
}

pub async fn handler_with_conflict_check(
    State(state): State<AppState>,
    Path(slot_id): Path<String>,
    Query(query): Query<UploadQuery>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response, AppError> {
    if !saves::validate_slot_id(&slot_id) {
        return Err(AppError::InvalidSlotId(slot_id));
    }

    // Optional client timestamp for conflict detection
    let client_ms: Option<i64> = headers
        .get("x-client-last-modified-ms")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.parse().ok());

    let saves_dir = state.saves_dir.as_ref().clone();
    let slot_dir = saves_dir.join(&slot_id);

    // Conflict check: return 409 if server copy is strictly newer and client didn't force
    if let Some(client_ms) = client_ms {
        if slot_dir.exists() && !query.force {
            let server_ms =
                saves::get_slot_mtime(&slot_dir, &slot_id).map_err(AppError::Internal)?;
            if server_ms > client_ms {
                return Ok((
                    StatusCode::CONFLICT,
                    Json(ConflictResponse {
                        error: "conflict".to_string(),
                        server_last_modified_ms: server_ms,
                        client_last_modified_ms: client_ms,
                        detail: "Server copy is newer. Use force=true to overwrite.".to_string(),
                    }),
                )
                    .into_response());
            }
        }
    }

    let is_new = !slot_dir.exists();
    let saves_dir_clone = saves_dir.clone();
    let slot_id_clone = slot_id.clone();
    let zip_bytes = body.to_vec();

    let server_last_modified_ms = tokio::task::spawn_blocking(move || {
        saves::extract_and_write_zip(&saves_dir_clone, &slot_id_clone, &zip_bytes)
    })
    .await
    .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))??;

    let status = if is_new {
        StatusCode::CREATED
    } else {
        StatusCode::OK
    };

    Ok((
        status,
        Json(UploadResponse {
            slot_id,
            server_last_modified_ms,
        }),
    )
        .into_response())
}
