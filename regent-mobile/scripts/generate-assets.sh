#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android/app/src/main/res"
IOS="$ROOT/ios/App/App/Assets.xcassets"
SOURCE="$ROOT/assets/icon.svg"
FOREGROUND="$ROOT/assets/icon-foreground.svg"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if ! command -v magick >/dev/null 2>&1; then
  echo "需要 ImageMagick 7（magick）生成图标资源。" >&2
  exit 1
fi

magick -background none "$SOURCE" -resize 1024x1024 "$TMP/icon.png"
magick "$TMP/icon.png" -background "#171719" -alpha remove -alpha off "$TMP/icon-opaque.png"

DENSITIES=(mdpi hdpi xhdpi xxhdpi xxxhdpi)
LEGACY_SIZES=(48 72 96 144 192)
ADAPTIVE_SIZES=(108 162 216 324 432)

mkdir -p "$ANDROID/values"
cat > "$ANDROID/values/ic_launcher_background.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#171719</color>
</resources>
EOF

for index in "${!DENSITIES[@]}"; do
  density="${DENSITIES[$index]}"
  size="${LEGACY_SIZES[$index]}"
  directory="$ANDROID/mipmap-$density"
  mkdir -p "$directory"
  magick "$TMP/icon-opaque.png" -resize "${size}x${size}" "$directory/ic_launcher.png"

  circle_mask="$TMP/circle-$size.png"
  magick -size "${size}x${size}" xc:none -fill white \
    -draw "circle $((size / 2)),$((size / 2)) $((size / 2)),1" "$circle_mask"
  magick "$TMP/icon-opaque.png" -resize "${size}x${size}" \
    "$circle_mask" -alpha off -compose CopyOpacity -composite \
    "$directory/ic_launcher_round.png"

  adaptive_size="${ADAPTIVE_SIZES[$index]}"
  magick -background none "$FOREGROUND" -resize "${adaptive_size}x${adaptive_size}" \
    "$directory/ic_launcher_foreground.png"
done

magick -background none "$SOURCE" -resize "1024x1024" \
  -background "#171719" -alpha remove -alpha off \
  "$IOS/AppIcon.appiconset/AppIcon-512@2x.png"

make_splash() {
  local destination="$1"
  local width="$2"
  local height="$3"
  local mark_size=$((width < height ? width : height))
  mark_size=$((mark_size * 3 / 10))
  if ((mark_size > 560)); then mark_size=560; fi
  magick -size "${width}x${height}" xc:"#171719" \
    \( "$TMP/icon.png" -resize "${mark_size}x${mark_size}" \) \
    -gravity center -compose over -composite "$destination"
}

for file in "$IOS/Splash.imageset"/*.png; do
  make_splash "$file" 2732 2732
done

for file in \
  "$ANDROID/drawable/splash.png" \
  "$ANDROID"/drawable-land-*/splash.png \
  "$ANDROID"/drawable-port-*/splash.png; do
  dimensions="$(magick identify -format '%w %h' "$file")"
  read -r width height <<<"$dimensions"
  make_splash "$file" "$width" "$height"
done

echo "Regent 移动端图标与启动图已生成。"
