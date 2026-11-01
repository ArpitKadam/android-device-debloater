# RESTORE: undoing everything

```
.\restore-tv.ps1 -DryRun      print the plan, change nothing
.\restore-tv.ps1              put the TV back, then reboot once and check Home
```

Pass `-TvHost <ip>` (or set `TV_HOST`), or give the TV's MAC with `-TvMac aa-bb-cc-dd-ee-ff` (or set `TV_MAC`) and the
script finds it through ARP.

## What it does

1. For every batch in `work/batches/` that has a recovery record, newest first, replays the state `tools/disable.ps1`
   recorded: the packages it disabled, the Home choice and the animation scales (through `tools/restore.ps1` and
   `tools/recovery.py`). Records live in `~/.cleanyourtv/` outside the repo and are keyed by the TV's address; the
   script adopts a record made under another address.
2. Reinstalls the apps that were uninstalled, from `backup/apks/`, if they are missing.
3. Sets Private DNS back to what `backup/settings_global.txt` says (or deletes the two settings).
4. Stops the CPU keep-alive loop and sets the governors back to `ondemand` (`work/cpu-fast.ps1 off`).
5. Clears the accessibility service list (the launcher's helper) and disables accessibility, as backed up.

The stock launcher is re-enabled by step 1 (it is in the batches). Reboot afterwards and confirm Home.

## What it does not undo

- Apps you installed yourself (Spotify, Crunchyroll, SmartTube, the add-ons, the launcher fork). Uninstall them with
  `pm uninstall` if you want them gone.
- The phone's accepted adb key on the TV. Revoke it in the TV's Developer options (Revoke USB debugging authorisations)
  and delete `~/.android/adbkey` in Termux.
- Network debugging itself. Turn it off in Developer options if you no longer need adb.
- AOT-compiled app code. To drop it: `cmd package compile --reset <package>`.
- `cpu-hold.sh` stays in `/data/local/tmp/` on the TV (the step stops the loop and removes its pid file). Delete it with `adb shell rm /data/local/tmp/cpu-hold.sh` if you like.

## When something goes wrong

- Home is blank or wrong after restore: `adb shell cmd package set-home-activity --user 0 <stock home component>`,
  or enable the stock launcher package with `pm enable --user 0`. Never force a home override without a reason.
- A package will not come back: `pm enable --user 0 <package>` one by one from `backup/packages_disabled.txt`'s
  complement; compare with `backup/packages_all.txt`.
- The Play Store crashes with `NoSuchElementException ... TvMainActivity`: re-enable
  `com.google.android.partnersetup` and run `pm clear com.android.vending`.
- Last resort: factory reset from the TV's settings. Everything above is then moot.

## Backups to keep

`backup/` is the only copy of the pre-change state and of the removed apps' APKs. Keep it until you are sure.
