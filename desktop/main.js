const { app, BrowserWindow, Tray, Menu, globalShortcut, shell, nativeImage, session } = require("electron");

let win;
let tray;
let quitting = false;

const APP_URL = "https://pak-point.github.io/NAVEED-AI/";

function showApp() {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
}

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 780,
    minWidth: 380,
    minHeight: 560,
    show: true,
    autoHideMenuBar: true,
    backgroundColor: "#07111f",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  // Explicitly allow microphone permission requests from the trusted NAVEED AI site.
  // Electron's SpeechRecognition implementation may still depend on Chromium support.
  const appSession = win.webContents.session;

  appSession.setPermissionRequestHandler((webContents, permission, callback, details) => {
    const requestingUrl = webContents.getURL() || "";
    const isTrustedSite =
      requestingUrl.startsWith("https://pak-point.github.io/") ||
      (details && details.requestingUrl && details.requestingUrl.startsWith("https://pak-point.github.io/"));

    if (permission === "media" && isTrustedSite) {
      const audioRequested = !details || !details.mediaTypes ||
        details.mediaTypes.length === 0 || details.mediaTypes.includes("audio");
      callback(Boolean(audioRequested));
      return;
    }

    callback(false);
  });

  appSession.setPermissionCheckHandler((webContents, permission, requestingOrigin, details) => {
    const isTrustedSite = requestingOrigin === "https://pak-point.github.io";
    if (permission === "media" && isTrustedSite) {
      const audioRequested = !details || !details.mediaTypes ||
        details.mediaTypes.length === 0 || details.mediaTypes.includes("audio");
      return Boolean(audioRequested);
    }
    return false;
  });

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("https://")) shell.openExternal(url);
    return { action: "deny" };
  });

  win.loadURL(APP_URL);

  win.on("close", (event) => {
    if (!quitting) {
      event.preventDefault();
      win.hide();
    }
  });
}

function createTray() {
  const image = nativeImage.createFromDataURL(
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII="
  );

  tray = new Tray(image);
  tray.setToolTip("NAVEED AI");
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: "Open NAVEED AI", click: showApp },
    { type: "separator" },
    {
      label: "Quit",
      click: () => {
        quitting = true;
        app.quit();
      }
    }
  ]));
  tray.on("double-click", showApp);
}

app.whenReady().then(() => {
  app.setAppUserModelId("com.pakpoint.naveedai");
  app.setLoginItemSettings({ openAtLogin: true });
  createWindow();
  createTray();
  globalShortcut.register("CommandOrControl+Shift+N", showApp);
});

app.on("before-quit", () => {
  quitting = true;
  globalShortcut.unregisterAll();
});

app.on("window-all-closed", () => {
  // Keep the tray app alive until the user chooses Quit.
});
