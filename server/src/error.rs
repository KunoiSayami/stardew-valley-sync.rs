use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use common::ApiErrorResponse;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("slot not found: {0}")]
    SlotNotFound(String),

    #[error("invalid slot id: {0}")]
    InvalidSlotId(String),

    #[error("saves directory unreadable: {0}")]
    SavesDirUnreadable(#[from] std::io::Error),

    #[error("zip error: {0}")]
    ZipError(#[from] zip::result::ZipError),

    #[error("internal error: {0}")]
    Internal(#[from] anyhow::Error),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error, detail) = match &self {
            AppError::SlotNotFound(id) => {
                (StatusCode::NOT_FOUND, "slot_not_found", Some(id.clone()))
            }
            AppError::InvalidSlotId(id) => {
                (StatusCode::BAD_REQUEST, "invalid_slot_id", Some(id.clone()))
            }
            AppError::SavesDirUnreadable(e) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "saves_dir_unreadable",
                Some(e.to_string()),
            ),
            AppError::ZipError(e) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "zip_error",
                Some(e.to_string()),
            ),
            AppError::Internal(e) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "internal_error",
                Some(e.to_string()),
            ),
        };
        (
            status,
            Json(ApiErrorResponse {
                error: error.to_string(),
                detail,
            }),
        )
            .into_response()
    }
}
