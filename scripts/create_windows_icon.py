"""Build a multi-size Windows ICO using the app's supplied PNG icon."""
from pathlib import Path
import struct

from PySide6.QtCore import QByteArray, QBuffer, QIODevice, Qt
from PySide6.QtGui import QImage


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "assets" / "download.png"
OUTPUT = ROOT / "windows" / "Downloader.ico"
SIZES = (16, 24, 32, 48, 64, 128, 256)


def png_bytes(image):
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    if not image.save(buffer, "PNG"):
        raise RuntimeError("Could not encode the Windows app icon.")
    buffer.close()
    return bytes(data)


def main():
    source = QImage(str(SOURCE))
    if source.isNull():
        raise RuntimeError(f"Could not read icon: {SOURCE}")
    images = [png_bytes(source.scaled(size, size, Qt.AspectRatioMode.IgnoreAspectRatio,
                                      Qt.TransformationMode.SmoothTransformation))
              for size in SIZES]
    offset = 6 + 16 * len(images)
    entries = []
    for size, image in zip(SIZES, images):
        dimension = 0 if size == 256 else size
        entries.append(struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32,
                                   len(image), offset))
        offset += len(image)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(struct.pack("<HHH", 0, 1, len(images)) + b"".join(entries) + b"".join(images))
    print(f"Created {OUTPUT}")


if __name__ == "__main__":
    main()
