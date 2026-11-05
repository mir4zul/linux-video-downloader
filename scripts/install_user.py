from pathlib import Path
import shlex
import shutil
import sys


def main():
    app_root = Path(sys.argv[1]).resolve()
    home = Path.home()
    uv_executable = shutil.which("uv")
    if not uv_executable:
        raise SystemExit("uv is missing; install it before installing the desktop launcher.")
    binary = home / ".local/bin/free-video-downloader"
    applications = home / ".local/share/applications"
    icons = home / ".local/share/icons/hicolor/512x512/apps"
    desktop = applications / "free-video-downloader.desktop"
    icon = icons / "downloader-app.png"
    binary.parent.mkdir(parents=True, exist_ok=True)
    applications.mkdir(parents=True, exist_ok=True)
    icons.mkdir(parents=True, exist_ok=True)
    icon.write_bytes((app_root / "app/assets/download.png").read_bytes())
    project = shlex.quote(str(app_root))
    binary.write_text("#!/bin/sh\nset -eu\nexec " + shlex.quote(uv_executable) + " run --locked --project " + project + " free-video-downloader \"$@\"\n")
    binary.chmod(0o755)
    # Desktop Exec quoting follows the Desktop Entry spec and supports spaces in home paths.
    executable = str(binary).replace("\\", "\\\\").replace('"', '\\"')
    desktop.write_text(
        "[Desktop Entry]\n"
        "Type=Application\n"
        "Name=Downloader\n"
        "GenericName=Video Downloader\n"
        "Keywords=downloader;video;audio;YouTube;MP3;MP4;\n"
        "Comment=Download a video at the resolution you choose\n"
        f'Exec="{executable}" %u\n'
        "Terminal=false\n"
        f"Icon={icon}\n"
        "MimeType=x-scheme-handler/free-video-downloader;\n"
        "Categories=AudioVideo;Video;\n"
        "StartupNotify=true\n"
    )
    desktop.chmod(0o644)
    import subprocess
    xdg_mime = shutil.which("xdg-mime")
    if xdg_mime:
        subprocess.run([xdg_mime, "default", desktop.name,
                        "x-scheme-handler/free-video-downloader"], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    desktop_database = shutil.which("update-desktop-database")
    if desktop_database:
        subprocess.run([desktop_database, str(applications)], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
