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

## 4. EXACT 1:1 IRL PHYSICAL CARD EDITING WINDOW
* **Hardware 1:1 Physical Sizing:** Compute exact screen pixels from `LocalContext.current.resources.displayMetrics` (`xdpi`, `ydpi`) and active template dimensions (`cardWidthInches = cardWidthPx / cardDpi`, `cardHeightInches = cardHeightPx / cardDpi`, with real-world card profiles e.g. `63mm × 88mm` MTG/Pokémon and `59mm × 86mm` Yu-Gi-Oh! when DPI metadata is missing).
* **Physical Card Calibration:** Provide a persistent **Calibration Slider** (user holds an actual physical card against their Android screen glass and locks the scale factor), pinch-to-zoom/pan, and a one-tap **"Snap 1:1 IRL"** button.
* **Direct Tap-on-Card Editing:** Render transparent touch hitboxes aligned proportionally over the MSE-rendered PNG using the `.mse-style` bounding boxes:
  * Tap any text/title/cost/type/rules/stat hitbox → Material 3 modal bottom sheet editor with MSE formatting/symbol bar → writes change losslessly into the `.mse-set` ZIP → re-renders via `POST /sets/render`.
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
  4. **1:1 IRL Card Editor Screen:** Hardware `xdpi`/`ydpi` physical scaling, persistent **Physical Card Calibration Slider**, pinch-to-zoom + **"Snap 1:1 IRL"** button, live bridge PNG display (or clean diagnostic overlay when bridge is offline—never a fake card drawing), aligned tap-on-card text editing bottom sheet, and empty-by-default art picker/cropper + Clear Art action.
  5. **Unified Searchable Font Dropdown & Commercial License Browser Payment Flow** (Section 5 complete: `font_catalog.json` seed with real open & commercial TCG font entries + official foundry purchase URLs, dynamic missing-font detection from `.mse-style`, `All / Ready / Free / Paid 🔒` filters, Custom Tabs/browser launch to payment URL, and SAF `.ttf`/`.otf`/`.zip` importer calling `POST /fonts/import`).
  6. **Upstream Splits & Manifest Manager Screen:** Reads/edits `upstream_manifest.json` pre-populated with all Section 3 `MagicSetEditorPacks` & `MagicSetEditor2` splits, checks GitHub public ETag/Atom updates, and triggers bridge delta-sync.
* **Phase 2 (DO NOT BUILD YET):** Firebase Cloud Set Storage per connected card game + offline cache/conflict resolution + Custom Split-Off Sets with external `.mse-set` import & Dynamic Style Binding / Field Mapper.
* **Phase 3 (DO NOT BUILD YET):** DataEditorX SQLite `.cdb`/`.db` editor, `cardinfo`/`setcode` parser, `.cdb` ↔ `.mse-set` sync, and Card-ID-linked mobile Code Editor.
* **Phase 4 (DO NOT BUILD YET):** FTS5 Living Rulebook + non-destructive Custom Rules overrides + MSE keyword/script linking + AI Frame Studio with real MSE `--cli` validation loop + Open-Resource auto-updaters.
* **Phase 5 (DO NOT BUILD YET):** Pluggable `EngineProfile` adapter for custom TCG engine export.

---

## 9. AUTHENTIC MSE WELCOME WINDOW & CLASSIC TABBED WORKSPACE UI SPECIFICATION
All phases (Phases 1–5) MUST preserve this clean, responsive (360dp–412dp phone-first) Magic Set Editor 2 UX:
1. **Startup — Authentic MSE Welcome Window:** Launch into the MSE Welcome Window (never cold-start directly into an Untitled Set wireframe) featuring a compact header (`Magic Set Editor — TCG Studio` + clickable Engine Status Pill), primary actions (**New Set** 2-step Game → Style wizard, **Open Set (`.mse-set`)**, **Open Last Set / Recent Sets** listing only real user sets), and a compact secondary tools row (**Fonts & Licenses**, **Upstream Splits**, **Bridge & Termux**).
2. **Main Set Window — Classic MSE Tabbed Workspace:** Compact single-row Top App Bar (Home/Back with unsaved-changes check, Set Title + `game • style` subtitle, clickable Bridge Status Dot, Font Picker icon, and Overflow menu) above the 4 classic MSE tabs: **Cards**, **Set Info**, **Style**, and **Keywords**.
3. **Zero-Overlap 1:1 IRL `Cards` Stage:** Never stamp floating diagnostic pills or red banners over the card face. When the Bridge is ONLINE, render the exact PNG from `POST /sets/render` scaled by hardware `xdpi`/`ydpi` × calibration with invisible aligned `.mse-style` tap hitboxes. When the Bridge is OFFLINE, render a clean non-overlapping structured card layout (`Header Row` → `Artwork Dropzone` → `Type Line` → `Rules & Flavor Box` → dedicated `Footer Row` with Illustrator and `P / T` pill) with strictly empty artwork (`image == null`) until imported. Place a slim `36.dp` scale/zoom bar cleanly BELOW the card and a compact non-clipping MSE bottom card bar (`<` | `Card 1 / N` opening the expandable MSE Card List Table sheet | `>` | `+ New` | Art) at the bottom.
4. **Zero Developer Jargon & Cohesive Dark Graphite Theme:** Use a refined dark graphite/slate MSE studio theme with warm gold/amber accents, 1dp borders, and clean sans-serif UI typography (no debug monospace walls or raw field placeholders).

