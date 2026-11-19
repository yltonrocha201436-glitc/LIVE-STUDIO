# NovaStream multi-destination relay

This optional companion server lets the phone publish one RTMP stream while the server forwards the same encoded video/audio to up to six platform ingest URLs. It reduces the phone's upload workload; it does not remove the need for a VPS, bandwidth, or valid stream keys.

## Deploy

1. Use a Linux VPS with Docker Engine and Docker Compose.
2. Copy this folder to the VPS.
3. Copy `.env.example` to `.env`.
4. In `mediamtx.yml`, replace `CHANGE_THIS_PASSWORD` with a long unique password.
5. Put the same password in `.env` under `SOURCE_URL`.
6. Put each platform's full RTMP/RTMPS ingest URL, including its stream key, into the relevant `*_RTMP` variable. Leave unused destinations empty.
7. From this folder, run `docker compose up -d`.

Open TCP port 1935 on the VPS firewall. Do not expose the MediaMTX port without changing the default password. Use a firewall rule that only allows expected publisher IPs where practical, and use a VPN or TLS-terminating setup for untrusted networks.

## NovaStream phone settings

In NovaStream Studio, set the destination to:

`rtmp://YOUR_VPS_PUBLIC_IP:1935/stream?user=nova&pass=YOUR_PASSWORD`

Start the live. The relay then forwards the incoming H.264/AAC stream to every destination configured in `.env`.

## Notes

- This is a server fan-out, not platform account login. The user must obtain the ingest URL/key from each platform.
- TikTok may not provide a custom RTMP key to every account; use the URL/key shown by TikTok Live Center when available.
- Audio/video are copied without re-encoding. Destinations must accept the incoming H.264 video and AAC audio formats.
- The phone sends only one upload stream to the VPS. The VPS needs enough outbound bandwidth for the sum of all active destinations.
- If no destinations are configured, the relay worker waits and does not start FFmpeg.
- Logs: `docker compose logs -f mediamtx restream`.
- Stop: `docker compose down`.
