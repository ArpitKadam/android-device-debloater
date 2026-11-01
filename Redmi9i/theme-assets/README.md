# Theme assets

The launcher and the Files app load their art from `/sdcard/GrandLine/` on the phone. This folder is where you keep the
sources and the prepared files on the PC; `tools/push-assets.sh` copies them across. Nothing is uploaded anywhere.

**Bring your own art.** The theme was made with One Piece fan art the owner supplied. That art is copyrighted and is not
part of this repository (see `.gitignore`). Any image set works: change the names in `theme/Crew.kt` and
`files-app/.../ui/Crew.kt` if you want a different cast. Every file is optional; anything missing falls back to art drawn
in code, so the apps run with an empty folder.

## Layout on the phone and here

```text
theme-assets/                 /sdcard/GrandLine/
  posters/<Name>.jpg            posters/<Name>.jpg     lock screen and drawer panel
  marks/<Name>.png              marks/<Name>.png       faint watermark (launcher settings, Files)
  icons/<app>.png               icons/<app>.png        app icons
  wallpapers/drawer.jpg         wallpapers/drawer.jpg  drawer background
  wallpapers/live/*.mp4         wallpapers/*.mp4       video loops
  wallpapers/*.jpg|png          wallpapers/*.jpg|png   still wallpapers
  wanted-posters/black|transparent/   (not pushed, optional)  big originals for tools/make-posters.py; create it when you have them
```

`<Name>` is the crew member: `Luffy`, `Zoro`, `Nami`, `Usopp`, `Vinsmoke_Sanji`, `Tony_Tony_Chopper`, `Nico_Robin`, `Franky`,
`Brook`, `Jinbe`. The crew member of the day is `members[epochDay mod 10]` in both apps; the lock screen picks randomly but
never the same poster twice in a row.

## Formats

| Folder | Format | Notes |
| --- | --- | --- |
| `posters/` | JPEG, 720 px wide, quality 88 | White line art on near-black. Made from large originals by `tools/make-posters.py` |
| `marks/` | PNG with alpha, 360 px wide | White line art on transparent; the apps tint it gold |
| `icons/` | PNG with alpha, 256 px square | Names: `browser`, `camera`, `files`, `security`, `settings`, `telegram`, `termux`. If your art has a baked-in checkerboard instead of real transparency, run `tools/make-icons.py` |
| `wallpapers/` stills | JPEG or PNG, portrait, at least 720 x 1600 | No text or watermarks; they sit behind icons |
| `wallpapers/` videos | MP4 or WebM, H.264, no audio, 30 fps, at most 720 px wide, about 2 to 5 MB | `tools/encode-wallpaper.sh` makes them. The launcher plays one muted loop at a time |
| `wallpapers/drawer.jpg` | JPEG, portrait | Drawer background |

## Workflow

```bash
python tools/make-posters.py theme-assets          # wanted-posters/* to posters/ and marks/
python tools/make-icons.py raw-icons theme-assets/icons
tools/encode-wallpaper.sh clip.mp4 theme-assets/wallpapers/live/clip.mp4
tools/push-assets.sh
```

Then, on the phone: long-press an empty spot on the home screen to pick a wallpaper (one tap applies it to the current
page); launcher settings can set the drawer wallpaper and per-page wallpapers.

## Current art set (owner's own)

10 posters and 10 marks, 7 icons, 5 video loops (`zoro1`, `zoro2`, `luffy1`, `luffy2`, `onepiece`) and one `drawer.jpg`.
