# Designkonzept "Naturfoto"

## Leitidee
Dunkles, "Outdoor-Nacht"-Theme: tiefe Grün-/Anthrazit-Töne mit moosgrünen und bernsteinfarbenen Akzenten. Fotos der Nutzer stehen im Vordergrund, die UI tritt zurück.

## Farbpalette
- Hintergrund: `#101613` (ForestNight), Surface: `#18211B` (ForestDeep)
- Primär: `#7FB069` (Leaf), Sekundär/Akzent: `#E8B44B` (Amber)
- Text: `#EDE6D6` (Sand), dezente Texte: `#9AA79B` (Fog)

## Transparente Menüs (Kartenansicht und global)
- Kartenansicht: OSM-Karte füllt den gesamten Screen; darüberliegende Overlays (Titel-Card oben, FABs rechts) haben halbtransparente Oberflächen (`ForestDeep` mit Alpha 0.72) und Rounded-Corner-Shapes.
- Oben und unten dezente Verlaufs-Scrims (`ScrimTop`/`ScrimBottom`), damit Text und Icons auf beliebigen Kartenbereichen lesbar bleiben, ohne harte Kanten.
- Gleiche transluzenten Cards auch für Chat-Header, Kalender-Popups und Profil-Header — konsistentes System statt einzelner Ausnahmen.

## Navigation
Bottom-Bar mit 5 Tabs: Timeline, Areas, Chat, Kalender, Karte. Die Karte hat keine App-Bar, damit die Kartenfläche maximal ist.

## Umgesetzte Verbesserungsvorschläge
- Rollen: `ADMIN` / `AREA_MODERATOR` / `MEMBER` (statt nur Admin/Member)
- Spot-Sichtbarkeit: `PROJECT` / `AREA_ONLY` / `PRIVATE` (Schutz sensibler Brutplätze)
- EXIF-Datenmodell für geteilte Fotos (Kamera, Objektiv, Blende, Zeit, ISO, Brennweite)
- Umfragen projektweit oder pro Area (`SurveyScope`)
- Profil: optionale öffentliche Homepage/Socials, Skill-Tags, Portfolio-Feld

## Technik
- Kotlin Multiplatform + Compose Multiplatform (Android zuerst, iOS/Windows später aus derselben Codebasis)
- Karte: osmdroid + OpenStreetMap (kein API-Key, DSGVO-freundlich); Projektgebiet als Polygon prüfbar
- Backend: Supabase (Free Tier, Region Frankfurt) — Auth, Postgres + Row Level Security, Realtime, Storage
- eBird: client-seitig, persönlicher API-Key je Nutzer, opt-in im Profil, Key verlässt das Gerät nicht
