"""On-device yt-dlp bridge for Off.

Everything happens on the device: no Off-owned backend and no third-party
extraction API. Network requests go straight to the target website through
yt-dlp, which is the only extractor used here. Off still keeps NewPipe Extractor
for YouTube and the sites it handles natively, and falls back to this for
everything else.

All entry points return a JSON string so the Kotlin side never has to deal with
Python objects.
"""
import json
import time
import os
import re
import shutil
import tempfile
import threading
import traceback

import yt_dlp

_jobs = {}
_cancelled = set()
_lock = threading.Lock()

# Directory holding executables named "ffmpeg" and "ffprobe". Set by
# prepare_ffmpeg(); None means yt-dlp falls back to searching PATH.
_FFMPEG_DIR = None


def prepare_ffmpeg(native_lib_dir, work_dir):
    """Expose the bundled ffmpeg/ffprobe to yt-dlp.

    The binaries ship in the APK as libffmpeg.so / libffprobe.so, and Android
    extracts them into the app's native library directory — the only place an app
    may execute its own binaries on recent Android. yt-dlp looks for files named
    exactly "ffmpeg" and "ffprobe", so those names are created as symlinks
    (falling back to copies) inside a writable directory.
    """
    global _FFMPEG_DIR
    try:
        os.makedirs(work_dir, exist_ok=True)
        found = False
        for name in ("ffmpeg", "ffprobe"):
            src = os.path.join(native_lib_dir, "lib" + name + ".so")
            dst = os.path.join(work_dir, name)
            if not os.path.exists(src):
                continue
            found = True
            try:
                if os.path.islink(dst) or os.path.exists(dst):
                    os.remove(dst)
            except Exception:
                pass
            try:
                os.symlink(src, dst)
            except Exception:
                shutil.copy2(src, dst)
                os.chmod(dst, 0o755)
        _FFMPEG_DIR = work_dir if found else None
        return _FFMPEG_DIR
    except Exception:
        _FFMPEG_DIR = None
        return None


def ffmpeg_available():
    return _FFMPEG_DIR is not None


def _ffmpeg_opts():
    return {"ffmpeg_location": _FFMPEG_DIR} if _FFMPEG_DIR else {}


def _progress_file(job_id):
    return os.path.join(tempfile.gettempdir(), "off_dl_progress_" + str(job_id) + ".json")


# yt-dlp calls the progress hook for every chunk it reads. Writing a JSON file
# that often was throttling the download to a crawl, so writes are rate-limited;
# anything important (finished, error, cancelled) still goes through immediately.
_PROGRESS_MIN_INTERVAL = 0.4
_last_progress_write = {}


def _write_progress(job_id, payload, force=False):
    try:
        now = time.time()
        final = payload.get("status") in ("finished", "error", "cancelled", "starting")
        if not force and not final:
            if now - _last_progress_write.get(job_id, 0.0) < _PROGRESS_MIN_INTERVAL:
                return
        _last_progress_write[job_id] = now
        path = _progress_file(job_id)
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(payload, f)
        os.replace(tmp, path)
    except Exception:
        pass


def _progress_hook(job_id):
    def hook(data):
        if job_id in _cancelled:
            raise yt_dlp.utils.DownloadError("Cancelled")
        status = data.get("status", "downloading")
        total = data.get("total_bytes") or data.get("total_bytes_estimate") or 0
        downloaded = data.get("downloaded_bytes") or 0
        percent = (downloaded / total * 100.0) if total else 0.0
        _write_progress(job_id, {
            "status": status,
            "percent": round(percent, 1),
            "downloaded_bytes": downloaded,
            "total_bytes": total,
            "speed": data.get("speed") or 0,
            "eta": data.get("eta"),
            "filename": data.get("filename") or "",
            "message": "Downloading" if status == "downloading" else "Processing",
        })
    return hook


def _formats(info):
    out = []
    for f in (info.get("formats") or []):
        vcodec = f.get("vcodec") or "none"
        acodec = f.get("acodec") or "none"
        has_video = vcodec != "none"
        has_audio = acodec != "none"
        if not has_video and not has_audio:
            continue
        height = f.get("height") or 0
        out.append({
            "format_id": str(f.get("format_id") or ""),
            "ext": f.get("ext") or "unknown",
            "resolution": f.get("resolution") or ((str(height) + "p") if height else ("audio only" if not has_video else "unknown")),
            "height": height,
            "filesize": f.get("filesize") or f.get("filesize_approx") or 0,
            "vcodec": vcodec,
            "acodec": acodec,
            "has_video": has_video,
            "has_audio": has_audio,
            "note": f.get("format_note") or "",
            "fps": f.get("fps") or 0,
            "abr": f.get("abr") or 0,
        })
    out.sort(key=lambda x: (x["has_video"], x["height"], x["has_audio"], x["filesize"]), reverse=True)
    return out


def extract_info(url):
    url = (url or "").strip()
    if not url.startswith(("https://", "http://")):
        return json.dumps({"ok": False, "error": "Enter a valid http or https URL."})
    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "ignoreerrors": False,
        "socket_timeout": 20,
        "retries": 2,
    }
    opts.update(_ffmpeg_opts())
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if not info:
            return json.dumps({"ok": False, "error": "No video metadata was returned."})
        return json.dumps({
            "ok": True,
            "id": info.get("id") or "",
            "title": info.get("title") or "Untitled video",
            "description": info.get("description") or "",
            "duration": info.get("duration") or 0,
            "thumbnail": info.get("thumbnail") or "",
            "uploader": info.get("uploader") or info.get("channel") or info.get("uploader_id") or "",
            "extractor": info.get("extractor_key") or info.get("extractor") or "yt-dlp",
            "webpage_url": info.get("webpage_url") or url,
            "formats": _formats(info),
        })
    except Exception as e:
        return json.dumps({
            "ok": False,
            "error": (str(e).strip() or "Could not read video metadata.")[:400],
        })


def start_download(url, format_selector, output_dir, job_id):
    """Download into app-private storage. Kotlin polls get_progress(job_id).

    Blocks until finished, so call it on a background thread.
    """
    job_id = str(job_id)
    os.makedirs(output_dir, exist_ok=True)
    output_template = os.path.join(output_dir, "%(extractor)s_%(title).120B_%(id)s.%(ext)s")
    selector = (format_selector or "").strip() or "bv*+ba/b"
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": selector,
        "outtmpl": output_template,
        "progress_hooks": [_progress_hook(job_id)],
        # Speed: pull fragmented streams in parallel and read in large chunks.
        "concurrent_fragment_downloads": 8,
        "http_chunk_size": 10485760,
        "buffersize": 1048576,
        "retries": 10,
        "fragment_retries": 10,
        "continuedl": True,
        "overwrites": False,
        "socket_timeout": 20,
        "windowsfilenames": True,
        # Many sites only offer separate video and audio streams, so merging is
        # required. It uses the ffmpeg bundled with the app.
        "merge_output_format": "mp4",
    }
    opts.update(_ffmpeg_opts())
    _write_progress(job_id, {"status": "starting", "percent": 0, "message": "Starting download"})
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            code = ydl.download([url])
        if job_id in _cancelled:
            _write_progress(job_id, {"status": "cancelled", "message": "Cancelled"})
            return json.dumps({"ok": False, "error": "Cancelled"})
        if code == 0:
            _write_progress(job_id, {
                "status": "finished",
                "percent": 100,
                "message": "Download complete",
                "files": sorted(os.listdir(output_dir)),
            })
            return json.dumps({"ok": True})
        _write_progress(job_id, {"status": "error", "message": "The download did not complete"})
        return json.dumps({"ok": False, "error": "The download did not complete."})
    except Exception as e:
        cancelled = job_id in _cancelled
        _write_progress(job_id, {
            "status": "cancelled" if cancelled else "error",
            "message": "Cancelled" if cancelled else str(e)[:400],
        })
        return json.dumps({"ok": False, "error": "Cancelled" if cancelled else str(e)[:400]})


def cancel_job(job_id):
    _cancelled.add(str(job_id))
    return True


def get_progress(job_id):
    try:
        with open(_progress_file(job_id), "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return json.dumps({"status": "starting", "percent": 0, "message": "Preparing download"})


def cleanup_progress(job_id):
    job_id = str(job_id)
    _cancelled.discard(job_id)
    try:
        os.remove(_progress_file(job_id))
    except Exception:
        pass
    return True


def version():
    return yt_dlp.version.__version__
