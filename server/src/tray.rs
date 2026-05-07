use tokio::sync::watch;
use tracing::{info, warn};
use tray_icon::{
    TrayIconBuilder,
    menu::{CheckMenuItem, Menu, MenuEvent, MenuItem, PredefinedMenuItem},
};
use windows_sys::Win32::UI::WindowsAndMessaging::{
    DispatchMessageW, GetMessageW, MSG, TranslateMessage,
};
use winreg::{RegKey, enums::HKEY_CURRENT_USER};

const ICON_BYTES: &[u8] = include_bytes!("../assets/icon.png");
const RUN_KEY: &str = r"Software\Microsoft\Windows\CurrentVersion\Run";
const APP_NAME: &str = "StardewSyncServer";

fn load_icon() -> tray_icon::Icon {
    let img = image::load_from_memory(ICON_BYTES)
        .expect("embedded icon is valid PNG")
        .into_rgba8();
    let (w, h) = img.dimensions();
    tray_icon::Icon::from_rgba(img.into_raw(), w, h).expect("icon dimensions are valid")
}

fn is_autostart_enabled() -> bool {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let Ok(key) = hkcu.open_subkey(RUN_KEY) else {
        return false;
    };
    key.get_value::<String, _>(APP_NAME).is_ok()
}

fn set_autostart(enabled: bool) {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let Ok((key, _)) = hkcu.create_subkey(RUN_KEY) else {
        warn!("Tray: failed to open autostart registry key");
        return;
    };
    if enabled {
        let exe = match std::env::current_exe() {
            Ok(p) => p,
            Err(e) => {
                warn!("Tray: could not determine exe path: {e}");
                return;
            }
        };
        if let Err(e) = key.set_value(APP_NAME, &exe.to_string_lossy().as_ref()) {
            warn!("Tray: failed to set autostart registry value: {e}");
        } else {
            info!("Tray: autostart enabled ({})", exe.display());
        }
    } else {
        match key.delete_value(APP_NAME) {
            Ok(()) => info!("Tray: autostart disabled"),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => warn!("Tray: failed to remove autostart registry value: {e}"),
        }
    }
}

/// Blocks the calling thread in a Win32 message pump.
/// Returns when the user selects Exit from the tray menu.
pub fn run(port: u16, shutdown_tx: watch::Sender<bool>) -> anyhow::Result<()> {
    let status_item = MenuItem::new(format!("StardewSync \u{2014} port {port}"), false, None);
    let autostart_item = CheckMenuItem::new("Start on Boot", true, is_autostart_enabled(), None);
    let exit_item = MenuItem::new("Exit", true, None);

    let menu = Menu::new();
    menu.append(&status_item)?;
    menu.append(&PredefinedMenuItem::separator())?;
    menu.append(&autostart_item)?;
    menu.append(&PredefinedMenuItem::separator())?;
    menu.append(&exit_item)?;

    let _tray = TrayIconBuilder::new()
        .with_menu(Box::new(menu))
        .with_tooltip(format!("Stardew Sync Server (:{port})"))
        .with_icon(load_icon())
        .build()?;

    let autostart_id = autostart_item.id().clone();
    let exit_id = exit_item.id().clone();
    let menu_channel = MenuEvent::receiver();

    // Standard Win32 message pump. tray-icon dispatches tray events via
    // WM_USER messages delivered to its hidden window, so this loop must run
    // on the same thread that created the TrayIcon.
    unsafe {
        let mut msg = std::mem::zeroed::<MSG>();
        loop {
            // Drain pending menu events before blocking on GetMessage.
            while let Ok(event) = menu_channel.try_recv() {
                if event.id == exit_id {
                    info!("Tray: Exit clicked, requesting shutdown");
                    let _ = shutdown_tx.send(true);
                    return Ok(());
                } else if event.id == autostart_id {
                    let enabled = autostart_item.is_checked();
                    set_autostart(enabled);
                }
            }

            // GetMessageW blocks until a message arrives (returns 0 on WM_QUIT,
            // -1 on error, positive otherwise).
            let ret = GetMessageW(&mut msg, std::ptr::null_mut(), 0, 0);
            if ret == 0 || ret == -1 {
                break;
            }
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }

    Ok(())
}
