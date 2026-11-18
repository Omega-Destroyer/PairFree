# Builds PairFree for Windows from this repo, in the E: mirror (Gradle 9 refuses to build on the NAS), and puts the program in
# desktop\dist\PairFree-X.Y.Z.exe in the mirror, with its SHA-256. With -Key (the private release key, outside the repo) it also writes
# PairFree-X.Y.Z.exe.sig; upload both to the release (docs/DESKTOP.md, section 7).
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File desktop\build-windows.ps1 [-Key E:\Builds\releases\pairfree-update.pem]
#
# For a release build from a fresh clone of the tag, run this from that clone's folder (the mirror step is skipped when -Mirror is off).
param(
    [string]$Key = "",
    [switch]$NoMirror
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
if (-not $NoMirror) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo ".vscode\mirror-build.ps1")
    if ($LASTEXITCODE -ne 0) { throw "the mirror step failed" }
    $build = "E:\Builds\PairFree"
} else {
    $build = $repo
}
$version = (Select-String -Path (Join-Path $repo "android\app\build.gradle.kts") -Pattern 'versionName = "([\d.]+)"').Matches[0].Groups[1].Value
Push-Location (Join-Path $build "android")
try {
    & .\gradlew.bat -p ..\desktop :app:linkReleaseExecutableMingwX64 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "the build failed" }
} finally {
    Pop-Location
}
$dist = Join-Path $build "desktop\dist"
New-Item -ItemType Directory -Force $dist | Out-Null
$exe = Join-Path $dist "PairFree-$version.exe"
Copy-Item (Join-Path $build "desktop\app\build\bin\mingwX64\releaseExecutable\PairFree.exe") $exe -Force
if ($Key -ne "") {
    & python (Join-Path $repo "tools\release_signing.py") sign $exe --private $Key
    if ($LASTEXITCODE -ne 0) { throw "signing failed" }
}
$hash = (Get-FileHash $exe -Algorithm SHA256).Hash.ToLower()
"{0}  {1}  ({2:N1} MB)" -f $hash, $exe, ((Get-Item $exe).Length / 1MB)
