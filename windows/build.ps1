$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$tempDir = $null
Push-Location $root
try {
    uv sync --locked
    if ($LASTEXITCODE -ne 0) { throw "Could not install the locked Python dependencies." }
    uv pip install --python .venv/Scripts/python.exe pyinstaller
    if ($LASTEXITCODE -ne 0) { throw "Could not install PyInstaller." }
    uv run --locked python scripts/create_windows_icon.py
    if ($LASTEXITCODE -ne 0) { throw "Could not create the Windows icon." }

    & .venv/Scripts/pyinstaller.exe --noconfirm --clean --windowed `
        --name Downloader --icon windows/Downloader.ico `
        --add-data "app/assets/download.png;app/assets" `
        --copy-metadata PySide6 --copy-metadata yt-dlp --copy-metadata yt-dlp-ejs `
        app/__main__.py
    if ($LASTEXITCODE -ne 0) { throw "Could not package the Downloader app." }

    & .venv/Scripts/pyinstaller.exe --noconfirm --clean --console `
        --distpath dist/worker --workpath build/worker `
        --name DownloaderWorker --copy-metadata yt-dlp --copy-metadata yt-dlp-ejs `
        --collect-all yt_dlp --collect-all yt_dlp_ejs `
        --hidden-import app.services.download_worker app/windows_worker.py
    if ($LASTEXITCODE -ne 0) { throw "Could not package the download worker." }

    $toolDir = Join-Path $root "dist/Downloader/tools"
    New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
    $tempDir = Join-Path $env:TEMP ("downloader-windows-build-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

    $ffmpegZip = Join-Path $tempDir "ffmpeg.zip"
    Invoke-WebRequest "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip" -OutFile $ffmpegZip
    $ffmpegExtract = Join-Path $tempDir "ffmpeg"
    Expand-Archive -Path $ffmpegZip -DestinationPath $ffmpegExtract -Force
    $ffmpegBin = Get-ChildItem $ffmpegExtract -Recurse -Directory | Where-Object {
        (Test-Path (Join-Path $_.FullName "ffmpeg.exe")) -and (Test-Path (Join-Path $_.FullName "ffprobe.exe"))
    } | Select-Object -First 1
    if (-not $ffmpegBin) { throw "FFmpeg tools were not found in the downloaded archive." }
    Get-ChildItem $ffmpegBin.FullName -File | Where-Object {
        $_.Name -in @("ffmpeg.exe", "ffprobe.exe") -or $_.Extension -eq ".dll"
    } | ForEach-Object { Copy-Item $_.FullName -Destination $toolDir }
    $ffmpegLicense = Get-ChildItem $ffmpegExtract -Recurse -File | Where-Object {
        $_.Name -match "^(LICENSE|COPYING)(\.txt)?$"
    } | Select-Object -First 1
    if (-not $ffmpegLicense) { throw "The FFmpeg license file was not found in its archive." }
    Copy-Item $ffmpegLicense.FullName (Join-Path $toolDir "FFMPEG-LICENSE.txt")

    $denoZip = Join-Path $tempDir "deno.zip"
    Invoke-WebRequest "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip" -OutFile $denoZip
    $denoExtract = Join-Path $tempDir "deno"
    Expand-Archive -Path $denoZip -DestinationPath $denoExtract -Force
    Copy-Item (Join-Path $denoExtract "deno.exe") (Join-Path $toolDir "deno.exe")
    Invoke-WebRequest "https://raw.githubusercontent.com/denoland/deno/main/LICENSE.md" -OutFile (Join-Path $toolDir "DENO-LICENSE.md")

    $iscc = (Get-Command ISCC.exe -ErrorAction SilentlyContinue).Source
    if (-not $iscc) {
        $iscc = Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6/ISCC.exe"
    }
    if (-not (Test-Path $iscc)) { throw "Install Inno Setup 6 before building the installer." }
    & $iscc windows/Downloader.iss
    if ($LASTEXITCODE -ne 0) { throw "Could not build the installer." }
    Write-Host "Installer created at dist/Downloader-Setup.exe"
}
finally {
    if ($tempDir -and (Test-Path $tempDir)) {
        Remove-Item $tempDir -Recurse -Force
    }
    Pop-Location
}
