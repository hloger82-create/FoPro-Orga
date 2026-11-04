# FoPro-Orga

App für Projektgruppen (Fokus: Natur- & Wildlifefotografie, aber projektarten-unabhängig).

- Profile,Areas/Boards mit Foren & Pinnwand, Chat, Timeline, Kalender mit Zu-/Absagen, Umfragen
- Karten-Tool auf OpenStreetMap-Basis mit Projektgebiets-Polygon und geteilten Spots (EXIF, Sichtbarkeit)
- Optional pro Nutzer: eBird-Sichtungen (client-seitig, eigener API-Key)

## Technik
- Kotlin Multiplatform + Compose Multiplatform (Android zuerst, iOS/Windows später aus derselben Codebasis)
- Backend: Supabase (Free Tier, Region Frankfurt) — Auth, Postgres + Row Level Security, Realtime, Storage
- Designkonzept: siehe `docs/DESIGN.md`

## Start
Projekt in Android Studio öffnen, Gradle syncen lassen, `composeApp` auf Gerät/Emulator starten (minSdk 26).
