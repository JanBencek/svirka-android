#!/usr/bin/env bash
# Generates a small fake library (tagged MP3 tones + cover art) for UI screenshots.
set -euo pipefail
out=${1:-music}; mkdir -p "$out"
# artist|album|year|hue|track titles (comma separated)
lib=(
  "Aurora Lane|Night Drive|2023|210|Neon Lights,Midnight Run,Glass City,Slow Fade,Afterglow,Northbound"
  "The Paper Kites|Harbour|2021|25|Low Tide,Lanterns,Saltwater,Driftwood,Paper Boats"
  "Mira Kovač|Ljeto|2024|330|Sunčano,More,Ponoć,Valovi,Zvijezde,Lavanda,Jugo"
  "Static Bloom|Signals|2022|130|Radio Silence,Interference,Frequencies,Pulse,Echo Chamber,Carrier Wave,Static"
  "Juniper|Juniper|2020|45|Wildflower,Paper Planes,Tall Grass,Hometown"
)
n=0
for entry in "${lib[@]}"; do
  IFS='|' read -r artist album year hue titles <<<"$entry"
  dir="$out/$artist/$album"; mkdir -p "$dir"
  convert -size 600x600 "gradient:hsl($hue,70%,45%)-hsl($(( (hue+60) % 360 )),60%,15%)" \
    -gravity center -fill 'rgba(255,255,255,0.85)' -pointsize 46 -annotate +0+0 "$album" "$dir/cover.jpg"
  i=0; IFS=',' read -ra ts <<<"$titles"
  for t in "${ts[@]}"; do
    i=$((i+1)); n=$((n+1)); f=$(printf '%s/%02d - %s.mp3' "$dir" "$i" "$t")
    ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=$((220 + n * 15)):duration=$((20 + n % 7 * 9))" \
      -i "$dir/cover.jpg" -map 0 -map 1 -c:a libmp3lame -b:a 96k -c:v mjpeg -id3v2_version 3 \
      -metadata:s:v title="Album cover" -metadata:s:v comment="Cover (front)" \
      -metadata title="$t" -metadata artist="$artist" -metadata album_artist="$artist" \
      -metadata album="$album" -metadata date="$year" -metadata track="$i" "$f"
  done
done
echo "seeded $n tracks"
