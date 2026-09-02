use axum::{
    Json,
    extract::{Path, Query, State},
};
use common::{BackupListResponse, DeleteBackupResponse};
use serde::Deserialize;

use crate::{error::AppError, routes::AppState, saves};

#[derive(Deserialize)]
pub struct PurgeParams {
    pub days: u64,
}

pub async fn list_handler(
    State(state): State<AppState>,
) -> Result<Json<BackupListResponse>, AppError> {
    let saves_dir = state.saves_dir.as_ref().clone();
    let backups = tokio::task::spawn_blocking(move || saves::list_backups(&saves_dir))
        .await
        .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))??;
    Ok(Json(BackupListResponse { backups }))
}

pub async fn purge_handler(
    State(state): State<AppState>,
    Query(params): Query<PurgeParams>,
) -> Result<Json<serde_json::Value>, AppError> {
    let saves_dir = state.saves_dir.as_ref().clone();
    let count =
        tokio::task::spawn_blocking(move || saves::purge_old_backups(&saves_dir, params.days))
            .await
            .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))??;
    Ok(Json(serde_json::json!({ "deleted": count })))
}

pub async fn delete_handler(
    State(state): State<AppState>,
    Path(backup_name): Path<String>,
) -> Result<Json<DeleteBackupResponse>, AppError> {
    let saves_dir = state.saves_dir.as_ref().clone();
    let name_clone = backup_name.clone();
    tokio::task::spawn_blocking(move || saves::delete_backup(&saves_dir, &name_clone))
        .await
        .map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?
        .map_err(|e| {
            let msg = e.to_string();
            if msg.contains("not found") {
                AppError::SlotNotFound(backup_name.clone())
            } else if msg.contains("invalid backup name") {
                AppError::InvalidSlotId(backup_name.clone())
            } else {
                AppError::Internal(e)
            }
        })?;
    Ok(Json(DeleteBackupResponse {
        deleted: backup_name,
    }))
}
