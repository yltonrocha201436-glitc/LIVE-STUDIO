#!/usr/bin/env bash
set -u

SOURCE_URL="${SOURCE_URL:-rtmp://mediamtx:1935/stream?user=nova&pass=CHANGE_THIS_PASSWORD}"

add_output() {
  local destination="${1:-}"
  if [[ -n "$destination" ]]; then
    OUTPUT_ARGS+=(
      -map 0:v:0
      -map 0:a:0
      -c:v copy
      -c:a copy
      -f flv
      "$destination"
    )
  fi
}

while true; do
  OUTPUT_ARGS=()
  add_output "${YOUTUBE_RTMP:-}"
  add_output "${TIKTOK_RTMP:-}"
  add_output "${TWITCH_RTMP:-}"
  add_output "${FACEBOOK_RTMP:-}"
  add_output "${KICK_RTMP:-}"
  add_output "${CUSTOM_RTMP:-}"

  if (( ${#OUTPUT_ARGS[@]} == 0 )); then
    echo "NovaStream relay: configure at least one destination in .env"
    sleep 10
    continue
  fi

  echo "NovaStream relay: waiting for phone publisher at $SOURCE_URL"
  ffmpeg -hide_banner -loglevel warning -i "$SOURCE_URL" "${OUTPUT_ARGS[@]}" || true
  echo "NovaStream relay: input ended or a destination disconnected; retrying in 3 seconds"
  sleep 3
done
