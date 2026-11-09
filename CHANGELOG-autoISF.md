# Changelog: AAPS 4.0.0 + AutoISF 3.2.1

Testversion. Nur mit virtueller Pumpe oder im Open Loop testen.
⚠ = diese Änderung kann die Dosierung beeinflussen.

---

## 2026-11-10 (8) – Parabel-Vergleich als Diagramm

- **Neues Diagramm „Parabola acceleration“:** zwei Linien über die Zeit, die Beschleunigung, die
  AutoISF benutzt hat (**acc raw**, grün), und dieselbe Parabel auf den UKF-geglätteten Werten
  (**acc smooth**, pink, dicker). Gleiche Achse, 0 in der Mitte (0 = BZ wird weder schneller noch
  langsamer).
  - Einschalten: „+ Diagramm hinzufügen“ → **acc raw** und **acc smooth** wählen.
  - Die Werte werden ab dieser Version bei jedem Loop-Lauf gespeichert; ältere Läufe haben keine.
  - Nur Anzeige, AutoISF rechnet unverändert mit den Rohwerten.

## 2026-11-09 (7) – Parabel-Vergleich roh / geglättet (nur Anzeige)

- **Vergleich des Parabel-Fits:** Bei 1-Minuten-Sensoren wird die AutoISF-Parabel zusätzlich mit den
  UKF-geglätteten 1-Minuten-Werten gerechnet. Auf der Hauptseite (unter der Loop-Statuszeile) und im
  APS-Tab stehen beide Ergebnisse nebeneinander: Beschleunigung, r² und Dauer.
  - Grün = r² mindestens 0,9, also der Wert, ab dem AutoISF die Parabel für acce_ISF nutzt.
  - **AutoISF rechnet weiter nur mit den Rohwerten**, wie im Original. Der geglättete Fit ist nur zum
    Vergleichen. Ein Test stellt sicher, dass der angezeigte Roh-Fit genau dem von AutoISF entspricht.
  - Sichtbar nur, wenn AutoISF aktiv ist und die Glättung **UKF** gewählt ist (Konfiguration → Glättung).

## 2026-11-09 (6) – iobTH im IOB-Diagramm

- **iobTH im IOB-Diagramm:** Im IOB-Diagramm kann iobTH als zweite Linie dazu. Beide liegen auf
  derselben Achse in IE, gleich skaliert, und die Basal-Leiste oben bleibt (vorher verschwand sie).
  Einschalten: Stift-Symbol am IOB-Diagramm → iobTH hinzufügen. Nur Anzeige.

## 2026-11-09 (5) – Bolus-Rechner mit neuestem BZ

- ⚠ **Bolus-Rechner nimmt den neuesten Messwert.** Bisher kam der BZ im Rechner aus dem
  5-Minuten-Raster und konnte bei 1-Minuten-Sensoren bis zu 4 Minuten alt sein. Jetzt ist es derselbe
  Wert wie oben in der Anzeige und im Loop. Die Rechnung selbst ist unverändert.

## 2026-11-09 (4) – Neuester BZ oben, Statuszeile Loop / SMB / TBR

- **BZ-Anzeige zeigt den neuesten Messwert.** Bisher kam der Wert oben aus dem festen 5-Minuten-Raster
  und konnte bei 1-Minuten-Sensoren bis zu 4 Minuten alt sein („vor 228 s“), obwohl schon ein neuer
  Wert da war. Jetzt zeigen BZ, Trendpfeil und Widgets denselben Wert, mit dem auch der Loop rechnet.
- **Neue Statuszeile** unter dem BZ: „Loop vor 35 s · SMB 0,30 IE vor 4 min · TBR 1,20 IE/h vor 3 min“
  (oder „Keine TBR“). Zählt jede Sekunde mit. Ist der letzte Loop-Lauf älter als 15 Minuten, wird
  „Loop“ rot. Nur auf dem Master-Handy, nur Anzeige.
- AutoISF selbst ist unverändert wie im Original 3.2.1.

## 2026-11-09 (3) – Loop bei jedem Wert, BZ-Alter in Sekunden

- ⚠ **Loop interval 1 = Loop bei jedem neuen Wert.** Bisher ließ der Loop einen Lauf aus, solange das
  SMB-Intervall noch lief. Jetzt nur noch, wenn das SMB-Intervall vor dem nächsten regulären Lauf
  endet (sonst käme der SMB unnötig spät). Bei 1 Minute läuft der Loop damit bei jedem Wert.
  Das SMB-Intervall gilt unverändert: Ein SMB kommt nie öfter als dort eingestellt.
- **BZ-Alter in Sekunden:** Oben steht jetzt „vor 47 s“ statt „vor 1 min“, jede Sekunde aktualisiert.
  Ab 10 Minuten wieder in Minuten.
- **Diagramm:** fISF hat eine eigene Farbe (weiß im dunklen Design), weil das alte Gold zu ähnlich zu
  bgISF (orange) war.

## 2026-11-09 (2) – Loop für 1-Minuten-Werte

- ⚠ **Frische Werte:** Der Loop rechnet mit dem neuesten Messwert und nicht mehr mit dem
  5-Minuten-Wert davor. (Von JetFoxy übernommen, Upstream-Vorschlag #5148.)
- ⚠ **1-Minuten-Werte im Filter:** Bei Libre 2/3, Sibionics, AiDex und Syai/Ottai bekommt der
  UKF-Filter die echten Minutenwerte. Ein zweiter Durchlauf über dieselben Werte stört das Lernen
  des Filters nicht mehr. (Von JetFoxy übernommen.)
- ⚠ **AutoISF-Beschleunigung mit 1-Minuten-Werten:** Die Parabel für acce_ISF nutzt die echten
  Messwerte, wenn der Sensor schneller als alle 4 Minuten sendet. (Von JetFoxy übernommen.)
- ⚠ **Neue Einstellung „Loop interval“** (1–10 min, Standard 5). Sie steht bei den SMB-Einstellungen
  von OpenAPS SMB und AutoISF und legt fest, wie oft der Loop höchstens läuft.
  - Weniger als 5 min hilft nur mit einem Sensor, der jede Minute sendet.
  - Mehr als 5 min: Der Loop reagiert später, auch bei Unterzucker.
  - Das **SMB-Intervall** (1–10 min) gilt weiter: Ein SMB kommt höchstens so oft, wie dort
    eingestellt ist. Ist das SMB-Intervall noch nicht vorbei, lässt der Loop Zwischenläufe aus.
    Spätestens nach 5 min läuft er aber immer (außer das Loop-Intervall ist größer als 5).
- Mit dem Standardwert 5 verhält sich der Loop wie bisher.

**Welche Werte nutzen die AutoISF-Faktoren jetzt?**

| Faktor | Was er braucht | Woher es kommt |
|---|---|---|
| acce_ISF | Beschleunigung (Parabel) | echte 1-Minuten-Messwerte |
| dura_ISF | wie lange der BZ schon flach und hoch ist | echte 1-Minuten-Messwerte |
| bg_ISF | aktueller BZ | neuester Messwert (früher: der 5-Minuten-Wert davor) |
| pp_ISF | Delta (Anstieg) | ab dem neuesten Messwert (früher: ab dem 5-Minuten-Wert davor) |

- Das Delta bleibt „Anstieg pro 5 Minuten“: neuester Wert minus Wert 5 Minuten vorher. Darauf sind
  die pp_ISF-Gewichte eingestellt. Ein Delta über 1 Minute wäre etwa fünfmal kleiner.
- acce_ISF und dura_ISF nutzen die echten 1-Minuten-Werte nur, wenn AAPS den Sensor erkennt (Libre 2,
  Libre 2 Native, Libre 3, Sibionics, AiDex, Syai/Ottai oder Dexcom) **und** etwa jede Minute ein Wert
  kommt. Sonst rechnen sie wie bisher mit dem 5-Minuten-Raster. bg_ISF und pp_ISF sind in jedem Fall
  frisch.

## 2026-11-09 (1) – Erster Stand

- AutoISF 3.2.1 auf AAPS 4.0.0, Basis: JetFoxys Branch `autoisf-3.2.1`.
- AutoISF lässt sich ohne Engineering Mode einschalten.
- ⚠ Prüfung ungültiger Profilwerte (ISF, KH-Faktor) wie in Upstream: AutoISF bricht dann ab.
- ⚠ Aktivitätserkennung (Activity Monitor und ActivityClassifier) wieder aktiv:
  - Bewegung gibt nie mehr Insulin als Autosens allein.
  - Inaktivität höchstens bis zur Autosens-Obergrenze.
  - Bei temporärem Ziel ist der ActivityClassifier aus.
- Libre-Kalibrierung entfernt (Kalibrieren in Juggluco).
- AutoISF-Werte (acce, bg, pp, dura, final ISF, iobTH) werden im Loop-Ergebnis gespeichert und als
  Diagramm angezeigt. Die Achse hat Rand, damit eine Linie bei 1,0 sichtbar ist.
- Datenbank wie Upstream 4.0.0 (keine eigene Tabelle).
