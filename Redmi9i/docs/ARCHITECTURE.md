# Architecture

How the two apps are built and why. For the commands to build and install them, see `CLAUDE.md`. The original
design specs and plans are in `docs/superpowers/`; this file describes what exists now and notes where the
build differs from those specs.

Both apps are Kotlin with plain Android Views. `minSdk 29`, `targetSdk 29` (so Termux-style exec and legacy storage
rules still apply), no Compose, no networking libraries. Business logic is kept in pure functions and small classes so
it runs in JVM unit tests on the PC (JUnit 4 with `org.json`); 56 tests for the launcher, 43 for Files.

## Grand Line launcher (`grandline-app/`, package `dev.redmi9i.grandline`)

### Packages

| Package | Responsibility |
| --- | --- |
| `data` | `LayoutModel` (immutable `Layout`, `Page`, `Cell`; `LayoutOps` moves, swaps, removes, JSON in and out), `Reconcile` (merge the installed app list into the saved layout), `AppRepository` (launcher activities and `filterApps`), `LayoutStore` (SharedPreferences: layout, hidden, known, flags, weather cache, wallpaper choices) |
| `home` | `HomeActivity` (everything on screen), `PageGridView`, `DockView`, `IconCellView`, `PageDots`, `CellGestures`, `DrawerView`, `CrewPanel`, `WallpaperView`, `VideoWallpaperView`, `WallpaperSheet` |
| `icons` | `IconProvider` interface, `MedallionIconProvider` (the user's drawn icon when present, else the app icon in a gold-ringed rounded medallion; cached) |
| `lock` | `LockService` (accessibility service), `LockView` (poster lock UI), `QuickMenuView` and `QuickIcon` (accessibility-button menu), `LockEnabler` |
| `widgets`, `weather` | `WidgetRegistry` and host, `WeatherView`, `WeatherGlyph`; `WeatherModel` (parsing, pure), `WeatherRepo` (network and cache) |
| `theme` | `Palette`, `Type` (Pirata One, SIL OFL, bundled), `Shapes`, `Art` (asset loader), `Crew` (crew of the day), `SystemWallpaper` |
| `settings` | `SettingsActivity` (built in code: pages, add-new-apps toggle, clear home, lock toggle, wallpapers, hidden apps, launcher switch) |
| `termux` | `TermuxBoot` |

### Layout model and reconciliation

- A `Layout` has `rows x cols` (5 x 4), a list of pages (each a list of `Cell(r, c, key)`) and a 4-slot dock.
  `key` is a flattened component name such as `com.brave.browser/com.google.android.apps.chrome.Main`.
- `LayoutOps` are pure: every function returns a new `Layout`. Dropping on an occupied cell swaps; the dock accepts
  drops even when empty.
- `reconcile(saved, installed, known, hidden, dockPrefer, skipFromHome, pageCount, autoPlace)`:
  first run builds the dock from `dockPrefer`; later runs remove uninstalled and hidden apps, and place apps never
  seen before into the first free cell only when `autoPlace` is on. Other launchers (`skipFromHome`) are never placed.
  **Safety guard:** an empty installed list (a package-manager hiccup) must not wipe the saved layout.
- Default stance: home is empty. `autoPlace` defaults off, the dock fades out when empty and fades in during a drag.
- Hidden apps: `LayoutStore.hidden` (component keys). `seededHidden` records which packages were hidden by default
  already, so a new default applies once and a later Unhide sticks. `HomeActivity.hiddenByDefault` lists them; they are
  only hidden, never uninstalled.

### Home screen

- Pager of pages, each with its own wallpaper (image or video). Transparent navigation bar: the wallpaper shows behind
  the three buttons and content is padded above them (`SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION`). No drawer chevron,
  no page dots; swipe or fling up opens the drawer.
- Long-press an icon: Remove or Add to home, App info, Uninstall (user apps), Hide. Native drag and drop between
  home, dock and a Remove zone. Long-press empty space opens `WallpaperSheet` (thumbnails of images and videos, one tap
  applies to the current page; a link to launcher settings).
- Home key returns to page 1. Wallpaper dim is 0 while home and dock are empty and a fixed 0.30 once any icon is
  placed. The drawer keeps 0.82.
- On start the launcher calls `TermuxBoot.ensureServices` and `LockEnabler.ensureEnabled`.

### Drawer and crew panel

`DrawerView` is a column: `CrewPanel`, search field, alphabetical app list. The panel is **open by default**:
poster of the day, large clock, date, crew name and the weather card. Tapping the poster or clock folds it into a pill
(time, temperature, crew name); it re-opens every time the drawer closes. `Crew.ofDay(epochDay)` is
`members[floorMod(epochDay, 10)]` (Luffy, Zoro, Nami, Usopp, Vinsmoke_Sanji, Tony_Tony_Chopper, Nico_Robin, Franky,
Brook, Jinbe); `nextFor` picks a random member different from the previous one for the lock screen. The Files app
duplicates the rule so both apps agree.

### Wallpapers

- Still images are decoded with `inSampleSize`, cover-cropped and cached (at most three in memory).
- Video: `VideoWallpaperView` is a `TextureView` with one muted, looping `MediaPlayer`, cover-cropped by matrix. A
  `TextureView` cannot have a background drawable, so a still `ImageView` (a frame one second into the video) sits
  underneath and shows while the video starts. Playback stops when the activity pauses, the drawer opens or the screen
  turns off, so a hidden wallpaper costs nothing.
- `SystemWallpaper.sync` writes a still of the current home wallpaper into Android's system wallpaper whenever it
  changes (`SET_WALLPAPER`, cache key prefixed `v2:`). Reason: the stock recents screen is see-through and used to
  flash the vendor's default wallpaper. It skips the write when a real live wallpaper is set.
- If the chosen wallpaper file is missing, the first file in `wallpapers/` is used, then a drawn sea scene.

### Lock overlay and accessibility button (`LockService`)

One accessibility service does both jobs. It declares `typeAnnouncement` events only, `canRetrieveWindowContent=false`
and `flagRequestAccessibilityButton`.

- **Poster lock:** a screen-off receiver shows `LockView` in a `TYPE_ACCESSIBILITY_OVERLAY` window (which draws above the
  status and navigation bars), positioned at `y = -statusBarHeight` with height `realHeight + statusBarHeight` so it
  covers everything. A random wanted poster (never the previous one, stored in `lastLockPoster`), the time and date;
  swipe or fling up opens. Only the inner content slides; sliding the opaque window itself leaves trails.
  **It is decoration, with no PIN.** Toggle: launcher settings, "Poster lock screen".
- **Accessibility button menu:** the button in the navigation bar opens `QuickMenuView` (Power, Recents, Lock,
  Notifications, Quick settings, Screenshot, Accessibility), each a drawn gold line icon (`QuickIcon`) with its name
  below, using `performGlobalAction`. It replaces the stock menu (which contained the Google Assistant); the stock menu
  service is removed from `enabled_accessibility_services`. The menu closes first (150 ms) so it is not in the
  screenshot, dismisses on an outside tap or the button again, and times out after 8 s because nothing reports Home or
  Back presses.
- **Staying alive:** Android restarts enabled accessibility services after kill or reboot, but a force-stop disables the
  service. `LockEnabler.ensureEnabled` (run on every launcher start, needs `WRITE_SECURE_SETTINGS`) adds it back and removes the stock
  accessibility menu (in both its full and its short `com.android.settings/.accessibility...` spelling) from the enabled list, because
  while that is enabled it owns the navigation-bar button. The service configuration is cached by the system; after changing
  `res/xml/lock_service.xml`, reinstall, force-stop and start the launcher again.

### Weather

`WeatherRepo` calls Open-Meteo (no API key; needs `INTERNET`): geocoding for the typed city, forecast for current
conditions and a three-day outlook. Results are cached as JSON in preferences (`weather_place`, `weather_json`,
`weather_at`) and refreshed after 30 minutes; offline keeps the old data. `WeatherParse` (pure, tested) maps weather
codes to a `Sky` and flavour lines ("A calm night on the sea."). `WeatherGlyph` draws the sky icons with Canvas. The city
is never guessed: tap the card the first time (long-press later) to type one.

### Icons

`MedallionIconProvider(ctx, sizePx, art)` maps a package to a file in `/sdcard/GrandLine/icons/` (`browser`, `camera`,
`files`, `security`, `settings`, `telegram`, `termux`; Termux:API and Termux:Boot reuse `termux`). Found: the art is
scaled to the cell size, no frame. Not found: the app's own icon clipped to a rounded square with a gold ring. To theme
another app, add a file and one entry to the `themed` map.

### Termux boot helper

`TermuxBoot.ensureServices` (once per launcher process) sends Termux a `com.termux.RUN_COMMAND` intent for
`~/bin/ensure-services.sh`, which takes the wake lock and starts `crond` if it is not running. It needs
`allow-external-apps = true` in `~/.termux/termux.properties` and the `RUN_COMMAND` permission granted by
`tools/grant-launcher-permissions.sh`. It exists because Termux:Boot's JobScheduler job is unreliable under aggressive
vendor battery management; the always-running launcher does not depend on it.

### Permissions

`INTERNET` (weather), `READ_EXTERNAL_STORAGE` (the asset folder), `REQUEST_DELETE_PACKAGES` (uninstall from the
menu), `com.termux.permission.RUN_COMMAND`, `WRITE_SECURE_SETTINGS` (granted by adb, not user-grantable),
`SET_WALLPAPER`. `HomeActivity` is `excludeFromRecents` and declares `HOME` and `DEFAULT` only, never `LAUNCHER`.

### Measured, and differences from the original spec

Memory about 50 MB PSS with the drawer open; cold start 2.36 s on the reference phone (the target was 2 s; left as is).
Differences from `docs/superpowers/specs/2026-11-01-grandline-launcher-design.md`: there are no separate `AppMenu`,
`HomePager` or `DragController` files (the menu, pager adapter and native drag and drop live in `HomeActivity`); the drawer
uses `drawer.jpg` if present, otherwise the dimmed home wallpaper (not a blurred copy); the first run seeds nothing; the
brightness-based scrim (`Scrim`) was removed in favour of the fixed 0.30 and 0.82 dims. Rollback of the default home is tested in both
directions: `cmd package set-home-activity app.lawnchair/.LawnchairLauncher` and
`... dev.redmi9i.grandline/.home.HomeActivity`.

### Things deliberately not built

Custom recents (the stock recents paints a static dimmed snapshot of the system wallpaper; a live background cannot show
through, and the Recents button raises no key event a service can intercept), a real live-wallpaper service (same
reason), a PIN lock, widgets beyond the crew panel and weather, a plug-in registry beyond `WidgetRegistry`.

## Files (`files-app/`, package `dev.redmi9i.files`)

| Unit | Does |
| --- | --- |
| `storage.FileRef`, `FsProvider`, `LocalFs`, `SafFs`, `FsRouter` | A small value type (provider id plus path or URI) so UI never touches `File` or `Uri`. `LocalFs` over `java.io.File`; `SafFs` over `DocumentsContract` for removable volumes with one persisted folder grant per volume (an Android 10 rule). `FsRouter` picks the provider. |
| `storage.VolumeMonitor` | Internal, SD and USB volumes, mount and unmount events |
| `ops.OperationEngine`, `OperationService` | Copy, move, delete, extract, compress as cancellable coroutines with progress; same-provider move uses rename; name clashes are asked once per paste; foreground service with a notification and cancel |
| `search.SearchEngine` | Recursive, cancellable filename search for the current folder or the whole drive, results stream in |
| `docs.DocxRenderer`, `XlsxRenderer`, `XmlDom` | Pure `File` to self-contained HTML; paragraphs, headings, emphasis, lists, tables, images; xlsx cached values, capped at 2000 rows and 100 columns |
| `ui.*` | `MainActivity` (volumes, folder list, selection, sort, search), `BrowserAdapter` (warm per-kind colours, gold folders), `ThumbnailLoader` (bounded pool, LRU), `Dialogs`, `Format`, `Crew` (watermark of the day) |
| `viewer.*` | Image (pinch zoom), video and audio, PDF (`PdfRenderer`), text (editable, files over 1 MB open read-only showing the first 2 MB), docx and xlsx in a locked-down `WebView` (JS off, no file or network access), zip as a virtual folder |

Security: no `INTERNET` permission; `FileProvider` for sharing; zip entries are checked for path traversal before
extraction; WebView content is HTML-escaped.

Theme: dark `Theme.AppCompat.NoActionBar`, Pirata One titles, sea-blue toolbar with a gold line, themed dialogs, popup and
overflow menu, a gold selection tint, a faint watermark of the crew member of the day from `/sdcard/GrandLine/marks/`
(absent file means no watermark), and a gold folder launcher icon on deep sea.

Differences from `docs/superpowers/specs/2026-11-01-file-manager-design.md`: the docx and xlsx renderers take a `File`
(random-access zip), files over 1 MB open read-only showing the first 2 MB (no chunking), and name clashes are asked once per
paste rather than per file (`Conflict` has no `ASK` value). Verified features: browse, thumbnails, sort, search, new folder, copy,
move, rename, delete, share sheet, properties, compress and extract (md5 identical), and every viewer. The stock file manager is
untouched.

Known limits (by design): legacy `.doc` and `.xls`, `.7z` and `.rar`, background audio, trash bin, docx
headers, footers and comments, and animated GIFs (first frame). Verified: 5000 files in one folder uses about 50 MB.
Not yet verified: a physical USB or SD drive (see `docs/ROADMAP.md`) and the video, zip and text viewers in the new theme.
