import os
import sys
from pathlib import Path


def configure_bundled_tools():
    roots = [Path(sys.executable).resolve().parent / "tools"]
    bundle_root = getattr(sys, "_MEIPASS", None)
    if bundle_root:
        roots.append(Path(bundle_root) / "tools")
    for root in roots:
        if root.is_dir():
            os.environ["PATH"] = str(root) + os.pathsep + os.environ.get("PATH", "")
            break


def main():
    configure_bundled_tools()
    try:
        from PySide6.QtWidgets import QApplication
    except ImportError:
        print("PySide6 is missing. Run: uv sync", file=sys.stderr)
        return 1
    from app.ui.window import MainWindow

    incoming_urls = sys.argv[1:]
    application = QApplication(sys.argv[:1])
    application.setApplicationName("Downloader")
    application.setOrganizationName("mir4zul")
    application.setStyle("Fusion")
    from PySide6.QtGui import QIcon
    icon_path = Path(__file__).parent / "assets" / "download.png"
    application.setWindowIcon(QIcon(str(icon_path)))
    window = MainWindow()
    window.show()
    for incoming_url in incoming_urls:
        if window.open_external_url(incoming_url):
            break
    return application.exec()


if __name__ == "__main__":
    sys.exit(main())
