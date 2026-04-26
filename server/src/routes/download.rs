use axum::{
    body::Body,
    extract::{Path, State},
    http::{HeaderMap, StatusCode, header},
    response::Response,
};
use bytes::Bytes;

use crate::{error::AppError, routes::AppState, saves};

pub async fn handler(
    State(state): State<AppState>,
    Path(slot_id): Path<String>,
) -> Result<Response, AppError> {
    if !saves::validate_slot_id(&slot_id) {
        return Err(AppError::InvalidSlotId(slot_id));
    }

    let saves_dir = state.saves_dir.as_ref().clone();
    let slot_dir = saves_dir.join(&slot_id);
    if !slot_dir.exists() {
        return Err(AppError::SlotNotFound(slot_id));
    }

    let last_modified_ms =
        saves::get_slot_mtime(&slot_dir, &slot_id).map_err(|e| AppError::Internal(e))?;

    let slot_id_clone = slot_id.clone();
    let zip_bytes: Vec<u8> =
        tokio::task::spawn_blocking(move || saves::build_zip(slot_dir, slot_id_clone))
            .await
            .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))??;

    let mut headers = HeaderMap::new();
    headers.insert(header::CONTENT_TYPE, "application/zip".parse().unwrap());
    headers.insert(
        header::CONTENT_DISPOSITION,
        format!("attachment; filename=\"{slot_id}.zip\"")
            .parse()
            .unwrap(),
    );
    headers.insert(
        "x-slot-last-modified-ms",
        last_modified_ms.to_string().parse().unwrap(),
    );

    Ok(Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, "application/zip")
        .header(
            header::CONTENT_DISPOSITION,
            format!("attachment; filename=\"{slot_id}.zip\""),
        )
        .header("x-slot-last-modified-ms", last_modified_ms.to_string())
        .body(Body::from(Bytes::from(zip_bytes)))
        .unwrap())
}
