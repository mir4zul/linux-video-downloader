"""Console entry point used by the packaged Windows app for child jobs."""
import os
from pathlib import Path
import sys


def configure_tools():
    tool_dir = Path(sys.executable).resolve().parent.parent / "tools"
    if tool_dir.is_dir():
        os.environ["PATH"] = str(tool_dir) + os.pathsep + os.environ.get("PATH", "")
    for stream in (sys.stdout, sys.stderr):
        if stream is not None and hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")


def main():
    configure_tools()
    if len(sys.argv) > 1 and sys.argv[1] == "--fvd-download-worker":
        from app.services.download_worker import main as download_main
        return download_main()
    if len(sys.argv) > 1 and sys.argv[1] == "--fvd-analysis-worker":
        import yt_dlp
        sys.argv = [sys.argv[0], *sys.argv[2:]]
        return yt_dlp.main()
    print("Unknown Downloader worker operation.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
