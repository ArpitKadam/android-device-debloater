#!/usr/bin/env bash
# One-time permissions for the Grand Line launcher. Re-run after `pm clear` or an uninstall/reinstall,
# which reset them. Safe to run repeatedly.
#
#   tools/grant-launcher-permissions.sh
#
# What each does:
#   RUN_COMMAND          lets the launcher ask Termux to start its background services after boot
#                        (also needs `allow-external-apps = true` in ~/.termux/termux.properties inside Termux)
#   WRITE_SECURE_SETTINGS lets the launcher re-enable its own accessibility service (the poster lock screen and
#                        the accessibility-button menu), which Android switches off after a force-stop
set -u
ADB="${ADB:-adb}"
export MSYS_NO_PATHCONV=1
PKG=dev.redmi9i.grandline

"$ADB" shell pm grant "$PKG" com.termux.permission.RUN_COMMAND || echo "RUN_COMMAND not granted (is Termux installed?)"
"$ADB" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS || echo "WRITE_SECURE_SETTINGS not granted"

# Turn the accessibility service on now. Do NOT delete and re-put the setting while the app is running: the system can be
# left "binding" the service forever (dumpsys accessibility shows Bound services:{}). A force-stop disables the service and
# the launcher re-enables it, with the stock accessibility menu removed, as soon as it starts (that needs the grant above).
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null
sleep 6
echo "enabled: $("$ADB" shell settings get secure enabled_accessibility_services)"
"$ADB" shell dumpsys accessibility | grep "Bound services"
