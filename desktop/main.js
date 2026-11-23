const { app, BrowserWindow, Tray, Menu, globalShortcut, shell, nativeImage } = require("electron");
let win, tray, quitting = false;
const URL = "https://pak-point.github.io/NAVEED-AI/";
function showApp(){ if(!win)return; if(win.isMinimized())win.restore(); win.show(); win.focus(); }
function createWindow(){
  win = new BrowserWindow({width:1100,height:780,minWidth:380,minHeight:560,show:true,autoHideMenuBar:true,backgroundColor:"#07111f",webPreferences:{contextIsolation:true,nodeIntegration:false,sandbox:true}});
  win.loadURL(URL);
  win.webContents.setWindowOpenHandler(({url})=>{if(url.startsWith("https://"))shell.openExternal(url);return {action:"deny"};});
  win.on("close",e=>{if(!quitting){e.preventDefault();win.hide();}});
}
function createTray(){
  const img=nativeImage.createFromDataURL("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=");
  tray=new Tray(img); tray.setToolTip("NAVEED AI");
  tray.setContextMenu(Menu.buildFromTemplate([{label:"Open NAVEED AI",click:showApp},{type:"separator"},{label:"Quit",click:()=>{quitting=true;app.quit();}}]));
  tray.on("double-click",showApp);
}
app.whenReady().then(()=>{app.setAppUserModelId("com.pakpoint.naveedai");app.setLoginItemSettings({openAtLogin:true});createWindow();createTray();globalShortcut.register("CommandOrControl+Shift+N",showApp);});
app.on("before-quit",()=>{quitting=true;globalShortcut.unregisterAll();});
app.on("window-all-closed",()=>{});
