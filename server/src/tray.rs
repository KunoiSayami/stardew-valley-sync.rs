use tokio::sync::watch::{self, Receiver};
use tracing::{info, warn};
use tray_icon::{
    TrayIconBuilder,
    menu::{CheckMenuItem, Menu, MenuEvent, MenuItem, PredefinedMenuItem},
};
use windows_sys::Win32::{
    Foundation::{HWND, LPARAM, LRESULT, WPARAM},
    Graphics::Gdi::{COLOR_BTNFACE, GetStockObject, WHITE_BRUSH},
    UI::WindowsAndMessaging::{
        BS_DEFPUSHBUTTON, BS_PUSHBUTTON, CREATESTRUCTW, CW_USEDEFAULT, CreateWindowExW,
        DefWindowProcW, DestroyWindow, DispatchMessageW, ES_AUTOHSCROLL, ES_PASSWORD,
        GWLP_USERDATA, GetDlgItemTextW, GetMessageW, GetWindowLongPtrW, IDC_ARROW, IDCANCEL, IDOK,
        IsDialogMessageW, LoadCursorW, MB_ICONERROR, MB_OK, MSG, MessageBoxW, PostQuitMessage,
        RegisterClassExW, SW_SHOW, SetWindowLongPtrW, ShowWindow, TranslateMessage, WM_CLOSE,
        WM_COMMAND, WM_CREATE, WM_CTLCOLORDLG, WM_DESTROY, WNDCLASSEXW, WS_BORDER, WS_CAPTION,
        WS_CHILD, WS_EX_DLGMODALFRAME, WS_GROUP, WS_OVERLAPPED, WS_SYSMENU, WS_TABSTOP, WS_VISIBLE,
    },
};
use winreg::{RegKey, enums::HKEY_CURRENT_USER};

use crate::routes::{AppState, LivePeer, PeerSource};

const ICON_BYTES: &[u8] = include_bytes!("../assets/icon.png");
const RUN_KEY: &str = r"Software\Microsoft\Windows\CurrentVersion\Run";
const APP_NAME: &str = "StardewSyncServer";

// Child-control IDs inside the Add-Peer dialog.
const IDC_ADDR: u16 = 101;
const IDC_PASS: u16 = 102;
const IDC_OK: u16 = IDOK as u16;
const IDC_CANCEL: u16 = IDCANCEL as u16;

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

fn wide(s: &str) -> Vec<u16> {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;
    OsStr::new(s)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

// ── Add-Peer dialog ────────────────────────────────────────────────────────

struct DialogState {
    default_password: String,
    result: Option<(String, String)>,
}

const DIALOG_CLASS: &str = "StardewSyncAddPeer";

unsafe extern "system" fn dialog_wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_CREATE => {
            unsafe {
                let cs = &*(lparam as *const CREATESTRUCTW);
                let state_ptr = cs.lpCreateParams as *mut DialogState;
                SetWindowLongPtrW(hwnd, GWLP_USERDATA, state_ptr as isize);

                let label_style = (WS_CHILD | WS_VISIBLE) as u32;
                let edit_style =
                    (WS_CHILD | WS_VISIBLE | WS_BORDER | WS_TABSTOP) as u32 | ES_AUTOHSCROLL as u32;
                let btn_style = (WS_CHILD | WS_VISIBLE | WS_TABSTOP) as u32;

                let cls_static = wide("STATIC");
                let cls_edit = wide("EDIT");
                let cls_button = wide("BUTTON");

                CreateWindowExW(
                    0,
                    cls_static.as_ptr(),
                    wide("Server address:").as_ptr(),
                    label_style,
                    10,
                    14,
                    110,
                    16,
                    hwnd,
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                );
                CreateWindowExW(
                    0,
                    cls_edit.as_ptr(),
                    wide("http://").as_ptr(),
                    edit_style,
                    125,
                    10,
                    235,
                    22,
                    hwnd,
                    IDC_ADDR as _,
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                );

                CreateWindowExW(
                    0,
                    cls_static.as_ptr(),
                    wide("Password (optional):").as_ptr(),
                    label_style,
                    10,
                    46,
                    110,
                    16,
                    hwnd,
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                );
                let default_pw = (*state_ptr).default_password.clone();
                CreateWindowExW(
                    0,
                    cls_edit.as_ptr(),
                    wide(&default_pw).as_ptr(),
                    edit_style | ES_PASSWORD as u32,
                    125,
                    42,
                    235,
                    22,
                    hwnd,
                    IDC_PASS as _,
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                );

                CreateWindowExW(
                    0,
                    cls_button.as_ptr(),
                    wide("OK").as_ptr(),
                    btn_style | BS_DEFPUSHBUTTON as u32,
                    195,
                    76,
                    80,
                    26,
                    hwnd,
                    IDC_OK as _,
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                );
                CreateWindowExW(
                    0,
                    cls_button.as_ptr(),
                    wide("Cancel").as_ptr(),
                    btn_style | BS_PUSHBUTTON as u32,
                    280,
                    76,
                    80,
                    26,
                    hwnd,
                    IDC_CANCEL as _,
                    std::ptr::null_mut(),
                    std::ptr::null_mut(),
                );
            }
            0
        }

        WM_COMMAND => {
            let ctrl_id = (wparam & 0xFFFF) as u16;
            if ctrl_id == IDC_OK {
                unsafe {
                    let state_ptr = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *mut DialogState;
                    let addr = read_edit(hwnd, IDC_ADDR);
                    if addr.is_empty() {
                        let title = wide("Stardew Sync Server");
                        let text = wide("Please enter a server address.");
                        MessageBoxW(hwnd, text.as_ptr(), title.as_ptr(), MB_OK | MB_ICONERROR);
                        return 0;
                    }
                    let pass = read_edit(hwnd, IDC_PASS);
                    (*state_ptr).result = Some((addr, pass));
                    DestroyWindow(hwnd);
                }
            } else if ctrl_id == IDC_CANCEL {
                unsafe { DestroyWindow(hwnd) };
            }
            0
        }

        WM_CLOSE => {
            unsafe { DestroyWindow(hwnd) };
            0
        }

        WM_DESTROY => {
            unsafe { PostQuitMessage(0) };
            0
        }

        WM_CTLCOLORDLG => unsafe { GetStockObject(WHITE_BRUSH as i32) as LRESULT },

        _ => unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) },
    }
}

unsafe fn read_edit(hwnd: HWND, ctrl_id: u16) -> String {
    let mut buf = vec![0u16; 1024];
    let len = unsafe { GetDlgItemTextW(hwnd, ctrl_id as i32, buf.as_mut_ptr(), buf.len() as i32) };
    String::from_utf16_lossy(&buf[..len as usize])
        .trim()
        .to_string()
}

unsafe fn ensure_dialog_class_registered() {
    let class_name = wide(DIALOG_CLASS);
    let mut wc: WNDCLASSEXW = unsafe { std::mem::zeroed() };
    wc.cbSize = std::mem::size_of::<WNDCLASSEXW>() as u32;
    wc.lpfnWndProc = Some(dialog_wnd_proc);
    wc.hCursor = unsafe { LoadCursorW(std::ptr::null_mut(), IDC_ARROW) };
    wc.hbrBackground = (COLOR_BTNFACE + 1) as _;
    wc.lpszClassName = class_name.as_ptr();
    unsafe { RegisterClassExW(&wc) };
}

/// Show the "Add Federated Server" dialog modally (runs its own message loop).
/// Returns `Some((url, password))` if the user clicked OK, `None` on cancel.
unsafe fn show_add_peer_dialog(default_password: &str) -> Option<(String, String)> {
    unsafe { ensure_dialog_class_registered() };

    let mut state = DialogState {
        default_password: default_password.to_string(),
        result: None,
    };

    let title = wide("Add Federated Server");
    let class = wide(DIALOG_CLASS);

    let hwnd = unsafe {
        CreateWindowExW(
            WS_EX_DLGMODALFRAME,
            class.as_ptr(),
            title.as_ptr(),
            (WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_VISIBLE | WS_GROUP) as u32,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            385,
            150,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            &mut state as *mut _ as _,
        )
    };

    if hwnd.is_null() {
        return None;
    }

    unsafe { ShowWindow(hwnd, SW_SHOW as i32) };

    let mut msg: MSG = unsafe { std::mem::zeroed() };
    loop {
        let ret = unsafe { GetMessageW(&mut msg, std::ptr::null_mut(), 0, 0) };
        if ret == 0 || ret == -1 {
            break;
        }
        if unsafe { IsDialogMessageW(hwnd, &msg) } == 0 {
            unsafe {
                TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }
        }
    }

    state.result
}

// ── Public entry point ─────────────────────────────────────────────────────

/// Blocks the calling thread in a Win32 message pump.
/// Returns when the user selects Exit from the tray menu or a shutdown signal is received.
pub fn run(
    port: u16,
    shutdown_tx: watch::Sender<bool>,
    mut shutdown_rx: Receiver<bool>,
    state: AppState,
) -> anyhow::Result<()> {
    let status_item = MenuItem::new(format!("StardewSync \u{2014} port {port}"), false, None);
    let autostart_item = CheckMenuItem::new("Start on Boot", true, is_autostart_enabled(), None);
    let add_peer_item = MenuItem::new("Add Federated Server\u{2026}", true, None);
    let exit_item = MenuItem::new("Exit", true, None);

    let menu = Menu::new();
    menu.append(&status_item)?;
    menu.append(&PredefinedMenuItem::separator())?;
    menu.append(&autostart_item)?;
    menu.append(&add_peer_item)?;
    menu.append(&PredefinedMenuItem::separator())?;
    menu.append(&exit_item)?;

    let _tray = TrayIconBuilder::new()
        .with_menu(Box::new(menu))
        .with_tooltip(format!("Stardew Sync Server (:{port})"))
        .with_icon(load_icon())
        .build()?;

    let autostart_id = autostart_item.id().clone();
    let add_peer_id = add_peer_item.id().clone();
    let exit_id = exit_item.id().clone();
    let menu_channel = MenuEvent::receiver();

    // Standard Win32 message pump. tray-icon dispatches tray events via
    // WM_USER messages delivered to its hidden window, so this loop must run
    // on the same thread that created the TrayIcon.
    unsafe {
        let mut msg = std::mem::zeroed::<MSG>();
        loop {
            // Check if an external shutdown was requested (e.g. Ctrl+C).
            if shutdown_rx.has_changed().unwrap_or(false) && *shutdown_rx.borrow_and_update() {
                info!("Tray: shutdown signal received, exiting message pump");
                return Ok(());
            }

            while let Ok(event) = menu_channel.try_recv() {
                if event.id == exit_id {
                    info!("Tray: Exit clicked, requesting shutdown");
                    let _ = shutdown_tx.send(true);
                    return Ok(());
                } else if event.id == autostart_id {
                    let enabled = autostart_item.is_checked();
                    set_autostart(enabled);
                } else if event.id == add_peer_id {
                    let default_pw = state.federation_token.as_deref().unwrap_or("").to_string();
                    if let Some((url, password)) = show_add_peer_dialog(&default_pw) {
                        add_peer_to_state(&state, url, password);
                    }
                }
            }

            // PeekMessageW with PM_REMOVE returns immediately; if no message is
            // pending we yield briefly so the shutdown check above stays responsive
            // without burning a full CPU core.
            let ret = windows_sys::Win32::UI::WindowsAndMessaging::PeekMessageW(
                &mut msg,
                std::ptr::null_mut(),
                0,
                0,
                windows_sys::Win32::UI::WindowsAndMessaging::PM_REMOVE,
            );
            if ret != 0 {
                if msg.message == windows_sys::Win32::UI::WindowsAndMessaging::WM_QUIT {
                    break;
                }
                TranslateMessage(&msg);
                DispatchMessageW(&msg);
            } else {
                std::thread::sleep(std::time::Duration::from_millis(50));
            }
        }
    }

    Ok(())
}

fn add_peer_to_state(state: &AppState, url: String, _password: String) {
    let peers = state.peers.clone();
    tokio::spawn(async move {
        let mut list = peers.write().await;
        if list.iter().any(|p| p.url == url) {
            info!("Tray: peer already in list, skipping: {url}");
            return;
        }
        list.push(LivePeer {
            url: url.clone(),
            source: PeerSource::Static,
        });
        info!("Tray: manually added peer: {url}");
    });
}
