use axum::{extract::State, Json};
use common::HealthResponse;

use crate::routes::AppState;

pub async fn handler(State(state): State<AppState>) -> Json<HealthResponse> {
    Json(HealthResponse {
        ok: true,
        version: state.version.to_string(),
        platform: if cfg!(target_os = "windows") {
            "windows"
        } else {
            "linux"
        }
        .to_string(),
    })
}
