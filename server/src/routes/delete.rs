use axum::{
    extract::{Path, State},
    Json,
};
use common::DeleteResponse;

use crate::{error::AppError, routes::AppState, saves};

pub async fn handler(
    State(state): State<AppState>,
    Path(slot_id): Path<String>,
) -> Result<Json<DeleteResponse>, AppError> {
    if !saves::validate_slot_id(&slot_id) {
        return Err(AppError::InvalidSlotId(slot_id));
    }

    let saves_dir = state.saves_dir.as_ref().clone();
    let slot_id_clone = slot_id.clone();

    tokio::task::spawn_blocking(move || saves::delete_slot(&saves_dir, &slot_id_clone))
        .await
        .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?
        .map_err(|e| {
            if e.to_string().contains("slot not found") {
                AppError::SlotNotFound(slot_id.clone())
            } else {
                AppError::Internal(e)
            }
        })?;

    Ok(Json(DeleteResponse { deleted: slot_id }))
}
