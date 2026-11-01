# Remote key map — TCL UnionTV (captured 2026-10-28, T3)

Source: `getevent -lt` plus the firmware's own `KeyEventManager`/`KCR-KeyConverter`/`CustomFunction` log lines, with every button pressed on the real remote. All keys arrive from one input device, `mtkinp` (IR); the other devices (`virtual-search`, `rtk_keypad`, `sim-kb`, `sim-mouse`) sent nothing. Power was not pressed on purpose (it would suspend the TV and drop ADB).

## Standard Android keys (reach the framework as normal KeyEvents; the launcher's key services can see them)

| Button | Scancode | Android keycode | Notes |
|---|---|---|---|
| Up / Down / Left / Right | — | `KEY_UP` / `KEY_DOWN` / `KEY_LEFT` / `KEY_RIGHT` | D-pad |
| OK | — | `KEY_ENTER` | |
| Back | 158 | 4 `BACK` | |
| Home | 102 | 3 `HOME` | |
| List (three lines) | 139 | 82 `MENU` | |
| Input (arrow into box) | 471 | 178 `TV_INPUT` | the repo launcher's `input_key_keycodes` can take this |
| Vol + / Vol − / Mute | — | `VOLUME_UP` / `VOLUME_DOWN` / `MUTE` | |

## TCL private keys (the TCL key converter turns the IR scancode into a 40xx keycode; `isTCLKey=true`)

Handled by `com.tcl.keycustomfunctionservice` (`CustomFunction.interceptKey`), which intercepts them before normal dispatch. A launcher or accessibility key service cannot remap them.

| Button | Scancode | TCL keycode | What it does today |
|---|---|---|---|
| "T" circle | 456 | 4020 | starts `com.tcl.partnercustomizer/.tkey.TKeyActivity` (action `com.tcl.partnercustomizer.tkey`) |
| Keyboard icon with colored dots | 563 | 4056 | does nothing on the user's TV (confirmed by the user); no handler to protect |
| Settings (gear) | 561 | 4077 | opens a settings **overlay** (user confirmed), not an activity, so the handler package is unidentified; test it after every debloat batch |
| NETFLIX | 758 | 4062 | Netflix starts itself (`START com.netflix.ninja/.MainActivity` from Netflix's own uid 10063). Works with any launcher. |
| App grid (dotted squares) | 588 | 4082 | `START com.google.android.tvlauncher/.appsview.AppsViewActivity` from uid 1000: it hard-codes the **stock launcher's** apps view |
| Mic (Google Assistant) | 555 | 4048 | goes to the Assistant path (katniss). Arrives over IR like the rest; the audio itself is a separate channel, tested in T13. |

## Consequences for later tasks

- **Keep `com.tcl.keycustomfunctionservice` enabled.** It carries the whole remote's TCL buttons. The repo's UnionTV example keeps it too.
- **RESOLVED in T6: stock launcher disabled, App-grid button is dead** (alternatives tested on the real remote and rejected, see CLAUDE.md section 6). Original note: **App grid button depends on the stock launcher** (`com.google.android.tvlauncher`). If the stock launcher is disabled in T6, the button does nothing. Options for T6: leave the stock launcher enabled but not Home (the repo tests this case), or accept a dead App grid button.
- **T button depends on `com.tcl.partnercustomizer`.** The repo's example list disables it (TCL promo rows) and notes the T key was not tested. Decide in T4: keep it, or lose the T button.
- **Netflix, Input, Back, Home, List, D-pad, volume, mute** are safe: standard keys or the app handles itself.
- **Mic**: the key works; what matters is whether audio can be captured (T13).
- **Keyboard-dots (4056)**: dead on this TV already; nothing to protect.
- **Gear (4077)**: the settings overlay must survive the debloat. Test it after each batch in T5; if it breaks, the last batch's packages are the suspects. `dumpsys window | grep mCurrentFocus` while the overlay is open identifies its owner.
