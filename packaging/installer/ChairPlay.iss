; ChairPlay's installer, built by `./gradlew installer -Pchairplay.version=X.Y.Z` (gradle/installer.gradle.kts), which
; passes AppVersion, SourceDir (the jpackage app image), OutputDir and AppData (the app's data, ';' between names).
;
; ChairPlay keeps its data next to its exe (settings, controls and window files, identity.properties, pairings\,
; offline-mfi\, carplay-icon.png, logs\, screenshots\, ChairPlay.lock). Optional MfiSourceDir supplies an initial MFi pair.
; Otherwise the installer ships no user data and deletes only
; the program folders app\ and runtime\ before an update: a lost identity or pairing makes the iPhone see a new car.
; Uninstalling asks whether to delete the data too, No by default and in a silent run.

#ifndef AppVersion
  #error Pass the version: ISCC /DAppVersion=X.Y.Z
#endif
#ifndef SourceDir
  #error Pass the app image: ISCC /DSourceDir=<build\jpackage\ChairPlay>
#endif
#ifndef OutputDir
  #define OutputDir "."
#endif
#ifndef AppData
  #error Pass the app's data: ISCC /DAppData=settings.properties;pairings;...
#endif

[Setup]
; Fixed for good: it is how a newer installer finds and updates an existing install.
AppId={{25CBBAE6-C3C2-48CB-AB46-25D3386168D8}
AppName=ChairPlay
AppVersion={#AppVersion}
AppPublisher=Oleksiy Strelets
; Per user and without admin rights: the app writes its data into its own folder.
DefaultDirName={localappdata}\Programs\ChairPlay
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
; Never let Windows close a running ChairPlay: a hard stop leaves the iPhone ignoring new sessions. [Code] closes it
; gracefully instead, and asks the user when that does not work.
CloseApplications=no
RestartApplications=no
SetupIconFile=..\chairplay.ico
UninstallDisplayIcon={app}\ChairPlay.exe
; Installed apps shows the version in its own column: the name goes without it.
UninstallDisplayName=ChairPlay
WizardStyle=modern
OutputDir={#OutputDir}
OutputBaseFilename=ChairPlay-{#AppVersion}-setup

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

; Jars and runtime modules a newer version dropped must not linger. Never a wildcard on {app} itself: the data is there.
[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
; Up to 1.0.5 the WebView2 cache was outside the app folder; it now lives in {app}\webview2 with the other data.
Type: filesandordirs; Name: "{localappdata}\ChairPlay\WebView2"
Type: dirifempty; Name: "{localappdata}\ChairPlay"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
#ifdef MfiSourceDir
Source: "{#MfiSourceDir}\identity.pk8"; DestDir: "{app}\offline-mfi"; Flags: onlyifdoesntexist uninsneveruninstall; Check: InstallBundledMfi
Source: "{#MfiSourceDir}\certificate.p7b"; DestDir: "{app}\offline-mfi"; Flags: onlyifdoesntexist uninsneveruninstall; Check: InstallBundledMfi
#endif

[Icons]
Name: "{autoprograms}\ChairPlay"; Filename: "{app}\ChairPlay.exe"
Name: "{autodesktop}\ChairPlay"; Filename: "{app}\ChairPlay.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\ChairPlay.exe"; Description: "{cm:LaunchProgram,ChairPlay}"; Flags: postinstall nowait skipifsilent

[Code]
const
  GENERIC_READ = $80000000;
  OPEN_EXISTING = 3;
  INVALID_HANDLE_VALUE = -1;
  ERROR_SHARING_VIOLATION = 32;
  // Ending the CarPlay session takes seconds (about 8 here); past this the user is asked to close ChairPlay.
  CloseWaitMillis = 15000;
  RunningMessage = 'ChairPlay is running. Close its window, then click Retry.';
  DeleteDataQuestion = 'Also delete your ChairPlay settings, iPhone pairings, and MFi files?' + #13#10#13#10 +
    'Keep them to install ChairPlay again later without setting it up again.';

function CreateFile(lpFileName: String; dwDesiredAccess, dwShareMode, lpSecurityAttributes,
  dwCreationDisposition, dwFlagsAndAttributes: Cardinal; hTemplateFile: THandle): THandle;
external 'CreateFileW@kernel32.dll stdcall';

function CloseHandle(hObject: THandle): Boolean;
external 'CloseHandle@kernel32.dll stdcall';

#ifdef MfiSourceDir
var
  BundledMfiAllowed: Boolean;

function InstallBundledMfi(): Boolean;
begin
  Result := BundledMfiAllowed;
end;
#endif

// A running ChairPlay holds ChairPlay.lock open from start to the end of its graceful shutdown, so opening it with no
// sharing fails with a sharing violation. A missing file or folder means no run.
function IsRunning(): Boolean;
var
  Handle: THandle;
begin
  Handle := CreateFile(ExpandConstant('{app}\ChairPlay.lock'), GENERIC_READ, 0, 0, OPEN_EXISTING,
    FILE_ATTRIBUTE_NORMAL, 0);
  if Handle = INVALID_HANDLE_VALUE then
    Result := DLLGetLastError() = ERROR_SHARING_VIOLATION
  else begin
    CloseHandle(Handle);
    Result := False;
  end;
end;

// Asks until ChairPlay is closed; False when the user cancels (and in a silent run with suppressed messages).
function WaitUntilClosed(): Boolean;
begin
  Result := True;
  while Result and IsRunning() do
    Result := SuppressibleMsgBox(RunningMessage, mbError, MB_RETRYCANCEL, IDCANCEL) = IDRETRY;
end;

// Closes the ChairPlay running from {app} as its own exit does: taskkill without /F sends its window WM_CLOSE, and the
// app ends the CarPlay session and lets go of ChairPlay.lock before it exits. Only one copy runs at a time, so the only
// ChairPlay.exe is this one. Waits up to CloseWaitMillis for the lock on a progress page, which keeps the wizard
// answering; a copy still running is left to WaitUntilClosed.
procedure CloseRunning();
var
  Progress: TOutputProgressWizardPage;
  ResultCode, Waited: Integer;
begin
  if not IsRunning() then
    Exit;
  Progress := CreateOutputProgressPage('Closing ChairPlay', 'ChairPlay is ending the CarPlay session.');
  Progress.Show();
  try
    Exec(ExpandConstant('{sys}\taskkill.exe'), '/IM ChairPlay.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Waited := 0;
    while IsRunning() and (Waited < CloseWaitMillis) do begin
      Progress.SetProgress(Waited, CloseWaitMillis);
      Sleep(250);
      Waited := Waited + 250;
    end;
  finally
    Progress.Hide();
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
#ifdef MfiSourceDir
  // Decide once for the whole pair, before either file is copied. Never complete a different user's partial pair.
  BundledMfiAllowed :=
    not FileExists(ExpandConstant('{app}\offline-mfi\identity.pk8')) and
    not FileExists(ExpandConstant('{app}\offline-mfi\certificate.p7b')) and
    not DirExists(ExpandConstant('{app}\offline-mfi\identity.pk8')) and
    not DirExists(ExpandConstant('{app}\offline-mfi\certificate.p7b'));
#endif
  CloseRunning();
  if WaitUntilClosed() then
    Result := ''
  else
    Result := 'ChairPlay is running. Close its window, then run Setup again.';
end;

function InitializeUninstall(): Boolean;
begin
  Result := WaitUntilClosed();
end;

// The data the build names ({#AppData}), each a file or a folder in {app}; then {app} itself if nothing else is left.
procedure DeleteData();
var
  Names, Name, Path: String;
  Split: Integer;
begin
  Names := '{#AppData}' + ';';
  while Names <> '' do begin
    Split := Pos(';', Names);
    Name := Copy(Names, 1, Split - 1);
    Names := Copy(Names, Split + 1, Length(Names));
    if Name <> '' then begin
      Path := ExpandConstant('{app}\' + Name);
      if DirExists(Path) then
        DelTree(Path, True, True, True)
      else
        DeleteFile(Path);
    end;
  end;
  RemoveDir(ExpandConstant('{app}'));
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    if SuppressibleMsgBox(DeleteDataQuestion, mbConfirmation, MB_YESNO or MB_DEFBUTTON2, IDNO) = IDYES then
      DeleteData();
end;
