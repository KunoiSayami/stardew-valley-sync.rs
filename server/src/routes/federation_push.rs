use axum::{
    Json,
    body::Bytes,
    extract::{Path, State},
    http::HeaderMap,
    http::StatusCode,
    response::{IntoResponse, Response},
};
use common::UploadResponse;

use crate::{error::AppError, federation::replicator::spawn_replication, routes::AppState, saves};

/// POST /api/v1/federation/push/{slot_id}
///
/// Receives a ZIP pushed by a peer. Skips conflict detection (last-write-wins).
/// Re-replicates to this server's own peers, excluding the sender.
pub async fn handler(
    State(state): State<AppState>,
    Path(slot_id): Path<String>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response, AppError> {
    if !saves::validate_slot_id(&slot_id) {
        return Err(AppError::InvalidSlotId(slot_id));
    }

    let client_ms: Option<i64> = headers
        .get("x-client-last-modified-ms")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.parse().ok());

    let sender_url: Option<String> = headers
        .get("x-federation-source")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.to_string());

    let saves_dir = state.saves_dir.as_ref().clone();
    let slot_id_clone = slot_id.clone();
    let zip_bytes = body.to_vec();

    let server_last_modified_ms = tokio::task::spawn_blocking(move || {
        saves::extract_and_write_zip(&saves_dir, &slot_id_clone, &zip_bytes, client_ms)
    })
    .await
    .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))??;

    spawn_replication(
        slot_id.clone(),
        state.saves_dir.clone(),
        state,
        client_ms,
        sender_url,
    );

    Ok((
        StatusCode::OK,
        Json(UploadResponse {
            slot_id,
            server_last_modified_ms,
        }),
    )
        .into_response())
}
