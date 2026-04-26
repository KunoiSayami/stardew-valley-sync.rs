use std::{net::SocketAddr, sync::Arc};

use axum::{
    routing::{delete, get, post},
    Router,
};
use clap::Parser;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use tower_http::{limit::RequestBodyLimitLayer, trace::TraceLayer};
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

mod auth;
mod config;
mod error;
mod routes;
mod saves;

use auth::PinAuthLayer;
use config::Config;
use routes::{
    delete::handler as delete_handler, download::handler as download_handler,
    health::handler as health_handler, saves_list::handler as saves_list_handler,
    upload::handler_with_conflict_check as upload_handler, AppState,
};

const VERSION: &str = env!("CARGO_PKG_VERSION");
const MDNS_SERVICE_TYPE: &str = "_stardewsync._tcp.local.";
const MDNS_INSTANCE: &str = "StardewSync";

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()))
        .with(tracing_subscriber::fmt::layer())
        .init();

    let cfg = Config::parse();
    let saves_dir = cfg.saves_dir_resolved();

    if !saves_dir.exists() {
        tracing::warn!(
            "Saves directory does not exist: {}. \
             Create it or pass --saves-dir.",
            saves_dir.display()
        );
    }

    info!("Saves directory: {}", saves_dir.display());
    info!("Listening on port {}", cfg.port);

    let state = AppState {
        saves_dir: Arc::new(saves_dir),
        pin: Arc::new(cfg.pin.clone()),
        version: VERSION,
    };

    // Routes that require PIN auth
    let protected = Router::new()
        .route("/api/v1/saves", get(saves_list_handler))
        .route("/api/v1/saves/{slot_id}/download", get(download_handler))
        .route("/api/v1/saves/{slot_id}/upload", post(upload_handler))
        .route("/api/v1/saves/{slot_id}", delete(delete_handler))
        .layer(PinAuthLayer::new(cfg.pin.clone()))
        .layer(RequestBodyLimitLayer::new(50 * 1024 * 1024));

    let app = Router::new()
        .route("/health", get(health_handler))
        .merge(protected)
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    // mDNS advertisement
    let mdns_port = cfg.port;
    tokio::spawn(async move {
        match advertise_mdns(mdns_port) {
            Ok(_) => info!("mDNS service advertised on port {mdns_port}"),
            Err(e) => tracing::warn!("mDNS advertisement failed: {e}"),
        }
    });

    let addr = SocketAddr::from(([0, 0, 0, 0], cfg.port));
    info!("Server running at http://{addr}");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}

fn advertise_mdns(port: u16) -> anyhow::Result<()> {
    let daemon = ServiceDaemon::new()?;
    let hostname = hostname::get()
        .ok()
        .and_then(|h| h.into_string().ok())
        .unwrap_or_else(|| "stardew-sync-server".to_string());
    let host_fqdn = format!("{hostname}.local.");

    let service = ServiceInfo::new(
        MDNS_SERVICE_TYPE,
        MDNS_INSTANCE,
        &host_fqdn,
        "",
        port,
        &[("version", VERSION)][..],
    )?;

    daemon.register(service)?;
    // Keep the daemon alive for the process lifetime
    std::mem::forget(daemon);
    Ok(())
}
