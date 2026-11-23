# PROJECT_SPEC.md — ANDROID TCG STUDIO

## 1. STRICT ENGINEERING & ANTI-"VIBE CODING" CONTRACT (NON-NEGOTIABLE)
1. **CLIENT-SIDE ANDROID SCOPE:** Build ONLY the native Kotlin + Jetpack Compose Android client (with Firebase Cloud Storage/Firestore/Auth/AI Logic, local SQLite, Android Storage Access Framework, and HTTP/REST client networking). Do NOT generate C++ wxWidgets code or Python backend files inside this Android module.
2. **NO FAKE OR APPROXIMATED MSE RENDERER:** Do NOT draw, simulate, or approximate Magic Set Editor card frames using Jetpack Compose Canvas, SVG, HTML, or hardcoded layouts. Every rendered card PNG MUST come exclusively from the external on-device Termux ARM64 MSE Bridge (`http://127.0.0.1:8765`, user-configurable base URL) which executes unmodified upstream `G-e-n-e-v-e-n-s-i-S/MagicSetEditor2` (MSE 2.5+/2.6+) and `twanvl/MagicSetEditor2` (legacy fallback) via `magicseteditor --cli --raw`.
3. **STRICTLY NO DEFAULT CARD ART:** The card art field (`image` in `.mse-set` and in all UI/data models) MUST initialize strictly to `null` / empty / blank. NEVER supply default artwork, stock photos, placeholder illustrations, vector art, or AI-generated images inside the card art field. Artwork may ONLY populate if the user explicitly imports/crops an image file or opens a `.mse-set` that already contains embedded artwork.
4. **NO STUBS, MOCK DATA, OR FAKE RENDERING:** Do NOT generate `// TODO` placeholders, hardcoded dummy card sets, fake card preview PNGs, or simulated sync timers. When the local MSE Bridge (`127.0.0.1:8765`) is offline (such as inside a preview emulator), display a clear **Bridge Connection & Diagnostics Banner** (showing live health check status, editable bridge URL, and Termux startup instructions) while keeping all native Android features (lossless `.mse-set` ZIP parsing/editing, SQLite `.cdb` management, Font Catalog & browser payment flow, Cloud Set Library, Split-Off Set creation, and FTS5 Rulebook) 100% functional locally.
5. **REUSE REAL UPSTREAM FORMATS & SOURCE LOGIC:**
   * **Magic Set Editor 2:** Implement lossless `.mse-set` ZIP archive reading/writing preserving strict tab-indented `set` text syntax, unknown fields, and embedded image blobs unmodified, plus real `.mse-style` parser for `card width`, `card height`, `card dpi`, and field geometry (`left`, `top`, `width`, `height`, `right`, `bottom`, `z index`).
   * **DataEditorX (`ElderLich/DataEditorX` / `247321453/DataEditorX`):** Mirror DataEditorX's exact SQLite `.cdb` schema (`datas`: `id, ot, alias, setcode, type, atk, def, level, race, attribute, category`; `texts`: `id, name, desc, str1..str16`), 64-bit hex `setcode` archetype bitmasking (4 × 16-bit slots), `cardinfo_*.txt` (`##setname`/constants/counters) & `strings.conf` parsers, `mse_*.txt` CDB↔MSE field-mapping config, multi-DB diff/copy, and card-ID-linked script files (`c<id>.lua` or custom engine extensions).
6. **NO PIRATED COMMERCIAL FONTS:** Never bundle commercial font binaries or silently replace unlicensed commercial fonts. Enforce the explicit Browser Licensing / Payment Workflow defined in Section 5.

---

## 2. REAL MSE BRIDGE REST API CONTRACT (`http://127.0.0.1:8765`)
Implement a real Ktor/Retrofit `MseBridgeClient` (with configurable base URL stored in DataStore) targeting these exact endpoints:
* `GET /health` → `{ status, engines: { mse_modern, mse_legacy }, installed_splits: [...] }`
* `POST /upstream/sync` → Triggers delta-only GitHub split sync from `upstream_manifest.json` (never full git clone) into read-only `upstream/` and updates `fc-cache`.
* `POST /sets/render` → Multipart/JSON sending `.mse-set` + `card_index` + `engine_split` (`mse_modern` | `mse_legacy`) + `stylesheet_id` → returns pixel-exact rendered PNG bytes + `{ card_width_px, card_height_px, card_dpi, hitboxes: [{ field, left, top, width, height, z_index }], missing_fonts: [...] }`.
* `GET /templates` → Lists available `.mse-game` & `.mse-style` templates across all synced upstream splits and user `custom_templates/`.
* `POST /templates/validate` → Runs `magicseteditor --cli --raw` on a custom/AI-edited `.mse-game`/`.mse-style` and returns `{ valid: Boolean, status_code: Int, errors: List<String>, preview_png_base64: String? }`.
* `GET /fonts` → Lists all local fonts indexed in MSE (`fc-list` + pack font folders).
* `POST /fonts/import` → Uploads legally acquired `.ttf`/`.otf`/`.zip` bytes to the bridge, extracts `.ttf`/`.otf`, installs into local MSE font directory, runs `fc-cache -fv`, and returns updated installed font families.

---

## 3. DUAL-PACK MSE & PUBLIC GITHUB HOOKS / SPLITS (`upstream_manifest.json`)
Track and respect real upstream GitHub repository splits and branches via an editable `upstream_manifest.json` synced in-app (read-only `upstream/` layer vs. user `custom_templates/` override layer, with commit SHA pinning & 1-tap rollback):
* **Engine Splits:** `G-e-n-e-v-e-n-s-i-S/MagicSetEditor2` (modern MSE 2.5+/2.6+ for `Full-Magic-Pack`) and `twanvl/MagicSetEditor2` (legacy compatibility for older Non-Magic templates).
* **Template & Font Splits (`MagicSetEditorPacks`):**
  * `MagicSetEditorPacks/Full-Magic-Pack` (`main`, stable, `mse_modern`, `Magic - Fonts`)
  * `MagicSetEditorPacks/Full-Non-Magic-Pack` (`main` + selectable branches, stable, `mse_legacy`, `Other - Fonts`)
  * `MagicSetEditorPacks/Beta-Update-Pack` (`main`, opt-in beta channel)
  * `MagicSetEditorPacks/Outdated-Templates` (`main`, on-demand legacy `.mse-set` fallback)
  * `MagicSetEditorPacks/Font-Pack` (`main`, open font assets)
* **Open-Resource Game Connectors:** Allow registering additional public GitHub repos/branches per card game for open card frames, symbol fonts, card datasets, scripts, and open rulebooks so connected games auto-update frames without overwriting user customizations.
* **Public Update Watcher:** Poll GitHub public API with `If-None-Match` (ETag) conditional headers and public commit/release Atom feeds (plus optional user GitHub Actions webhook relay URL) to detect new commits across all splits and trigger delta-only updates on the bridge.

---

## 4. EXACT 1:1 IRL PHYSICAL CARD EDITING WINDOW & REAL UPSTREAM BASE-FRAME OFFLINE PREVIEW
* **Hardware 1:1 Physical Sizing:** Compute exact screen pixels from `LocalContext.current.resources.displayMetrics` (`xdpi`, `ydpi`) and active template dimensions (`cardWidthInches = cardWidthPx / cardDpi`, `cardHeightInches = cardHeightPx / cardDpi`, with real-world card profiles e.g. `63mm × 88mm` MTG/Pokémon and `59mm × 86mm` Yu-Gi-Oh! when DPI metadata is missing).
* **Physical Card Calibration:** Provide a persistent **Calibration Slider** (user holds an actual physical card against their Android screen glass and locks the scale factor), pinch-to-zoom/pan, and a one-tap **"Snap 1:1 IRL"** button.
* **Bridge ONLINE Rendering:** Render the 100% pixel-exact PNG returned by `POST /sets/render` at calibrated 1:1 IRL physical dimensions with invisible aligned `.mse-style` tap-to-edit hitboxes over each field.
* **Bridge OFFLINE — Real Upstream Base-Frame Compositor (NO Fake Canvas/SVG Frames):**
  * Resolve the active `.mse-style`'s real blank base-frame PNG/JPG image directly from `MagicSetEditorPacks` (`raw.githubusercontent.com` via Coil with disk caching) by inspecting parsed `choice images:`, `default:`, `card color:` / `background:` / `frame:`, and (for scripted styles) a single cached `contents` lookup on the style folder and its primary `depends on:` `.mse-include`/`.mse-style` package (strictly excluding masks, symbols, icons, and `card-sample*`).
  * Display the real upstream frame inside the exact `card width : card height` physical aspect-ratio container with proportional `.mse-style` coordinate-mapped overlays for artwork (`image` stays strictly `null`/empty until imported, respecting `z index`) and tappable live text previews using locally installed fonts when available.
  * Provide a compact Frame Color / Variant Selector below the card and a subtle status caption below the card (never stamped on top of the card face).
  * Fall back cleanly to a non-overlapping structured card layout ONLY when a style has unparseable/purely scripted coordinates or no resolvable upstream frame image.
* **Direct Tap-on-Card Editing:**
  * Tap any text/title/cost/type/rules/stat hitbox → Material 3 modal bottom sheet editor with MSE formatting/symbol bar → writes change losslessly into the `.mse-set` ZIP → re-renders via `POST /sets/render` when online or updates the live coordinate-mapped preview when offline.
  * Tap the **blank art hitbox** → launches Android Photo Picker (`PickVisualMedia`) + aspect-ratio-locked cropper matching the `.mse-style` art box → embeds image into the `.mse-set` ZIP (plus a **"Clear Art"** action that returns the art field strictly to `null`/blank) → re-renders.

---

## 5. UNIFIED SEARCHABLE FONT DROPDOWN & COMMERCIAL LICENSE BROWSER PAYMENT FLOW
* **Single Searchable Font Dropdown:** Provide a searchable **Font Dropdown Picker** in the Card/Style Editor toolbar and Font Manager screen with filter chips (`All`, `Ready ✅`, `Free ⬇️`, `Paid 🔒`) displaying:
  1. All installed free/MSE pack fonts (`Magic - Fonts`, `Other - Fonts`, `Font-Pack`, Android system fonts) & previously imported user fonts (`installed_free` / `user_purchased_installed`),
  2. Open-source downloadable fonts (`downloadable_open`, e.g., Google Fonts / SIL OFL catalog fetched on demand),
  3. Commercial/paid foundry fonts from `font_catalog.json` PLUS any missing font family dynamically detected from an active `.mse-style` file (`commercial_payment_required`).
* **Browser Payment & Post-Purchase Import Flow:**
  1. Selecting any `commercial_payment_required` entry MUST NOT fail silently or substitute an unlicensed font.
  2. Show a **Commercial Font License Choice Modal** displaying Font Name, Foundry/Vendor, License Note, and two primary actions:
     * **"Open Browser to Purchase License"** → Launches `font.purchaseUrl` (or a verified foundry license search URL if dynamically detected from a template without a catalog URL) via Android Custom Tabs / `Intent.ACTION_VIEW`.
     * **"Import Purchased Font (.ttf / .otf / .zip)"** → Opens Android SAF `OpenDocument` picker; accepts `.ttf`, `.otf`, or vendor `.zip` archives; forwards to `POST /fonts/import` (and caches locally); flips the font's dropdown badge from `Paid 🔒` to `Ready ✅ (Licensed)`; and immediately applies & re-renders the active card.

---

## 6. CLOUD STORAGE OF SETS & CUSTOM SPLIT-OFF SETS WITH `.mse-set` IMPORT
* **Cloud Set Library (All Connected Card Games):** Implement `CloudSetRepository` backed by Firebase (Cloud Firestore metadata + Firebase Storage blobs) with offline-first Room/filesystem caching, SHA-256 checksums, version snapshots, and explicit conflict resolution. Partition cleanly by `/cloud_sets/{game_id}/{split_channel}/{set_id}/` storing `.mse-set`, linked `.cdb`, scripts, custom rules, and `set_manifest.json`.
* **Custom Split-Off Sets:**
  * Allow creating a **Custom Split-Off Set** forked from any connected card game or cloud set (recording `parentSetId`, `gameId`, and `boundStylesheetId`).
  * Support **external `.mse-set` file import** via Android SAF (`OpenDocument`) directly into a Split-Off Set—merging cards, keywords, styling options, and ONLY existing embedded art (keeping cards without art strictly blank).
  * Support **Dynamic Style Binding & Re-Mapping:** Connect the Split-Off Set (or individual cards) to any compatible `.mse-style` across Magic, Non-Magic, Beta, Outdated-Templates, open-resource frames, or custom AI frames. When switching stylesheets, open an interactive **Style Field Mapper** (auto-matching identical keys, letting the user map mismatched keys cleanly) and validate against the real MSE bridge before committing.

---

## 7. DATAEDITORX WORKSPACE, LIVING RULEBOOK, AI FRAME STUDIO & FUTURE TCG ENGINE SLOT
* **DataEditorX-Style Data & Code Workspace:**
  * Native Android SQLite `.cdb`/`.db` editor mirroring DataEditorX (`datas` & `texts` tables, 64-bit hex `setcode` archetypes, bitflags, `cardinfo_*.txt`/`strings.conf` parser, multi-DB diff/copy, and bi-directional `.cdb` ↔ `.mse-set` sync via `mse_*.txt` mappings).
  * Card-ID-linked mobile Code Editor (syntax highlighting, line numbers, search, touch symbol bar) for `.lua`, `.json`, `.gd`, `.cs`, `.py`, `.ts`.
  * Keep target engine export decoupled behind a pluggable `EngineProfile` JSON adapter (ready to connect the user's separate custom TCG engine in Phase 5).
* **Living Rulebook & Custom Rules Layer:**
  * Local SQLite FTS5-indexed **Base Rulebooks** (numbered rule paragraphs) + non-destructive **`custom_rules.json` / errata overrides** that survive upstream base rulebook updates.
  * Live tap-keyword rule lookup inside the 1:1 Card Editor, automatic push of custom keywords to MSE `keywords`, rule citation links inside Card Code files, and automatic rulebook grounding for AI prompts.
* **AI Frame Studio (Real MSE Self-Healing Validation Loop):**
  * Powered by Firebase AI Logic (Gemini) editing/creating `.mse-game` & `.mse-style` files strictly in `custom_templates/` (never modifying read-only `upstream/`).
  * Every generated template edit MUST call `POST /templates/validate`. If `valid == false`, automatically feed MSE's real error lines back to Gemini for repair until real MSE validates and renders a clean preview PNG.

---

## 8. STRICT PHASED EXECUTION ORDER — IMPLEMENT PHASE 1 ONLY RIGHT NOW
Implement **Phase 1 completely with real, compilable Kotlin + Jetpack Compose code, zero mocks/stubs, and zero default card art**, then **STOP** and wait for my verification before beginning Phase 2:

* **Phase 1 (BUILD NOW):**
  1. Write `PROJECT_SPEC.md` verbatim at project root.
  2. App architecture, DI, Material 3 navigation shell, DataStore settings, and real `MseBridgeClient` (all Section 2 endpoints + Bridge Health & Termux Diagnostics panel).
  3. Real, lossless Kotlin `.mse-set` ZIP + tab-indented `set` parser/writer (guaranteeing `image` defaults strictly to `null`/empty) and `.mse-style` header/hitbox parser.
  4. **1:1 IRL Card Editor Screen:** Hardware `xdpi`/`ydpi` physical scaling, persistent **Physical Card Calibration Slider**, pinch-to-zoom + **"Snap 1:1 IRL"** button, live bridge PNG display when online or Real Upstream Base-Frame Offline Preview when offline (never a fake Compose-drawn card frame), aligned tap-on-card text editing bottom sheet, and empty-by-default art picker/cropper + Clear Art action.
  5. **Unified Searchable Font Dropdown & Commercial License Browser Payment Flow** (Section 5 complete: `font_catalog.json` seed with real open & commercial TCG font entries + official foundry purchase URLs, dynamic missing-font detection from `.mse-style`, `Magic - Fonts` & `Other - Fonts` upstream pack font discovery, `All / Ready / Free / Paid 🔒` filters, Custom Tabs/browser launch to payment URL, and SAF `.ttf`/`.otf`/`.zip` importer calling `POST /fonts/import`).
  6. **Upstream Splits & Manifest Manager Screen:** Reads/edits `upstream_manifest.json` pre-populated with all Section 3 `MagicSetEditorPacks` & `MagicSetEditor2` splits, checks GitHub public ETag/Atom updates, and triggers bridge delta-sync.
  7. **Complete Dynamic Game & Card Frame Catalog (`MseTemplateCatalogRepository`):** Zero-rate-limit dynamic discovery of all `.mse-game` and `.mse-style` packages across `MagicSetEditorPacks` + local disk cache + visual Game & Style galleries + 5-tab MSE Workspace (`Cards | Set Info | Style | Keywords | Stats`) with Undo/Redo.
* **Phase 2 (DO NOT BUILD YET):** Firebase Cloud Set Storage per connected card game + offline cache/conflict resolution + Custom Split-Off Sets with external `.mse-set` import & Dynamic Style Binding / Field Mapper.
* **Phase 3 (DO NOT BUILD YET):** DataEditorX SQLite `.cdb`/`.db` editor, `cardinfo`/`setcode` parser, `.cdb` ↔ `.mse-set` sync, and Card-ID-linked mobile Code Editor.
* **Phase 4 (DO NOT BUILD YET):** FTS5 Living Rulebook + non-destructive Custom Rules overrides + MSE keyword/script linking + AI Frame Studio with real MSE `--cli` validation loop + Open-Resource auto-updaters.
* **Phase 5 (DO NOT BUILD YET):** Pluggable `EngineProfile` adapter for custom TCG engine export.

---

## 9. AUTHENTIC MSE WELCOME WINDOW & CLASSIC 5-TAB WORKSPACE UI SPECIFICATION
All phases (Phases 1–5) MUST preserve this clean, responsive (360dp–412dp phone-first) Magic Set Editor 2 UX:
1. **Startup — Authentic MSE Welcome Window:** Launch into the MSE Welcome Window (never cold-start directly into an Untitled Set wireframe) featuring a compact header with MSE emblem (`Magic Set Editor — TCG Studio`, live synced catalog count pill, and clickable Engine Status Pill), primary actions (**New Set** 2-step visual Game → Style gallery wizard, **Open Set (`.mse-set`)**, **Open Last Set / Recent Sets** listing only real user sets), and a compact secondary tools row (**Fonts & Licenses**, **Upstream Splits**, **Bridge & Termux**).
2. **Main Set Window — Classic 5-Tab MSE Workspace:** Compact single-row Top App Bar (Back/Welcome with unsaved-changes check, Set Title + `game • style` subtitle, real Undo/Redo history buttons, Save icon with unsaved indicator dot, Font Dropdown `TT`, clickable Bridge Status Dot, and Overflow menu) above the 5 classic MSE tabs: **Cards**, **Set Info**, **Style**, **Keywords**, and **Stats** (live Card Count, Color/Type distribution bars, and Cost/Mana curve).
3. **Zero-Overlap 1:1 IRL `Cards` Stage:** Never stamp floating diagnostic pills or red banners over the card face. Render live Bridge PNGs when ONLINE, or the Real Upstream Base-Frame Compositor (with safe structured non-overlapping fallback when upstream frame/coordinates are unavailable) when OFFLINE. Keep card artwork strictly `null`/empty until imported, place the compact Frame Color/Variant selector + subtle status caption + slim scale/zoom bar (with active style mini thumbnail) cleanly BELOW the card, and maintain comfortable `12.dp–16.dp` horizontal padding on the bottom card navigator (`<` | `Card 1 / N` | `>` | `+ New` | Art).
4. **Warm-Obsidian Drafting Studio Craft:** `#111318`, `#1A1E26`, `#232834` dark obsidian surfaces with antiqued MSE gold accents (`#D4A84B`), 1dp bevel borders, soft tabletop vignette behind the card, and soft `14.dp` physical card drop shadow.

---

## 10. DYNAMIC TEMPLATE CATALOG REPOSITORY (`MseTemplateCatalogRepository`)
* **Zero-Rate-Limit GitHub Discovery:**
  1. Calls `GET https://api.github.com/repos/MagicSetEditorPacks/{repo}/contents/data` (1 ETag-cached API call per repo) for `Full-Magic-Pack` (default on), `Full-Non-Magic-Pack` (default on), `Beta-Update-Pack` (opt-in toggle), and `Outdated-Templates` (opt-in toggle).
  2. Discovers all `.mse-game`, `.mse-style`, and `.mse-include` folders.
  3. Fetches plain-text `game` and `style` files via `https://raw.githubusercontent.com/MagicSetEditorPacks/{repo}/{branch}/data/{folder}/{game|style}` (unmetered raw CDN) with bounded coroutine concurrency (`Semaphore(10)`).
  4. Parses `full name:`, `short name:`, `game:`, `icon:`, `card width:`, `card height:`, `card dpi:`, `position hint:`, `depends on:`, and `card style:` field blocks (`left`, `top`, `width`, `height`, `right`, `bottom`, `z index`, `choice images:`, `default:`).
  5. Builds real MSE thumbnail URLs (`raw.githubusercontent.com/.../{icon}`) loaded via Coil with disk+memory caching.
  6. Persists the catalog locally to JSON disk cache immediately with incremental sync progress feedback and manual Refresh Catalog action, merging local `GET /templates` when the Bridge is online.
  7. Scans `Magic - Fonts` and `Other - Fonts` via the same 1-call `contents` endpoint so all real MSE pack fonts appear in the Searchable Font Dropdown.

---

## 11. HARDENED OFFLINE REAL-FRAME RESOLVER & VISIBLE FIELD RULES
When the local MSE Bridge is offline, the Android client MUST strictly enforce these visual integrity gates (never drawing fake Compose Canvas card frames and never stretching tiny icon/overlay PNGs):
1. **Strict Non-Card Asset Blacklist:** Reject any filename/path containing non-card overlay/icon/mask tokens (`slice`, `loyalty`, `flash`, `dot`, `mask`, `pt`, `stamp`, `holo`, `crown`, `nyx`, `symbol`, `icon`, `watermark`, `border`, `overlay`, `arrow`, `bar`, `blend`, `foil`, `sample`, `back` except `background`, `flip`, `transform`, `dfc`, `mdfc`, `saga`, `level`, `class`, `chapter`, `pip`, `mana`, `rare`, `common`, `uncommon`, `mythic`, `tombstone`, `companion`, `mutate`, `adventure`, `lesson`, `fuse`, `split`, `modal`, `snow_overlay`, `shadow`, `glow`, `logo`).
2. **Real Full-Card Frame Discovery:** Resolve explicit `choice images:` / `card color:` base frames first, then Magic base frames (`ccard`, `wcard`, `ucard`, `bcard`, `rcard`, `gcard`, `mcard`, `acard`, `lcard`) from the active style, `cards/` subfolder, `magic-m15-altered.mse-style`, or `magic-modules.mse-include/cards`; for Non-Magic styles, inspect the style root and immediate subfolders (`cards/`, `frames/`, `bg/`). Display full clean human-readable labels (`Colorless`, `White`, `Blue`, `Black`, `Red`, `Green`, `Gold`, `Artifact`, `Land`, or cleaned Non-Magic names).
3. **Mandatory Portrait Card Bitmap Validation Gate:** Every candidate frame MUST be validated (`width >= 250 && height >= 350 && height/width in 1.25f..1.65f`) before rendering (cached in `CatalogDiskCache` and re-checked on runtime `BitmapDrawable`). If no valid full-card portrait frame passes, fall back cleanly to `StructuredOfflineCardLayout`.
4. **Visible Core-Field Allowlist & Luminance-Aware Contrast:** Parse `visible:` (`false`, `no`, or conditional scripts) as hidden/conditional and filter Offline overlays to core visible card fields only (Magic: `name`, `casting cost`, `type`, `super type`, `sub type`, `rule text`, `flavor text`, `pt`, `illustrator`, `copyright`; Non-Magic: core non-internal text fields, strictly excluding `alias`, `border color`, `card color 2`, `loyalty`, `stamp`, `level`, `chapter`, `transform`, `indicator`, `foil`, `notes`, `stylesheet`). Sample pixel luminance behind each hitbox to guarantee legible `#111111` text on light regions and `#F5F5F5` + drop shadow on dark regions, while keeping the artwork region strictly empty (`image == null`).

---

## 12. REPO-ROOT ON-DEVICE TERMUX ARM64 MSE BRIDGE & CI COMPILER LAYOUT
Located at the repository root **outside** the Android `:app` Gradle module (`settings.gradle.kts` includes only `:app`):
* `/.github/workflows/build-mse-arm64.yml` — Native ARM64 Ubuntu 24.04 workflow compiling unmodified `G-e-n-e-v-e-n-s-i-S/MagicSetEditor2` (`magicseteditor-modern-arm64`) and best-effort `twanvl/MagicSetEditor2` (`magicseteditor-legacy-arm64`).
* `/bridge/requirements.txt` — Python dependencies (`fastapi`, `uvicorn`, `python-multipart`, `httpx`, `Pillow`).
* `/bridge/sync_upstream.py` — Delta-only GitHub Branches/Compare/Trees + `raw.githubusercontent.com` synchronizer populating read-only `bridge/workspace/upstream/<split_id>/` (preserving `bridge/workspace/custom_templates/`) and installing `.ttf`/`.otf` fonts into `~/.local/share/fonts/mse-studio/` + `fc-cache -fv`.
* `/bridge/server.py` — FastAPI localhost server (`127.0.0.1:8765`) implementing `GET /health`, `POST /upstream/sync`, `POST /sets/render`, `GET /templates`, `POST /templates/validate`, `GET /fonts`, and `POST /fonts/import`, invoking unmodified `xvfb-run -a <mse_binary> --cli --raw`.
* `/bridge/setup_termux.sh` & `/bridge/start_bridge.sh` — Idempotent Termux `proot-distro` Ubuntu ARM64 setup and localhost launch scripts, surfaced via 1-tap copyable setup cards inside the Android app's **Bridge & Termux** modal.

---

## 13. BIDIRECTIONAL GITHUB SYNC BRIDGE (NEXUS MASTER HUB <-> MPV CARD MAKER)
* **Root Manifest & CI Sync Workflow (Repo Root):**
  * `/nexus-repo-link.json` — Canonical mutual repo link manifest (`thisRepo: "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated"`, `linkedHubRepo: "Summer-Warrior/Project-MPV-RP-Master-Hub"`, `bridgeSchemaVersion: "1.1.0"`, `hubIndexPath: "nexus-bridge/client-codestring-index.json"`, `exportedPacksDir: "nexus-bridge/card-packs/"`).
  * `/.github/workflows/mpv-nexus-sync.yml` — Automated GitHub Actions workflow pulling live Hub index snapshots from `Summer-Warrior/Project-MPV-RP-Master-Hub` and validating exported packs against Locked-Client Firewall, 21-digit register boundaries, and Notebook Time-Travel guardrails.
* **Native Kotlin Engine & Service Architecture:**
  * Pure Kotlin validation/schema layer (`domain/nexus/NexusSchemas.kt`, `domain/nexus/CardCodeValidator.kt`, zero UI imports): strict runtime JSON schema validation, Locked-Client Firewall (`upstreamSourceLock == false` writable; `upstreamSourceLock == true` strictly read-only with "Target Non-Locked 21-Digit Fork Instead" redirect), arbitrary-length IDs + native `BigInteger` 21-digit register math (`^[0-9]{1,21}$`, rejecting `.*[0-9]{22}.*`), `c<21-digits>.lua` & JSON pack generation, unified 20-Color Pie wheel (`W, Iv, Cy, Az, U, In, Vi, Ob, B, Cr, Mg, Sc, R, Or, Am, Ch, G, Ja, Te, Aq` + `{C}`, loads with ZERO default colors selected, zero invented philosophies/emotions/moral alignments, full `{A·B}`, `{A/B}`, `{A|B}`, `{A>B}`, `{2A/B}`, `:T/:S/:M`, `{-X}`, `{-(A,B)}`, `{Ω}`, and 6 `{Ω·M..A}` sacred geometries), modular manual-friendly Ultimate TCG WIP fields, and strict Match Gameplay Time-Travel guardrail (`allowTimeTravelFlagsOnMatchCards == false`, Filters 19–21 blocked unless explicitly switched to Avatar Profile / Memorial Forge Dossier mode; only `"End Turn"` and `"Extra Turn"` temporal effects permitted on match cards).
  * Live GitHub REST API service (`data/nexus/NexusHubBridgeService.kt`, ETag `If-None-Match` cached, headers `X-GitHub-Api-Version: 2026-03-10` & `Accept: application/vnd.github+json`): mutual repo handshake (`UNLINKED`, `ONE_WAY_ONLY`, `LINKED_OUT_OF_SYNC`, `LINKED_AND_CURRENT`), Hub index pull + changelog diff review before local apply, deep-link handshake (`tcgstudio://nexus-hub?hubSync=1&exportClientInfo=1&from=nexustcg`), Option A Direct Commit (optional Fine-Grained PAT scoped ONLY to Contents: Read & Write, session-only by default, never hardcoded or logged, GET-SHA + Base64 PUT with 409/422 conflict recovery), and Option B Zero-Token Mobile & GitHub Actions Sync.

---

## 14. TWO-WAY GAME ENGINE HANDSHAKE, LIVE DATA READER, CARD FRAME REVIEWER & ON-OPEN ALIGNMENT CHECK
* **Root Manifests & Zero-Build Web Surface (Outside `:app`):**
  * `/nexus-app.json` — Master Hub registration (`category: "TCG_TOOL"`, `launchType: "ANDROID_APK_AND_WEB"`, `linkedMasterHub: "Summer-Warrior/Project-MPV-RP-Master-Hub"`, `linkedGameEngineRepo: "Summer-Warrior/Tcg-game-engine-test"`, `androidPackageName: "com.aistudio.tcgstudio.kqrmvx"`, `deepLinkScheme: "tcgstudio://nexus-hub"`, `gameEngineAndroidPackage: "com.aistudio.nexustcg.vxqrmk"`, `gameEngineDeepLinkScheme: "nexus-tcg-engine://open"`, `distributionPolicy: "GITHUB_RELEASES_PUBLIC_PLAY_STORE_PERSONAL_ONLY"`, `requiredDiscordLock`, `masterHubAdminGate`).
  * `/nexus-repo-link.json` — Mutual links to `Summer-Warrior/Project-MPV-RP-Master-Hub` and formal Two-Way Handshake link to `Summer-Warrior/Tcg-game-engine-test`.
  * `/handshake-contract.json` — Dual-Engine Update Consent contract v1.1.0 (`cardMakerConsent` & `gameEngineConsent`); incoming changes remain `STAGED` until BOTH engines record `"CONSENTED"` for the exact target commit SHA.
  * `/engine-sync-snapshot.json` & `/docs/engine-health-status.json` — Verified baseline engine snapshot aligned with live `Tcg-game-engine-test` enums (`DRAW, STANDBY, MAIN, COMBAT, END`; `DIRECT_DAMAGE_BURN, LIFE_RESTORE_HEAL, ATK_DEF_BUFF, DRAW_CARDS, RESOURCE_RAMP, TARGET_DESTROY_REMOVAL, NEGATE_AND_COUNTER`), canonical non-locked variants (`ULTIMATE_TCG_WIP`, `EDOPRO_CUSTOM_FORK`, `FORGE_CUSTOM_CLONE`, `HYBRID_FORGE_EDOPRO_MIX`), and `firestore.rules` bounds (`customCardName <= 100`, `customCardAbility <= 1000`).
  * `/.github/workflows/engine-compatibility-watch.yml` — Scheduled watchdog running Benefit-Integrity Audits, deduplicating by commit SHA, sending `repository_dispatch` (`nexus_engine_breaking_alert`) to `Summer-Warrior/Project-MPV-RP-Master-Hub`, Discord Admin Role webhook alerts, and Admin email alerts.
  * `/docs/index.html` — Zero-build self-contained public landing page with live ETag-cached engine reader, Dual-Engine Consent status, `?hubSync=1` / `?exportClientInfo=1` & `window.postMessage` (`NEXUS_HUB_PING` -> `NEXUS_APP_MANIFEST_READY` & `MPV_CARD_MAKER_READY`), Card Frame Reviewer (`@V2|...` unified descriptor + unified 20-Color Pie wheel + 6 Layer 20 `{Ω·M..A}` sacred geometries), and Alignment Warning Modal (inspection visible to all; Admin actions gated).
* **Native Android Implementation (`domain/nexus/`, `data/nexus/`, `ui/nexus/`):**
  * `InstalledAppsBridge` (`<queries>` Play Creator Program compliant): detects private Play Store install of `com.aistudio.nexustcg.vxqrmk` (`versionName`/`versionCode`) and opens `nexus-tcg-engine://open?section=settings&hubSync=1&from=tcgstudio`.
  * `EngineSyncIngestionService` & `EngineCompatibilityAuditor`: ETag `304 Not Modified` cached-body safe live ingestion from `Tcg-game-engine-test`, balanced-brace parameterized Kotlin enum parser, targeted `firestore.rules` bounds parser, unified `@V2|...` + legacy descriptor generation, and automatic on-open Alignment Warning Pop-Up (separating alignment visibility from Master Hub Admin-only escalation actions).

