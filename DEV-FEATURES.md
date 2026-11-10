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
| AutoISF chip on the main screen | `feature/autoisf-chip` | 🧪 in test | Chip over the full width under the BG, same color as the sensitivity chip, text right-aligned. Click and long click open the AutoISF screen. Only shown with AutoISF active. The grey status line (loop, SMB, TBR) is gone. Check phone, tablet and split layout. |
| Parabola comparison raw / smoothed | `feature/parabola-compare` | 🔒 stays in dev | Raw and smoothed fit on the main screen, in the APS tab and as a graph. Display only. |
