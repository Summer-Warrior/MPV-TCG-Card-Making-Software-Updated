"""TCG Studio On-Device Termux ARM64 MSE Bridge (FastAPI).

Wraps unmodified upstream Magic Set Editor 2 ARM64 binaries (`magicseteditor --cli --raw`
under `xvfb-run`) to render 100% authentic desktop Magic and Non-Magic cards over localhost
(`http://127.0.0.1:8765`), manage delta-synced upstream packs, validate custom templates,
and install legally licensed user fonts via fontconfig.
"""

from __future__ import annotations

import asyncio
import base64
import io
import json
import logging
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response
from PIL import Image
from pydantic import BaseModel, Field

from bridge.sync_upstream import (
    BRIDGE_DIR,
    CUSTOM_TEMPLATES_DIR,
    FONT_EXTENSIONS,
    FONT_INSTALL_DIR,
    LICENSED_FONT_DIR,
    MSE_PACKAGE_SUFFIXES,
    UPSTREAM_DIR,
    get_installed_splits,
    load_default_manifest,
    run_fc_cache,
    sync_manifest,
)

logger = logging.getLogger("tcg_studio.bridge")
logging.basicConfig(
    level=logging.INFO,
    format="[%(levelname)s] %(name)s: %(message)s",
)

BIN_DIR = BRIDGE_DIR / "bin"
MSE_RENDER_TIMEOUT_SEC = int(os.environ.get("MSE_RENDER_TIMEOUT_SEC", "45"))
_RENDER_LOCK = asyncio.Lock()

app = FastAPI(
    title="TCG Studio ARM64 MSE Bridge",
    version="3.0.0",
    description="Local HTTP bridge for unmodified MagicSetEditor2 CLI rendering on Android Termux.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class TemplateValidateRequest(BaseModel):
    template_id: str = Field(..., min_length=1)
    engine_split: str = Field(default="mse_modern")
    game_source: str | None = None
    style_source: str = Field(..., min_length=1)


@dataclass
class ParsedStyleMetadata:
    card_width_px: int = 375
    card_height_px: int = 523
    card_dpi: int = 150
    game: str = ""
    short_name: str = ""
    hitboxes: list[dict[str, Any]] = field(default_factory=list)
    referenced_fonts: list[str] = field(default_factory=list)


def _resolve_engine_binary(engine_split: str) -> Path | None:
    """Locate the real compiled ARM64 MagicSetEditor2 binary for modern or legacy split."""
    if engine_split == "mse_legacy":
        candidates = [
            BIN_DIR / "magicseteditor-legacy",
            BIN_DIR / "magicseteditor-legacy-arm64",
        ]
    else:
        candidates = [
            BIN_DIR / "magicseteditor-modern",
            BIN_DIR / "magicseteditor-modern-arm64",
            BIN_DIR / "magicseteditor",
        ]

    for candidate in candidates:
        if candidate.exists() and os.access(candidate, os.X_OK):
            return candidate.resolve()

    if engine_split != "mse_legacy":
        system_bin = shutil.which("magicseteditor")
        if system_bin:
            return Path(system_bin).resolve()
    return None


def _list_fontconfig_fonts() -> list[dict[str, Any]]:
    """Query local fonts registered in fontconfig (`fc-list`)."""
    fonts: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    if shutil.which("fc-list") is not None:
        try:
            proc = subprocess.run(
                ["fc-list", "--format=%{family}\t%{file}\n"],
                capture_output=True,
                text=True,
                timeout=15,
                check=False,
            )
            if proc.returncode == 0:
                for raw_line in proc.stdout.splitlines():
                    if not raw_line.strip():
                        continue
                    parts = raw_line.split("\t", 1)
                    families_blob = parts[0].strip()
                    file_path = parts[1].strip() if len(parts) > 1 else ""
                    source = "fc-list"
                    if file_path.startswith(str(LICENSED_FONT_DIR)):
                        source = "mse-licensed"
                    elif file_path.startswith(str(FONT_INSTALL_DIR)):
                        source = "mse-studio"
                    for family in (f.strip() for f in families_blob.split(",") if f.strip()):
                        key = (family.lower(), file_path)
                        if key in seen:
                            continue
                        seen.add(key)
                        fonts.append(
                            {
                                "family": family,
                                "source": source,
                                "file": file_path or None,
                            }
                        )
        except Exception as exc:
            logger.warning("fc-list query failed: %s", exc)

    # Also include any installed font files in FONT_INSTALL_DIR even if fc-list is unavailable
    if FONT_INSTALL_DIR.exists():
        for font_file in sorted(FONT_INSTALL_DIR.rglob("*")):
            if font_file.is_file() and font_file.suffix.lower() in FONT_EXTENSIONS:
                stem_family = font_file.stem.replace("_", " ").replace("-", " ").strip()
                key = (stem_family.lower(), str(font_file))
                if stem_family and key not in seen:
                    seen.add(key)
                    source = (
                        "mse-licensed"
                        if str(font_file).startswith(str(LICENSED_FONT_DIR))
                        else "mse-studio"
                    )
                    fonts.append(
                        {
                            "family": stem_family,
                            "source": source,
                            "file": str(font_file),
                        }
                    )

    fonts.sort(key=lambda item: (item["family"].lower(), item["source"]))
    return fonts


def _installed_font_family_set() -> set[str]:
    return {item["family"].strip().lower() for item in _list_fontconfig_fonts() if item.get("family")}


def _leading_tabs(line: str) -> int:
    count = 0
    for ch in line:
        if ch == "\t":
            count += 1
        elif ch == " ":
            count += 1
        else:
            break
    return count


def _parse_numeric_value(raw: str) -> float | None:
    cleaned = raw.strip().strip('"').strip("'")
    if not cleaned:
        return None
    try:
        return float(cleaned)
    except ValueError:
        match = re.match(r"^(-?\d+(?:\.\d+)?)$", cleaned)
        if match:
            return float(match.group(1))
        return None


def parse_mse_style_text(style_text: str) -> ParsedStyleMetadata:
    """Parse real MSE `.mse-style` dimensions, hitboxes, and referenced font families."""
    meta = ParsedStyleMetadata()
    lines = style_text.splitlines()
    in_card_style = False
    current_field: str | None = None
    in_font_block = False
    field_props: dict[str, dict[str, float | int | bool]] = {}
    referenced_fonts: list[str] = []
    seen_fonts: set[str] = set()

    for raw_line in lines:
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#"):
            continue

        indent = _leading_tabs(raw_line)

        if indent == 0:
            in_font_block = False
            current_field = None
            lower = stripped.lower()
            if lower == "card style:":
                in_card_style = True
                continue
            in_card_style = False
            if ":" in stripped:
                key, value = stripped.split(":", 1)
                k = key.strip().lower()
                v = value.strip()
                if k == "game":
                    meta.game = v
                elif k == "short name":
                    meta.short_name = v
                elif k == "card width":
                    num = _parse_numeric_value(v)
                    if num and num > 0:
                        meta.card_width_px = int(round(num))
                elif k == "card height":
                    num = _parse_numeric_value(v)
                    if num and num > 0:
                        meta.card_height_px = int(round(num))
                elif k == "card dpi":
                    num = _parse_numeric_value(v)
                    if num and num > 0:
                        meta.card_dpi = int(round(num))
            continue

        if not in_card_style:
            continue

        if indent == 1 and stripped.endswith(":"):
            current_field = stripped[:-1].strip()
            in_font_block = False
            field_props.setdefault(current_field, {})
            continue

        if current_field is None:
            continue

        if indent == 2:
            if stripped.lower() == "font:":
                in_font_block = True
                continue
            in_font_block = False
            if ":" in stripped:
                key, value = stripped.split(":", 1)
                k = key.strip().lower()
                v = value.strip()
                props = field_props[current_field]
                if k in ("left", "top", "width", "height", "right", "bottom"):
                    num = _parse_numeric_value(v)
                    if num is not None:
                        props[k] = num
                elif k == "z index":
                    num = _parse_numeric_value(v)
                    if num is not None:
                        props["z_index"] = int(round(num))
                elif k == "visible":
                    if v.lower() in ("false", "no") or "{" in v or "script:" in v.lower():
                        props["hidden"] = True
            continue

        if in_font_block and indent >= 3 and ":" in stripped:
            key, value = stripped.split(":", 1)
            if key.strip().lower() == "name":
                font_name = value.strip().strip('"').strip("'")
                if font_name and "{" not in font_name and font_name.lower() not in seen_fonts:
                    seen_fonts.add(font_name.lower())
                    referenced_fonts.append(font_name)

    for field_name, props in field_props.items():
        if props.get("hidden"):
            continue
        left = float(props.get("left", 0.0))
        top = float(props.get("top", 0.0))
        width = float(props["width"]) if "width" in props else (
            float(props["right"]) - left if "right" in props else 0.0
        )
        height = float(props["height"]) if "height" in props else (
            float(props["bottom"]) - top if "bottom" in props else 0.0
        )
        if width > 1.0 and height > 1.0:
            meta.hitboxes.append(
                {
                    "field": field_name,
                    "left": left,
                    "top": top,
                    "width": width,
                    "height": height,
                    "z_index": int(props.get("z_index", 0)),
                }
            )

    meta.referenced_fonts = referenced_fonts
    return meta


def _extract_set_game_and_stylesheet(set_bytes: bytes) -> tuple[str, str]:
    """Read `game:` and `stylesheet:` from the `set` entry inside a `.mse-set` ZIP archive."""
    game = ""
    stylesheet = ""
    try:
        with zipfile.ZipFile(io.BytesIO(set_bytes), "r") as zf:
            set_member = next(
                (name for name in zf.namelist() if name.rstrip("/").endswith("set")),
                None,
            )
            if not set_member:
                return game, stylesheet
            text = zf.read(set_member).decode("utf-8-sig", errors="replace")
            for raw_line in text.splitlines():
                if raw_line.startswith("\t") or raw_line.startswith(" "):
                    continue
                stripped = raw_line.strip()
                if stripped.lower().startswith("game:"):
                    game = stripped.split(":", 1)[1].strip()
                elif stripped.lower().startswith("stylesheet:"):
                    stylesheet = stripped.split(":", 1)[1].strip()
                if game and stylesheet:
                    break
    except Exception as exc:
        logger.warning("Could not inspect uploaded .mse-set archive: %s", exc)
    return game, stylesheet


def _iter_package_dirs() -> list[tuple[Path, str, bool]]:
    """Yield (package_dir, source_split, is_custom) across upstream splits and custom_templates."""
    packages: list[tuple[Path, str, bool]] = []
    if UPSTREAM_DIR.exists():
        for split_dir in sorted(UPSTREAM_DIR.iterdir()):
            if not split_dir.is_dir():
                continue
            search_roots = [split_dir / "data", split_dir]
            seen_names: set[str] = set()
            for root in search_roots:
                if not root.is_dir():
                    continue
                for child in sorted(root.iterdir()):
                    if (
                        child.is_dir()
                        and child.name not in seen_names
                        and child.name.lower().endswith(MSE_PACKAGE_SUFFIXES)
                    ):
                        seen_names.add(child.name)
                        packages.append((child, split_dir.name, False))

    if CUSTOM_TEMPLATES_DIR.exists():
        for child in sorted(CUSTOM_TEMPLATES_DIR.iterdir()):
            if child.is_dir() and child.name.lower().endswith(MSE_PACKAGE_SUFFIXES):
                packages.append((child, "custom_templates", True))

    return packages


def _find_style_folder(game: str, stylesheet_id: str) -> Path | None:
    """Resolve the exact `.mse-style` directory matching `stylesheet_id` (custom overrides upstream)."""
    candidates: list[str] = []
    clean_id = stylesheet_id.strip()
    if clean_id:
        if clean_id.endswith(".mse-style"):
            candidates.append(clean_id.lower())
        else:
            candidates.append(f"{clean_id}.mse-style".lower())
            if game and not clean_id.lower().startswith(f"{game.lower()}-"):
                candidates.append(f"{game}-{clean_id}.mse-style".lower())

    # Search in reverse so custom_templates override upstream splits
    for pkg_dir, _, _ in reversed(_iter_package_dirs()):
        if not pkg_dir.name.lower().endswith(".mse-style"):
            continue
        if pkg_dir.name.lower() in candidates:
            return pkg_dir
    return None


def _build_sandbox_data_dir(
    sandbox_root: Path,
    extra_packages: list[Path] | None = None,
) -> Path:
    """Assemble a read-only symlinked MSE `data/` directory combining synced splits + custom templates."""
    data_dir = sandbox_root / "data"
    data_dir.mkdir(parents=True, exist_ok=True)

    # Symlink upstream splits first, then custom_templates (overwriting symlink if custom overrides)
    for pkg_dir, _, _ in _iter_package_dirs():
        link_path = data_dir / pkg_dir.name
        if link_path.is_symlink() or link_path.exists():
            link_path.unlink()
        link_path.symlink_to(pkg_dir.resolve())

    if extra_packages:
        for pkg_dir in extra_packages:
            link_path = data_dir / pkg_dir.name
            if link_path.is_symlink() or link_path.exists():
                link_path.unlink()
            link_path.symlink_to(pkg_dir.resolve())

    return data_dir


def _parse_mse_raw_packets(stdout_text: str) -> list[tuple[int, list[str]]]:
    """Parse MagicSetEditor2 `--cli --raw` output packets: status_code, line_count, k lines."""
    lines = stdout_text.splitlines()
    packets: list[tuple[int, list[str]]] = []
    idx = 0
    while idx < len(lines):
        status_line = lines[idx].strip()
        if not status_line:
            idx += 1
            continue
        if not status_line.isdigit():
            idx += 1
            continue
        status_code = int(status_line)
        if idx + 1 >= len(lines) or not lines[idx + 1].strip().isdigit():
            packets.append((status_code, []))
            break
        line_count = int(lines[idx + 1].strip())
        body = lines[idx + 2 : idx + 2 + line_count]
        packets.append((status_code, body))
        idx += 2 + line_count
    return packets


def _run_mse_cli_render(
    mse_binary: Path,
    sandbox_root: Path,
    data_dir: Path,
    set_path: Path,
    card_index: int,
    out_png_path: Path,
) -> tuple[int, list[str]]:
    """Invoke unmodified `magicseteditor --cli --raw` (under xvfb-run when available) to render card PNG."""
    # Place a symlink to the MSE binary inside sandbox_root next to `data/` AND map
    # `$HOME/.magicseteditor/data` -> `data_dir` so MSE locates all synced packs reliably
    # regardless of build-time datadir configuration.
    sandbox_bin = sandbox_root / mse_binary.name
    if not sandbox_bin.exists():
        sandbox_bin.symlink_to(mse_binary)

    fake_home = sandbox_root / "home"
    mse_home_dir = fake_home / ".magicseteditor"
    mse_home_dir.mkdir(parents=True, exist_ok=True)
    home_data_link = mse_home_dir / "data"
    if not home_data_link.exists():
        home_data_link.symlink_to(data_dir)

    # Keep user's local fonts visible inside the sandboxed HOME
    real_fonts_dir = Path.home() / ".local" / "share" / "fonts"
    if real_fonts_dir.exists():
        sandbox_share = fake_home / ".local" / "share"
        sandbox_share.mkdir(parents=True, exist_ok=True)
        sandbox_fonts_link = sandbox_share / "fonts"
        if not sandbox_fonts_link.exists():
            sandbox_fonts_link.symlink_to(real_fonts_dir)

    env = os.environ.copy()
    env["HOME"] = str(fake_home)
    env["XDG_DATA_HOME"] = str(Path.home() / ".local" / "share")

    if shutil.which("xvfb-run") is not None:
        cmd = ["xvfb-run", "-a", str(sandbox_bin), "--cli", "--raw"]
    else:
        cmd = [str(sandbox_bin), "--cli", "--raw"]

    escaped_out = str(out_png_path).replace("\\", "\\\\").replace('"', '\\"')
    stdin_script = (
        f":load {set_path}\n"
        f'write_image_file(set.cards.{card_index}, file: "{escaped_out}")\n'
        ":quit\n"
    )

    try:
        proc = subprocess.run(
            cmd,
            input=stdin_script,
            capture_output=True,
            text=True,
            cwd=str(sandbox_root),
            env=env,
            timeout=MSE_RENDER_TIMEOUT_SEC,
            check=False,
        )
    except subprocess.TimeoutExpired:
        return 2, [f"MSE CLI render timed out after {MSE_RENDER_TIMEOUT_SEC}s"]
    except OSError as exc:
        return 2, [f"Failed to launch MSE binary {mse_binary}: {exc}"]

    packets = _parse_mse_raw_packets(proc.stdout)
    max_status = 0
    messages: list[str] = []
    for code, body_lines in packets:
        if code > max_status:
            max_status = code
        for line in body_lines:
            cleaned = line.strip()
            if cleaned:
                messages.append(cleaned)

    if proc.stderr.strip():
        for err_line in proc.stderr.strip().splitlines():
            cleaned = err_line.strip()
            # Filter harmless GTK/Xvfb chatter unless rendering failed
            if cleaned and (not out_png_path.exists() or "error" in cleaned.lower()):
                messages.append(cleaned)

    if proc.returncode != 0 and not out_png_path.exists():
        max_status = max(max_status, 2)
        if not messages:
            messages.append(f"MSE exited with code {proc.returncode}")

    return max_status, messages


@app.get("/health")
async def get_health() -> dict[str, Any]:
    modern_bin = _resolve_engine_binary("mse_modern")
    legacy_bin = _resolve_engine_binary("mse_legacy")
    return {
        "status": "ok",
        "engines": {
            "mse_modern": modern_bin is not None,
            "mse_legacy": legacy_bin is not None,
        },
        "installed_splits": get_installed_splits(),
        "xvfb_available": shutil.which("xvfb-run") is not None,
        "font_count": len(_list_fontconfig_fonts()),
    }


@app.post("/upstream/sync")
async def post_upstream_sync(request: Request) -> dict[str, Any]:
    raw_body = await request.body()
    manifest: dict[str, Any] | None = None
    if raw_body.strip():
        try:
            parsed = json.loads(raw_body.decode("utf-8"))
            if isinstance(parsed, dict):
                manifest = parsed
        except json.JSONDecodeError as exc:
            raise HTTPException(status_code=400, detail=f"Invalid upstream_manifest JSON: {exc}") from exc

    if manifest is None:
        manifest = load_default_manifest()

    result = await sync_manifest(manifest)
    return {
        "status": result["status"],
        "synced_splits": result["synced_splits"],
        "fc_cache_updated": result["fc_cache_updated"],
        "message": result["message"],
        "split_shas": result.get("split_shas", {}),
    }


@app.post("/sets/render")
async def post_sets_render(
    request: Request,
    set_file: UploadFile = File(...),
    card_index: int = Form(0),
    engine_split: str = Form("mse_modern"),
    stylesheet_id: str = Form(""),
) -> Response:
    mse_binary = _resolve_engine_binary(engine_split)
    if mse_binary is None:
        raise HTTPException(
            status_code=503,
            detail=(
                f"Engine '{engine_split}' binary not found in {BIN_DIR}. "
                "Run `bash bridge/setup_termux.sh` inside Termux Ubuntu to build/install the ARM64 MSE binary."
            ),
        )

    set_bytes = await set_file.read()
    if not set_bytes:
        raise HTTPException(status_code=400, detail="Uploaded .mse-set archive is empty.")

    set_game, set_stylesheet = _extract_set_game_and_stylesheet(set_bytes)
    effective_stylesheet = stylesheet_id.strip() or set_stylesheet
    style_meta = ParsedStyleMetadata()
    style_folder = _find_style_folder(set_game, effective_stylesheet)
    if style_folder is not None:
        style_file = style_folder / "style"
        if style_file.is_file():
            style_meta = parse_mse_style_text(style_file.read_text(encoding="utf-8-sig", errors="replace"))

    installed_families = _installed_font_family_set()
    missing_fonts = [
        font for font in style_meta.referenced_fonts if font.strip().lower() not in installed_families
    ]

    async with _RENDER_LOCK:
        with tempfile.TemporaryDirectory(prefix="tcg_mse_render_") as tmp_dir_str:
            sandbox_root = Path(tmp_dir_str)
            data_dir = _build_sandbox_data_dir(sandbox_root)
            set_path = sandbox_root / "active.mse-set"
            out_png_path = sandbox_root / "rendered_card.png"
            set_path.write_bytes(set_bytes)

            status_code, messages = await asyncio.to_thread(
                _run_mse_cli_render,
                mse_binary,
                sandbox_root,
                data_dir,
                set_path,
                max(0, card_index),
                out_png_path,
            )

            if not out_png_path.exists() or out_png_path.stat().st_size == 0:
                detail = "; ".join(messages[-6:]) if messages else "MSE produced no output PNG."
                raise HTTPException(
                    status_code=422,
                    detail=f"MSE CLI render failed (status={status_code}): {detail}",
                )

            png_bytes = out_png_path.read_bytes()

    width_px = style_meta.card_width_px
    height_px = style_meta.card_height_px
    try:
        with Image.open(io.BytesIO(png_bytes)) as img:
            width_px, height_px = img.size
    except Exception:
        pass

    accept_header = request.headers.get("accept", "").lower()
    if "image/png" in accept_header and "application/json" not in accept_header:
        headers = {
            "X-Card-Width-Px": str(width_px),
            "X-Card-Height-Px": str(height_px),
            "X-Card-Dpi": str(style_meta.card_dpi),
            "X-Card-Hitboxes": json.dumps(style_meta.hitboxes, separators=(",", ":")),
            "X-Missing-Fonts": json.dumps(missing_fonts, separators=(",", ":")),
        }
        return Response(content=png_bytes, media_type="image/png", headers=headers)

    return JSONResponse(
        content={
            "png_base64": base64.b64encode(png_bytes).decode("ascii"),
            "card_width_px": width_px,
            "card_height_px": height_px,
            "card_dpi": style_meta.card_dpi,
            "hitboxes": style_meta.hitboxes,
            "missing_fonts": missing_fonts,
        }
    )


@app.get("/templates")
async def get_templates() -> dict[str, Any]:
    templates: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    # Traverse in reverse so custom_templates take precedence if overriding an upstream template
    for pkg_dir, source_split, is_custom in reversed(_iter_package_dirs()):
        lower_name = pkg_dir.name.lower()
        if not (lower_name.endswith(".mse-style") or lower_name.endswith(".mse-game")):
            continue
        if lower_name in seen_ids:
            continue
        seen_ids.add(lower_name)

        descriptor = pkg_dir / ("style" if lower_name.endswith(".mse-style") else "game")
        game_name = ""
        short_name = pkg_dir.name
        if descriptor.is_file():
            try:
                head_text = descriptor.read_text(encoding="utf-8-sig", errors="replace")[:4096]
                for raw_line in head_text.splitlines():
                    if raw_line.startswith("\t") or raw_line.startswith(" "):
                        continue
                    stripped = raw_line.strip()
                    if stripped.lower().startswith("game:"):
                        game_name = stripped.split(":", 1)[1].strip()
                    elif stripped.lower().startswith("short name:"):
                        short_name = stripped.split(":", 1)[1].strip()
            except OSError:
                pass

        if lower_name.endswith(".mse-game") and not game_name:
            game_name = pkg_dir.name[: -len(".mse-game")]

        templates.append(
            {
                "id": pkg_dir.name,
                "game": game_name,
                "short_name": short_name,
                "source_split": source_split,
                "is_custom": is_custom,
            }
        )

    templates.sort(key=lambda t: (t["game"].lower(), t["short_name"].lower(), t["id"].lower()))
    return {"templates": templates}


@app.post("/templates/validate")
async def post_templates_validate(payload: TemplateValidateRequest) -> dict[str, Any]:
    mse_binary = _resolve_engine_binary(payload.engine_split)
    if mse_binary is None:
        raise HTTPException(
            status_code=503,
            detail=f"Engine '{payload.engine_split}' binary not found in {BIN_DIR}.",
        )

    style_meta = parse_mse_style_text(payload.style_source)
    game_id = style_meta.game or "magic"
    safe_template_id = re.sub(r"[^a-zA-Z0-9._-]", "_", payload.template_id).strip("._") or "custom"
    style_folder_name = (
        safe_template_id if safe_template_id.endswith(".mse-style") else f"{game_id}-{safe_template_id}.mse-style"
    )
    stylesheet_short = style_folder_name[: -len(".mse-style")]
    if stylesheet_short.startswith(f"{game_id}-"):
        stylesheet_short = stylesheet_short[len(game_id) + 1 :]

    async with _RENDER_LOCK:
        with tempfile.TemporaryDirectory(prefix="tcg_mse_validate_") as tmp_dir_str:
            sandbox_root = Path(tmp_dir_str)
            extra_pkgs: list[Path] = []

            if payload.game_source:
                game_pkg = sandbox_root / f"{game_id}.mse-game"
                game_pkg.mkdir(parents=True, exist_ok=True)
                (game_pkg / "game").write_text(payload.game_source, encoding="utf-8")
                extra_pkgs.append(game_pkg)

            style_pkg = sandbox_root / style_folder_name
            style_pkg.mkdir(parents=True, exist_ok=True)
            (style_pkg / "style").write_text(payload.style_source, encoding="utf-8")
            extra_pkgs.append(style_pkg)

            data_dir = _build_sandbox_data_dir(sandbox_root, extra_packages=extra_pkgs)

            # Build minimal test .mse-set with strictly NO card art
            test_set_path = sandbox_root / "validate.mse-set"
            set_text = (
                "mse version: 2.0.0\n"
                f"game: {game_id}\n"
                f"stylesheet: {stylesheet_short}\n"
                "set info:\n"
                "\ttitle: Template Validation\n"
                "card:\n"
                "\thas styling: false\n"
                "\tname: Validation Card\n"
            )
            with zipfile.ZipFile(test_set_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("set", set_text.encode("utf-8"))

            out_png_path = sandbox_root / "validate_preview.png"
            status_code, messages = await asyncio.to_thread(
                _run_mse_cli_render,
                mse_binary,
                sandbox_root,
                data_dir,
                test_set_path,
                0,
                out_png_path,
            )

            preview_b64: str | None = None
            if out_png_path.exists() and out_png_path.stat().st_size > 0:
                preview_b64 = base64.b64encode(out_png_path.read_bytes()).decode("ascii")

            return {
                "valid": status_code == 0 and preview_b64 is not None,
                "status_code": status_code,
                "errors": messages if status_code != 0 or preview_b64 is None else [],
                "preview_png_base64": preview_b64,
            }


@app.get("/fonts")
async def get_fonts() -> dict[str, Any]:
    return {"fonts": _list_fontconfig_fonts()}


def _query_font_family_for_file(font_path: Path) -> list[str]:
    if shutil.which("fc-query") is not None:
        try:
            proc = subprocess.run(
                ["fc-query", "--format=%{family}\n", str(font_path)],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            if proc.returncode == 0 and proc.stdout.strip():
                families: list[str] = []
                for line in proc.stdout.splitlines():
                    for fam in line.split(","):
                        cleaned = fam.strip()
                        if cleaned and cleaned not in families:
                            families.append(cleaned)
                if families:
                    return families
        except Exception:
            pass
    return [font_path.stem.replace("_", " ").replace("-", " ").strip() or font_path.name]


@app.post("/fonts/import")
async def post_fonts_import(font_file: UploadFile = File(...)) -> dict[str, Any]:
    LICENSED_FONT_DIR.mkdir(parents=True, exist_ok=True)
    raw_bytes = await font_file.read()
    if not raw_bytes:
        raise HTTPException(status_code=400, detail="Uploaded font file is empty.")

    orig_name = Path(font_file.filename or "imported_font").name
    suffix = Path(orig_name).suffix.lower()
    installed_paths: list[Path] = []

    if suffix in FONT_EXTENSIONS:
        safe_name = re.sub(r"[^a-zA-Z0-9._ -]", "_", orig_name)
        dest = (LICENSED_FONT_DIR / safe_name).resolve()
        try:
            dest.relative_to(LICENSED_FONT_DIR.resolve())
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="Invalid font filename.") from exc
        dest.write_bytes(raw_bytes)
        installed_paths.append(dest)
    elif suffix == ".zip":
        try:
            with zipfile.ZipFile(io.BytesIO(raw_bytes), "r") as zf:
                for member in zf.infolist():
                    if member.is_dir():
                        continue
                    member_name = Path(member.filename).name
                    if not member_name or Path(member_name).suffix.lower() not in FONT_EXTENSIONS:
                        continue
                    safe_name = re.sub(r"[^a-zA-Z0-9._ -]", "_", member_name)
                    dest = (LICENSED_FONT_DIR / safe_name).resolve()
                    try:
                        dest.relative_to(LICENSED_FONT_DIR.resolve())
                    except ValueError:
                        continue
                    dest.write_bytes(zf.read(member))
                    installed_paths.append(dest)
        except zipfile.BadZipFile as exc:
            raise HTTPException(status_code=400, detail=f"Invalid font ZIP archive: {exc}") from exc
    else:
        raise HTTPException(
            status_code=400,
            detail="Unsupported font format. Upload .ttf, .otf, .ttc, or a .zip archive containing font files.",
        )

    if not installed_paths:
        raise HTTPException(
            status_code=400,
            detail="No .ttf or .otf font files were found in the uploaded file.",
        )

    _, fc_output = run_fc_cache()
    installed_families: list[str] = []
    for path in installed_paths:
        for fam in _query_font_family_for_file(path):
            if fam not in installed_families:
                installed_families.append(fam)

    return {
        "status": "ok",
        "installed_families": installed_families,
        "fc_cache_output": fc_output,
    }
