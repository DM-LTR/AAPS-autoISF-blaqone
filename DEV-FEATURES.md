# Features in the dev version

This list only exists in `aaps4-autoisf-dev`. It shows what is in the test version and what is
not yet in the normal version (`aaps4-autoisf`).

How a feature moves:

1. It is made on its own branch (`feature/...` or `fix/...`), starting from the normal version.
2. It is merged into dev and tested here.
3. When it works well, it is merged into the normal and the sport version.

Status: 🧪 in test · ✅ good, ready to move · 📦 in normal/sport · 🔒 stays in dev

| Feature | Branch | Status | What to check |
|---|---|---|---|
| AutoISF chip on the main screen | `feature/autoisf-chip` | 🧪 in test | Chip over the full width under the BG, only with AutoISF active. Closed: symbol and pinned values in one line, right-aligned. Tap opens/closes, long press opens the AutoISF screen. Open: all values in a grid; tap a value to pin/unpin it (at most 4). Pins and open/closed stay after a restart. Grey status line (loop, SMB, TBR) is gone. Check phone, tablet and split layout. |
| Pink BG parabola on the BG graph | `feature/bg-parabola-curve` | 🧪 in test | As in AutoISF 3.2.1: pink line solid over the fitted minutes, dashed 20 min ahead (only with predictions on). One point per minute, new with every reading. Pencil on the BG graph → "BG parabola (AutoISF)". Only with AutoISF. Check that it lies on the readings and moves with each new value. |
| Coloured BG dots every minute | `feature/ukf-minute-dots` | 🧪 in test | With a 1 minute CGM and UKF: a coloured dot every minute (smoothed value), grey rings stay. Check that dots sit where expected, treatment triangles still sit on the curve, and the BG-dots widget looks fine. |
| Hold on the BG graph shows the smoothed value | `fix/inspect-smoothed-value` | 🧪 in test | Zoom in, hold a reading: the dot sits on the coloured point, the pill shows its value in range colour and the raw value in grey brackets, e.g. 147 (raw 151). |
| Parabola comparison raw / smoothed | `feature/parabola-compare` | 🔒 stays in dev | Inside the open AutoISF chip: raw and smoothed fit, the used one marked "(used)". Can be pinned as "parabola raw/smoothed". Also in the APS tab and as a graph. Display only. |
