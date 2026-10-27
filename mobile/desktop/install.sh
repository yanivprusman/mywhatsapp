#!/usr/bin/env bash
# Build the Linux desktop app and install it for this user: an app image (bundled JRE)
# under ~/.local/share/mywhatsapp, a launcher in the GNOME app grid, and its icon.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
data="${XDG_DATA_HOME:-$HOME/.local/share}"

(cd "$here/.." && ./gradlew :desktop:createDistributable --console=plain -q)

dest="$data/mywhatsapp"
rm -rf "$dest.new"
cp -a "$here/build/compose/binaries/main/app/MyWhatsApp" "$dest.new"
rm -rf "$dest"; mv "$dest.new" "$dest"

install -Dm644 "$here/src/jvmMain/resources/mywhatsapp.svg" "$data/icons/hicolor/scalable/apps/mywhatsapp.svg"
install -Dm644 /dev/stdin "$data/applications/mywhatsapp.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=MyWhatsApp
Comment=Your allowlisted WhatsApp groups, filtered and merged
Exec=$dest/bin/MyWhatsApp
Icon=mywhatsapp
Terminal=false
Categories=Network;Chat;
StartupWMClass=com-automatelinux-mywhatsapp-MainKt
DESKTOP
update-desktop-database "$data/applications" 2>/dev/null || true
gtk-update-icon-cache -q "$data/icons/hicolor" 2>/dev/null || true
echo "Installed: $dest/bin/MyWhatsApp (launcher: MyWhatsApp)"
