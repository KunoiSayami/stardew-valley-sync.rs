#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{net::SocketAddr, sync::Arc};

use axum::{
    Router,
    routing::{delete, get, post},
};
use mdns_sd::{ServiceDaemon, ServiceInfo};
use tower_http::{limit::RequestBodyLimitLayer, trace::TraceLayer};
use tracing::info;
use tracing_appender::non_blocking::WorkerGuard;
use tracing_subscriber::{EnvFilter, layer::SubscriberExt, util::SubscriberInitExt};

mod auth;
mod config;
mod error;
mod routes;
mod saves;
#[cfg(target_os = "windows")]
mod tray;

use auth::PinAuthLayer;
use config::Config;
use routes::{
    AppState, delete::handler as delete_handler, download::handler as download_handler,
    health::handler as health_handler, saves_list::handler as saves_list_handler,
    upload::handler_with_conflict_check as upload_handler,
};

const VERSION: &str = env!("CARGO_PKG_VERSION");
const MDNS_SERVICE_TYPE: &str = "_stardewsync._tcp.local.";
const MDNS_INSTANCE: &str = "StardewSync";

// ── Windows entry point ────────────────────────────────────────────────────
#[cfg(target_os = "windows")]
fn main() -> anyhow::Result<()> {
    let (cfg, config_path) = Config::load().unwrap_or_else(|e| {
        show_error(&format!("{e}"));
        std::process::exit(1);
    });
    // Hold the guard for the process lifetime so the non-blocking writer is not dropped.
    let _log_guard = init_logging(&cfg);

    match &config_path {
        Some(p) => info!("Config file: {}", p.display()),
        None => info!(
            "No config file found (default: {})",
            config::default_config_path().display()
        ),
    }
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

    let port = cfg.port;
    let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
    let rt = tokio::runtime::Runtime::new()?;
    let server_handle = rt.spawn(run_server(cfg, shutdown_rx));

    // Ctrl+C sends shutdown and posts WM_QUIT so the tray message pump exits.
    let ctrlc_tx = shutdown_tx.clone();
    rt.spawn(async move {
        if tokio::signal::ctrl_c().await.is_ok() {
            info!("Ctrl+C received, requesting shutdown");
            let _ = ctrlc_tx.send(true);
            // Wake the Win32 message pump so tray::run() can return.
            unsafe {
                windows_sys::Win32::UI::WindowsAndMessaging::PostQuitMessage(0);
            }
        }
    });

    // Blocks the main thread in the Win32 message pump until Exit is clicked.
    tray::run(port, shutdown_tx)?;
    rt.block_on(server_handle)??;
    info!("Server shut down gracefully");
    Ok(())
}

// ── Non-Windows entry point ────────────────────────────────────────────────
#[cfg(not(target_os = "windows"))]
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let (cfg, config_path) = Config::load()?;
    let _log_guard = init_logging(&cfg);

    match &config_path {
        Some(p) => info!("Config file: {}", p.display()),
        None => info!(
            "No config file found (default: {})",
            config::default_config_path().display()
        ),
    }
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

    let (_, shutdown_rx) = tokio::sync::watch::channel(false);
    axum_serve_with_ctrlc(cfg, shutdown_rx).await?;
    info!("Server shut down gracefully");
    Ok(())
}

async fn run_server(
    cfg: Config,
    mut shutdown_rx: tokio::sync::watch::Receiver<bool>,
) -> anyhow::Result<()> {
    let saves_dir = cfg.saves_dir_resolved();
    let state = AppState {
        saves_dir: Arc::new(saves_dir),
        pin: Arc::new(cfg.pin.clone()),
        version: VERSION,
    };

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

    // Clone for the timeout race — both watch the same channel.
    let mut timeout_rx = shutdown_rx.clone();

    let serve = axum::serve(listener, app).with_graceful_shutdown(async move {
        loop {
            if shutdown_rx.changed().await.is_err() {
                break;
            }
            if *shutdown_rx.borrow() {
                break;
            }
        }
        info!("Shutdown signal received, waiting for in-flight requests…");
    });

    // Race graceful drain against a 5-second hard timeout so idle keep-alive
    // connections can't prevent the process from exiting.
    tokio::select! {
        result = serve => { result?; }
        _ = async {
            // Wait for shutdown signal first, then start the timeout.
            loop {
                if timeout_rx.changed().await.is_err() { break; }
                if *timeout_rx.borrow() { break; }
            }
            tokio::time::sleep(std::time::Duration::from_secs(5)).await;
        } => {
            info!("Shutdown timeout elapsed, forcing exit");
        }
    }

    Ok(())
}

#[cfg(not(target_os = "windows"))]
async fn axum_serve_with_ctrlc(
    cfg: Config,
    _shutdown_rx: tokio::sync::watch::Receiver<bool>,
) -> anyhow::Result<()> {
    let saves_dir = cfg.saves_dir_resolved();
    let state = AppState {
        saves_dir: Arc::new(saves_dir),
        pin: Arc::new(cfg.pin.clone()),
        version: VERSION,
    };

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
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    Ok(())
}

#[cfg(not(target_os = "windows"))]
async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install SIGTERM handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    info!("Shutdown signal received, waiting for in-flight requests…");
}

#[cfg(target_os = "windows")]
fn show_error(msg: &str) {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::UI::WindowsAndMessaging::{MB_ICONERROR, MB_OK, MessageBoxW};

    let title: Vec<u16> = OsStr::new("Stardew Sync Server")
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    let text: Vec<u16> = OsStr::new(msg)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect();
    unsafe {
        MessageBoxW(
            std::ptr::null_mut(),
            text.as_ptr(),
            title.as_ptr(),
            MB_OK | MB_ICONERROR,
        );
    }
}

fn init_logging(cfg: &Config) -> Option<WorkerGuard> {
    let filter = cfg
        .log_filter
        .as_deref()
        .map(EnvFilter::new)
        .unwrap_or_else(|| EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()));

    if cfg.log_stdout {
        tracing_subscriber::registry()
            .with(filter)
            .with(tracing_subscriber::fmt::layer())
            .init();
        return None;
    }

    let log_dir = dirs::data_local_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("stardew-sync-server");

    let file_appender = tracing_appender::rolling::daily(&log_dir, "server.log");
    let (non_blocking, guard) = tracing_appender::non_blocking(file_appender);

    tracing_subscriber::registry()
        .with(filter)
        .with(tracing_subscriber::fmt::layer().with_writer(non_blocking))
        .init();

    eprintln!("Logging to {}", log_dir.join("server.log.<date>").display());
    Some(guard)
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
