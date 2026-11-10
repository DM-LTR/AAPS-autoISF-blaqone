# AAPS 4 + AutoISF 3.2.1 (test version)

---

## ⚠️ READ THIS FIRST – SAFETY NOTICE

**This software can control an insulin pump. Wrong insulin doses can cause severe hypoglycemia,
diabetic ketoacidosis, loss of consciousness or death.**

- **This is an experimental test version.** It is NOT an official AndroidAPS release and is NOT
  supported, reviewed or approved by the AndroidAPS team or the AutoISF authors.
- **This is not a medical device.** It is not approved by any health authority (FDA, CE, MDR or
  any other). It is not medical advice.
- **You use it entirely at your own risk.** You alone are responsible for building it, installing
  it, choosing every setting and every insulin dose that results from it.
- **No warranty of any kind.** The software is provided "as is". The authors and contributors
  accept no liability for any harm, injury, damage or loss, direct or indirect, that results from
  using, building or sharing it (see the license, sections 15 and 16).
- **Test first, without risk:** use the virtual pump or **Open Loop** for several days. Only move
  to Closed Loop when every result has made sense to you – and then start with careful limits
  (max IOB, max SMB, AutoISF min/max).
- **Changes marked ⚠️** in the changelog can change how much insulin is given. Read them before
  you update.
- **Always keep a backup plan:** a blood glucose meter, fast carbs, insulin pen and a way to stop
  the loop at any time.
- **Do not use it** if you do not understand how AndroidAPS, OpenAPS/SMB and AutoISF work, or if
  you cannot check and correct what the loop is doing.
- **Talk to your diabetes team** before you change your therapy.

**If you do not accept these terms, do not build, install or use this software.**

---

## What this is

This fork brings **AutoISF 3.2.1** to **AndroidAPS 4.0.0** and adds features for CGM sensors
that send a value every minute (for example Libre 3 through Juggluco).

- Based on AndroidAPS 4.0.0 and JetFoxy's branch `autoisf-3.2.1` – many thanks to JetFoxy!
- Goal: AutoISF stays **as close to the original as possible**.
- All changes in detail: [CHANGELOG-autoISF.md](CHANGELOG-autoISF.md) (German)

## Branches – which one is for me?

| Branch | What it is | For whom |
|---|---|---|
| [`aaps4-autoisf`](https://github.com/blaqone/AAPS-autoISF/tree/aaps4-autoisf) | **Normal version.** AutoISF 3.2.1 as close to the original as possible. | Start here. |
| [`aaps4-autoisf-dev`](https://github.com/blaqone/AAPS-autoISF/tree/aaps4-autoisf-dev) | **Test version.** The normal version plus features that are still being tested (at the moment: the parabola comparison raw / smoothed). | Only for testers, may change at any time. |
| [`aaps4-autoisf-sport`](https://github.com/blaqone/AAPS-autoISF/tree/aaps4-autoisf-sport) | **Sport version** (planned). Today the same as the normal version. Later: exercise mode in scenes and automations. | Not ready yet. |

How new things get in:

1. A new feature or fix is made on its own branch (`feature/...` or `fix/...`), starting from the
   normal version.
2. It is tested in `aaps4-autoisf-dev`.
3. Only when it works well, it goes into `aaps4-autoisf` (and `aaps4-autoisf-sport`).

The dev version is never merged into the normal version as a whole, so the normal version only
gets features that were tested.

## Features

### AutoISF 3.2.1
- acce_ISF, bg_ISF, pp_ISF, dura_ISF, iobTH – as in the original
- can be enabled without engineering mode
- activity detection (activity monitor / classifier) with safety limits: activity never gives more
  insulin than autosens, inactivity never overrules an autosens that says "more sensitive"
- AutoISF values are stored in the loop result, no own database table

### One-minute values and loop interval
- the loop uses the **newest reading**, not the 5-minute grid
- UKF filter with the real one-minute readings
- **loop interval 1–10 minutes** (default 5) – at 1 the loop runs on every new value
- the **SMB interval** stays a separate setting
- BG display, trend arrow, widgets and bolus wizard use the newest value

### Parabola (acce_ISF)
- default as in the original: parabola fit on raw values
- switch: parabola on **UKF-smoothed values** (calmer, but later)
- slider: **shortest parabola** 5–20 minutes (default 10)
- when the switch is on, the APS tab shows which values the parabola uses
- *dev version only:* raw and smoothed fit side by side on the main screen, in the APS tab and as
  a graph

### CGM correction
- **Only slope and offset – no calibration, no extra smoothing**
- `BG = value × slope + offset` for every value that comes in through xDrip (also Juggluco)
- example: the sensor always reads 30 mg/dL too low → offset +30
- set in the xDrip source settings or in the AutoISF settings; it always applies and is also
  stored and uploaded (Nightscout)

### Scenes and automations
- scenes can change **AutoISF on/off, acce weight and iobTH** while they run –
  the old values come back automatically at the end
- automation actions: enable/disable AutoISF, set acce weight, set iobTH
- automation triggers: acce weight, iobTH (compatible with rules from AutoISF 3.2.1)

### Display
- BG age in seconds
- AutoISF chip on the main screen: the AutoISF values of the last loop run, over the whole width
  under the BG; a click opens the AutoISF screen with the calculation
- graphs for the AutoISF factors and iobTH (also in the IOB graph)

Everything that only concerns AutoISF (graph lines, scene section, automations, main screen
lines) is hidden when AutoISF is not the active APS plugin.

## Building

1. Install Android Studio, clone this repository, check out the branch you want
   (normally `aaps4-autoisf`, see [Branches](#branches--which-one-is-for-me))
2. Build `fullDebug` (for testing) or a signed `fullRelease` with your own keystore
3. Copy the APK to the phone and install it

No ready-made APK is provided.

## Before your first test

- export the settings of your current app
- best use a second phone or the virtual pump
- start in **Open Loop**; move to Closed Loop only after several days that made sense
- choose careful limits (max IOB, max SMB, AutoISF min/max)

## Feedback

Bugs and ideas are welcome as an [issue](https://github.com/blaqone/AAPS-autoISF/issues) –
ideally with a screenshot of the APS tab and the time.

## License

Same as AndroidAPS: GNU Affero General Public License v3.0 (AGPL-3.0).
This program comes with ABSOLUTELY NO WARRANTY, to the extent permitted by applicable law.

---

<!-- changelog:start -->
## Changelog (German)

The newest changes. ⚠ = this change can affect insulin dosing.

### 2026-11-10 (17) – AutoISF-Chip zum Aufklappen (nur dev, im Test)

- Der AutoISF-Chip ist jetzt **zugeklappt einzeilig**: AutoISF-Symbol und die angehefteten Werte,
  rechtsbündig. **Tippen** klappt ihn auf und zu, **lang drücken** öffnet den AutoISF-Bildschirm.
- **Aufgeklappt** stehen alle Werte (acce, bg, pp, dura, final, iobTH) in einem Raster.
  **Antippen** eines Wertes heftet ihn an die zugeklappte Zeile an oder löst ihn (höchstens 4,
  Standard: final und iobTH). Auswahl und auf/zu werden gespeichert und kommen mit dem Export mit.
- Farben: Faktor über 1 (stärker, mehr Insulin) orange/gelb, unter 1 (schwächer) grün.
- Nur sichtbar, wenn AutoISF das aktive APS ist. Reine Anzeige, keine Änderung an der Dosierung.

### 2026-11-10 (16) – AutoISF-Chip auf der Hauptseite (nur dev, im Test)

- Die AutoISF-Werte des letzten Loop-Laufs (acce, bg, pp, dura, final, iobTH) stehen jetzt in einem
  **eigenen Chip** über die ganze Breite, direkt unter BZ-Wert und Sensitivity-Chip. Gleiche Farbe
  und Höhe wie der Sensitivity-Chip, Text rechtsbündig.
- **Klick oder langer Klick** auf den Chip öffnet den AutoISF-Bildschirm (APS-Tab) mit der Berechnung.
- Nur sichtbar, wenn AutoISF das aktive APS ist.
- Die **graue Statuszeile** (letzter Loop, letzter SMB, TBR) ist wieder entfernt.
- Reine Anzeige, keine Änderung an der Dosierung.

### 2026-11-10 (15) – README zeigt die neuesten Änderungen

- Unten in der README stehen jetzt die 5 neuesten Changelog-Einträge, darunter der Link auf den
  ganzen Changelog.
- Keine Änderung am Code.

### 2026-11-10 (14) – README: Übersicht der Zweige

- Die README erklärt jetzt die drei Zweige (normal, dev, sport) mit Links und wie neue Features
  hineinkommen.
- Der Parabel-Vergleich roh/geglättet ist in der README als „nur dev“ markiert.
- Keine Änderung am Code.

### 2026-11-10 (13) – Automation-States nach dem Import

- **States werden jetzt immer frisch gelesen.** Nach einem Einstellungs-Import gelten die importierten
  States sofort, ohne App-Neustart. Vorher konnte die nächste Änderung die alten States wieder über
  den Import schreiben.
- Der **States-Bildschirm aktualisiert sich live**, wenn sich ein State ändert (auch durch eine
  Automation oder einen Import).
- Bewusst **kein Import-Filter**: Ein Import stellt den Stand genau so wieder her, wie er exportiert
  wurde.
- In allen drei Zweigen (normal, dev, sport).

All changes: [CHANGELOG-autoISF.md](CHANGELOG-autoISF.md)
<!-- changelog:end -->
