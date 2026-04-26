use axum::{extract::State, Json};
use common::SaveListResponse;

use crate::{error::AppError, routes::AppState, saves};

pub async fn handler(State(state): State<AppState>) -> Result<Json<SaveListResponse>, AppError> {
    let saves_dir = state.saves_dir.as_ref().clone();
    let slots = tokio::task::spawn_blocking(move || saves::list_slots(&saves_dir))
        .await
        .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))??;
    Ok(Json(SaveListResponse { slots }))
}
