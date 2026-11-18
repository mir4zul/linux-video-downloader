import os
import shutil

from PySide6.QtCore import QProcess
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QSystemTrayIcon


class DesktopNotifier:
    """Send completion and failure notices through the desktop notification service."""

    def __init__(self, parent, icon_path):
        self.parent = parent
        self.icon_path = str(icon_path)
        self.tray = None
        if os.name == "nt" or not shutil.which("notify-send"):
            self.tray = QSystemTrayIcon(QIcon(self.icon_path), parent)
            self.tray.setToolTip("Downloader")
            if QSystemTrayIcon.isSystemTrayAvailable():
                self.tray.show()

    def notify(self, title, message, failed=False):
        if os.name != "nt" and shutil.which("notify-send"):
            urgency = "critical" if failed else "normal"
            QProcess.startDetached("notify-send", [
                "--app-name=Downloader",
                f"--icon={self.icon_path}",
                f"--urgency={urgency}",
                title,
                message,
            ])
            return
        if self.tray and QSystemTrayIcon.isSystemTrayAvailable():
            icon = QSystemTrayIcon.MessageIcon.Warning if failed else QSystemTrayIcon.MessageIcon.Information
            self.tray.showMessage(title, message, icon, 8000)
