"""Delta-only upstream MagicSetEditorPacks & Font-Pack synchronizer for TCG Studio MSE Bridge.

Uses GitHub Branches, Compare, and Git Trees APIs combined with unmetered
raw.githubusercontent.com downloads (never `git clone`) to maintain read-only
upstream template splits under `bridge/workspace/upstream/<split_id>/` without
ever touching `bridge/workspace/custom_templates/`.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote

import httpx

logger = logging.getLogger("tcg_studio.sync_upstream")

BRIDGE_DIR = Path(__file__).resolve().parent
WORKSPACE_DIR = BRIDGE_DIR / "workspace"
UPSTREAM_DIR = WORKSPACE_DIR / "upstream"
CUSTOM_TEMPLATES_DIR = WORKSPACE_DIR / "custom_templates"
SYNC_STATE_FILE = WORKSPACE_DIR / "sync_state.json"
FONT_INSTALL_DIR = Path.home() / ".local" / "share" / "fonts" / "mse-studio"
LICENSED_FONT_DIR = FONT_INSTALL_DIR / "licensed"

GITHUB_API_BASE = "https://api.github.com"
RAW_GITHUB_BASE = "https://raw.githubusercontent.com"

FONT_EXTENSIONS = {".ttf", ".otf", ".ttc"}
MSE_PACKAGE_SUFFIXES = (
    ".mse-game",
    ".mse-style",
    ".mse-include",
    ".mse-symbol-font",
    ".mse-export-template",
)

MAX_CONCURRENT_DOWNLOADS = 12


def load_default_manifest() -> dict[str, Any]:
    """Default upstream_manifest.json matching TCG Studio Section 3."""
    return {
        "schema_version": 1,
        "engine_splits": [
            {
                "id": "mse_modern",
                "name": "MSE 2.5+/2.6+ Modern Engine (GenevensiS)",
                "owner": "G-e-n-e-v-e-n-s-i-S",
                "repo": "MagicSetEditor2",
                "branch": "main",
                "enabled": True,
                "engine_target": "mse_modern",
            },
            {
                "id": "mse_legacy",
                "name": "MSE2 Legacy Fallback Engine (twanvl)",
                "owner": "twanvl",
                "repo": "MagicSetEditor2",
                "branch": "master",
                "enabled": False,
                "engine_target": "mse_legacy",
            },
        ],
        "pack_splits": [
            {
                "id": "full_magic_pack",
                "name": "Full Magic Pack",
                "owner": "MagicSetEditorPacks",
                "repo": "Full-Magic-Pack",
                "branch": "main",
                "font_folder": "Magic - Fonts",
                "enabled": True,
            },
            {
                "id": "full_non_magic_pack",
                "name": "Full Non-Magic Pack",
                "owner": "MagicSetEditorPacks",
                "repo": "Full-Non-Magic-Pack",
                "branch": "main",
                "font_folder": "Other - Fonts",
                "enabled": True,
            },
            {
                "id": "font_pack",
                "name": "MSE Community Font Pack",
                "owner": "MagicSetEditorPacks",
                "repo": "Font-Pack",
                "branch": "main",
                "font_folder": "",
                "enabled": True,
            },
            {
                "id": "beta_update_pack",
                "name": "Beta Update Pack",
                "owner": "MagicSetEditorPacks",
                "repo": "Beta-Update-Pack",
                "branch": "main",
                "font_folder": "",
                "enabled": False,
            },
            {
                "id": "outdated_templates",
                "name": "Outdated Templates (Legacy)",
                "owner": "MagicSetEditorPacks",
                "repo": "Outdated-Templates",
                "branch": "main",
                "font_folder": "",
                "enabled": False,
            },
        ],
        "open_resource_connectors": [],
    }


def _github_headers() -> dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "TCGStudio-MSE-Bridge/3.0",
    }
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def _ensure_workspace_dirs() -> None:
    UPSTREAM_DIR.mkdir(parents=True, exist_ok=True)
    CUSTOM_TEMPLATES_DIR.mkdir(parents=True, exist_ok=True)
    FONT_INSTALL_DIR.mkdir(parents=True, exist_ok=True)
    LICENSED_FONT_DIR.mkdir(parents=True, exist_ok=True)


def load_sync_state() -> dict[str, Any]:
    if not SYNC_STATE_FILE.exists():
        return {"splits": {}}
    try:
        data = json.loads(SYNC_STATE_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict) and isinstance(data.get("splits"), dict):
            return data
    except Exception as exc:
        logger.warning("Failed to read %s: %s", SYNC_STATE_FILE, exc)
    return {"splits": {}}


def save_sync_state(state: dict[str, Any]) -> None:
    WORKSPACE_DIR.mkdir(parents=True, exist_ok=True)
    tmp_file = SYNC_STATE_FILE.with_suffix(".json.tmp")
    tmp_file.write_text(json.dumps(state, indent=2, sort_keys=True), encoding="utf-8")
    tmp_file.replace(SYNC_STATE_FILE)


def get_installed_splits() -> list[str]:
    if not UPSTREAM_DIR.exists():
        return []
    installed: list[str] = []
    for child in sorted(UPSTREAM_DIR.iterdir()):
        if child.is_dir() and any(child.iterdir()):
            installed.append(child.name)
    return installed


def _is_safe_rel_path(rel_path: str) -> bool:
    if not rel_path or rel_path.startswith("/") or "\\" in rel_path:
        return False
    parts = Path(rel_path).parts
    return bool(parts) and all(part not in ("", ".", "..") for part in parts)


def _should_sync_repo_path(rel_path: str, font_folder: str = "") -> bool:
    """Keep sync fast and focused on MSE template packages and fonts."""
    if not _is_safe_rel_path(rel_path):
        return False
    lower = rel_path.lower()
    if lower.endswith(tuple(FONT_EXTENSIONS)):
        return True
    if rel_path.startswith("data/"):
        return True
    if font_folder and (rel_path == font_folder or rel_path.startswith(f"{font_folder}/")):
        return True
    parts = Path(rel_path).parts
    if any(part.lower().endswith(MSE_PACKAGE_SUFFIXES) for part in parts):
        return True
    return False


def _safe_target_path(base_dir: Path, rel_path: str) -> Path | None:
    if not _is_safe_rel_path(rel_path):
        return None
    resolved_base = base_dir.resolve()
    candidate = (resolved_base / rel_path).resolve()
    try:
        candidate.relative_to(resolved_base)
    except ValueError:
        return None
    return candidate


async def _resolve_branch_head(
    client: httpx.AsyncClient,
    owner: str,
    repo: str,
    branch: str,
) -> tuple[str, str]:
    """Resolve branch HEAD commit SHA, falling back from main <-> master if needed."""
    candidates = [branch] if branch else ["main"]
    if "main" not in candidates:
        candidates.append("main")
    if "master" not in candidates:
        candidates.append("master")

    last_error = ""
    for candidate in candidates:
        url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/branches/{quote(candidate, safe='')}"
        resp = await client.get(url, headers=_github_headers())
        if resp.status_code == 200:
            payload = resp.json()
            sha = payload.get("commit", {}).get("sha")
            if sha:
                return candidate, str(sha)
        last_error = f"HTTP {resp.status_code} for {owner}/{repo}@{candidate}"
    raise RuntimeError(f"Unable to resolve GitHub branch HEAD ({last_error})")


async def _fetch_compare_delta(
    client: httpx.AsyncClient,
    owner: str,
    repo: str,
    base_sha: str,
    head_sha: str,
) -> tuple[list[str], list[str]] | None:
    """Returns (files_to_download, files_to_delete) or None if full tree reconciliation is needed."""
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/compare/{base_sha}...{head_sha}"
    resp = await client.get(url, headers=_github_headers())
    if resp.status_code != 200:
        return None
    data = resp.json()
    files = data.get("files")
    # GitHub Compare API caps files at 300 entries; fall back to tree scan if close/at cap.
    if not isinstance(files, list) or len(files) >= 300:
        return None

    to_download: list[str] = []
    to_delete: list[str] = []
    for entry in files:
        if not isinstance(entry, dict):
            continue
        status = str(entry.get("status", "")).lower()
        filename = str(entry.get("filename", ""))
        prev_name = str(entry.get("previous_filename", ""))
        if status == "removed":
            if filename:
                to_delete.append(filename)
        elif status == "renamed":
            if prev_name:
                to_delete.append(prev_name)
            if filename:
                to_download.append(filename)
        else:
            if filename:
                to_download.append(filename)
    return to_download, to_delete


async def _fetch_tree_paths(
    client: httpx.AsyncClient,
    owner: str,
    repo: str,
    head_sha: str,
) -> list[str]:
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/git/trees/{head_sha}?recursive=1"
    resp = await client.get(url, headers=_github_headers())
    if resp.status_code != 200:
        raise RuntimeError(
            f"GitHub Trees API returned HTTP {resp.status_code} for {owner}/{repo}@{head_sha[:8]}"
        )
    payload = resp.json()
    tree = payload.get("tree", [])
    paths: list[str] = []
    for item in tree:
        if isinstance(item, dict) and item.get("type") == "blob":
            path = str(item.get("path", ""))
            if path:
                paths.append(path)
    return paths


async def _download_raw_file(
    client: httpx.AsyncClient,
    semaphore: asyncio.Semaphore,
    owner: str,
    repo: str,
    ref_sha: str,
    rel_path: str,
    split_dir: Path,
) -> bool:
    target = _safe_target_path(split_dir, rel_path)
    if target is None:
        return False

    encoded_path = "/".join(quote(segment, safe="") for segment in rel_path.split("/"))
    url = f"{RAW_GITHUB_BASE}/{owner}/{repo}/{ref_sha}/{encoded_path}"

    async with semaphore:
        for attempt in range(3):
            try:
                resp = await client.get(url, follow_redirects=True)
                if resp.status_code == 200:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    tmp_path = target.with_name(f".{target.name}.part")
                    tmp_path.write_bytes(resp.content)
                    tmp_path.replace(target)
                    return True
                if resp.status_code in (404, 410):
                    return False
            except httpx.HTTPError as exc:
                if attempt == 2:
                    logger.warning("Download failed for %s: %s", rel_path, exc)
            await asyncio.sleep(0.35 * (attempt + 1))
    return False


async def sync_single_split(
    client: httpx.AsyncClient,
    split: dict[str, Any],
    state: dict[str, Any],
) -> dict[str, Any]:
    """Synchronize one upstream pack split using delta-only GitHub APIs."""
    split_id = str(split.get("id", "")).strip()
    owner = str(split.get("owner", "")).strip()
    repo = str(split.get("repo", "")).strip()
    branch = str(split.get("branch", "main")).strip() or "main"
    font_folder = str(split.get("font_folder", "") or "").strip()

    if not split_id or not owner or not repo:
        raise ValueError(f"Invalid split entry: {split}")

    split_dir = UPSTREAM_DIR / split_id
    split_dir.mkdir(parents=True, exist_ok=True)

    resolved_branch, head_sha = await _resolve_branch_head(client, owner, repo, branch)
    prev_record = state.get("splits", {}).get(split_id, {})
    prev_sha = str(prev_record.get("pinned_sha") or split.get("pinned_sha") or "").strip()

    existing_files_present = any(split_dir.rglob("*"))
    if prev_sha and prev_sha == head_sha and existing_files_present:
        logger.info("Split %s already at %s (0 delta downloads).", split_id, head_sha[:8])
        return {
            "id": split_id,
            "sha": head_sha,
            "branch": resolved_branch,
            "downloaded": 0,
            "deleted": 0,
            "up_to_date": True,
        }

    to_download: list[str] = []
    to_delete: list[str] = []

    if prev_sha and existing_files_present:
        delta = await _fetch_compare_delta(client, owner, repo, prev_sha, head_sha)
        if delta is not None:
            raw_download, raw_delete = delta
            to_download = [p for p in raw_download if _should_sync_repo_path(p, font_folder)]
            to_delete = [p for p in raw_delete if _should_sync_repo_path(p, font_folder)]

    if not prev_sha or not existing_files_present or (not to_download and not to_delete and prev_sha != head_sha):
        tree_paths = await _fetch_tree_paths(client, owner, repo, head_sha)
        wanted = {p for p in tree_paths if _should_sync_repo_path(p, font_folder)}
        # On full tree reconciliation, only download files that do not yet exist locally
        # unless SHA changed (when SHA changed and compare wasn't usable, refresh tracked files).
        if not prev_sha or not existing_files_present:
            to_download = sorted(
                p for p in wanted
                if (target := _safe_target_path(split_dir, p)) is not None and not target.exists()
            )
        else:
            to_download = sorted(wanted)
            # Remove local files no longer present in upstream tree
            for local_file in split_dir.rglob("*"):
                if local_file.is_file():
                    rel = local_file.relative_to(split_dir).as_posix()
                    if rel not in wanted:
                        to_delete.append(rel)

    deleted_count = 0
    for rel in to_delete:
        target = _safe_target_path(split_dir, rel)
        if target is not None and target.exists() and target.is_file():
            target.unlink()
            deleted_count += 1

    semaphore = asyncio.Semaphore(MAX_CONCURRENT_DOWNLOADS)
    results = await asyncio.gather(
        *(
            _download_raw_file(client, semaphore, owner, repo, head_sha, rel, split_dir)
            for rel in to_download
        )
    )
    downloaded_count = sum(1 for ok in results if ok)

    state.setdefault("splits", {})[split_id] = {
        "owner": owner,
        "repo": repo,
        "branch": resolved_branch,
        "previous_sha": prev_sha or None,
        "pinned_sha": head_sha,
        "downloaded_last_run": downloaded_count,
        "deleted_last_run": deleted_count,
        "synced_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    save_sync_state(state)

    logger.info(
        "Synced split %s (%s/%s@%s): %d downloaded, %d deleted.",
        split_id,
        owner,
        repo,
        head_sha[:8],
        downloaded_count,
        deleted_count,
    )
    return {
        "id": split_id,
        "sha": head_sha,
        "branch": resolved_branch,
        "downloaded": downloaded_count,
        "deleted": deleted_count,
        "up_to_date": False,
    }


def install_fonts_from_workspace() -> tuple[int, bool, str]:
    """Copy `.ttf`/`.otf` fonts from synced upstream packs into ~/.local/share/fonts/mse-studio/ and run fc-cache."""
    _ensure_workspace_dirs()
    copied = 0
    if UPSTREAM_DIR.exists():
        for font_file in UPSTREAM_DIR.rglob("*"):
            if not font_file.is_file():
                continue
            if font_file.suffix.lower() not in FONT_EXTENSIONS:
                continue
            dest = FONT_INSTALL_DIR / font_file.name
            try:
                if not dest.exists() or dest.stat().st_size != font_file.stat().st_size:
                    shutil.copy2(font_file, dest)
                    copied += 1
            except OSError as exc:
                logger.warning("Could not install font %s: %s", font_file, exc)

    fc_ok, fc_output = run_fc_cache()
    return copied, fc_ok, fc_output


def run_fc_cache() -> tuple[bool, str]:
    """Run `fc-cache -fv` on the MSE Studio font directory if fontconfig is available."""
    FONT_INSTALL_DIR.mkdir(parents=True, exist_ok=True)
    if shutil.which("fc-cache") is None:
        return False, "fc-cache binary not installed"
    try:
        proc = subprocess.run(
            ["fc-cache", "-fv", str(FONT_INSTALL_DIR)],
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
        combined = (proc.stdout + "\n" + proc.stderr).strip()
        return proc.returncode == 0, combined[-1000:]
    except Exception as exc:
        return False, str(exc)


async def sync_manifest(manifest: dict[str, Any] | None = None) -> dict[str, Any]:
    """Sync all enabled `pack_splits` from `manifest` (or default manifest) and refresh fonts."""
    _ensure_workspace_dirs()
    effective_manifest = manifest if isinstance(manifest, dict) else load_default_manifest()
    pack_splits = effective_manifest.get("pack_splits")
    if not isinstance(pack_splits, list) or not pack_splits:
        pack_splits = load_default_manifest()["pack_splits"]

    enabled_splits = [s for s in pack_splits if isinstance(s, dict) and s.get("enabled", True)]
    state = load_sync_state()

    synced_ids: list[str] = []
    split_shas: dict[str, str] = {}
    errors: list[str] = []
    total_downloaded = 0

    timeout = httpx.Timeout(connect=15.0, read=45.0, write=15.0, pool=30.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        for split in enabled_splits:
            split_id = str(split.get("id", "unknown"))
            try:
                summary = await sync_single_split(client, split, state)
                synced_ids.append(summary["id"])
                split_shas[summary["id"]] = summary["sha"]
                total_downloaded += int(summary["downloaded"])
            except Exception as exc:
                logger.error("Failed syncing split %s: %s", split_id, exc)
                errors.append(f"{split_id}: {exc}")

    copied_fonts, fc_ok, fc_output = install_fonts_from_workspace()

    status = "ok" if not errors or synced_ids else "error"
    summary_parts = [
        f"Synced {len(synced_ids)} split(s), downloaded {total_downloaded} file(s), installed {copied_fonts} font(s)."
    ]
    if errors:
        summary_parts.append("Warnings: " + "; ".join(errors))

    return {
        "status": status,
        "synced_splits": synced_ids,
        "fc_cache_updated": fc_ok,
        "message": " ".join(summary_parts),
        "split_shas": split_shas,
        "fc_cache_output": fc_output,
    }


def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="[%(levelname)s] %(name)s: %(message)s",
    )
    manifest: dict[str, Any] | None = None
    if len(sys.argv) > 1:
        manifest_path = Path(sys.argv[1])
        if manifest_path.exists():
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    result = asyncio.run(sync_manifest(manifest))
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
