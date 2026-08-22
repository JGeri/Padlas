# PADLÁS — Termék- és UI-specifikáció

**Platform:** Android 14+ (target 36), Samsung Galaxy S22 Ultra elsődleges eszköz
**Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Media3, Coil 3, ML Kit
**Terjesztés:** sideload, saját használatra (nincs Play Store megfelelőségi kényszer)
**Verzió:** spec v1.0

---

## 1. Mit csinál és mit nem

### 1.1 A tézis

Ez nem takarítóapp. A takarítóapp abból indul ki, hogy szemetet kell kidobni. Nálad nincs elég szemét: a 128 GB-ot valódi, megtartandó tartalom tölti meg, és pont ezért nem működik semmilyen törlésalapú megoldás.

2 TB Drive-val a probléma nem az, hogy *mit dobj ki*, hanem hogy **mi maradjon kéznél**. Az app ezt csinálja: a telefont meleg réteggé, a Drive-ot hideg réteggé fokozza le, és a kettő között mozgat tartalmat úgy, hogy soha semmi ne vesszen el.

> A telefon a szoba, amiben élsz. A Drive a padlás fölötte. A kuka meg kuka.

Ez az app fizikai metaforája, és mindent meghatároz benne: az információs architektúrát, az ikonográfiát, és minden animáció irányát. Padlásra = felfelé. Kidobóba = lefelé.

Fontos, hogy a padlás **nem** a kuka helyettesítője. Ami pótolhatatlan, az felmegy. Ami szemét, az kimegy. A két útvonal külön, és az app dolga eldönteni, melyik asset melyikre való — mert ha ezt rád bízza, megint 25 000 döntés lesz belőle.

### 1.2 Célok

1. A 128 GB-os készülék tartósan 60-70% alatt maradjon manuális karbantartás nélkül.
2. Semmi ne vesszen el véletlenül. A pótolhatatlan tartalom csak ellenőrzött másolat után törölhető; a pótolható egyszerűen kidobható.
3. A döntéshozatal legyen gyors: a Sponge-féle swipe csak akkor kerül elő, ha minden automatizálható lépés már lefutott.

### 1.3 Nem célok

- Nem cél a Play Store-kompatibilitás, így `MANAGE_EXTERNAL_STORAGE` szabadon használható.
- Nem cél a multi-user, multi-device szinkron.
- Nem cél a Google Photos helyettesítése. A Drive itt archívum, nem galéria.
- Nem cél más appok cache-ének programozott ürítése (rendszerszinten tiltott). Csak navigációs asszisztencia.

### 1.4 A swipe-paradoxon

Ha 25 000 asseted van és 1 mp-et töltesz eggyel, az 7 óra. A Sponge azért nem old meg semmit, mert a swipe-ot szépíti ahelyett, hogy csökkentené a számát.

A Padlás fordított sorrendben dolgozik. Minden réteg a következő bemenetét zsugorítja:

| Réteg | Mechanizmus | Tipikus nyereség | Emberi döntés |
|---|---|---|---|
| 0. Index + térkép | mérés | 0 GB | nincs |
| 1. Duplikátumok | hash + dHash | 5-15 GB | 1 döntés / csoport |
| 2. Videótömörítés | HEVC transzkód | 30-50% a videótárból | 1 döntés / köteg |
| 3. Archiválás | Drive offload | korlátlan | 1 döntés / szabály |
| 4. Szemétmappák | inverz kiválasztás | 3-8 GB | 1 döntés / mappa |
| 5. Válogató (swipe) | kézi | maradék | 1 döntés / kép |

A 4. rétegig eljutva a swipe-olandó halmaz jellemzően 25 000-ről 1500-2000-re esik. Ez 25-30 perc, nem 7 óra.

### 1.5 Értékosztályok

Minden asset indexeléskor besorolást kap, és **az osztály dönti el, mennyi súrlódás jár a törléséhez.** Ez váltja ki az egységes „csak archiválás után törölhetsz" szabályt, ami pótolhatatlan tartalomra helyes, screenshotokra viszont abszurd.

| Osztály | Mi tartozik ide | Alapértelmezett sors |
|---|---|---|
| **Pótolhatatlan** | kamerafotó és -videó, kedvencek, kézzel védett | padlásra |
| **Pótolható** | fogadott média (WhatsApp, Telegram), letöltések, mentett képek | felülvizsgálat, jellemzően kidobó |
| **Efemer** | screenshotok, duplikátum-vesztesek, homályos felvételek, appok által generált képek | kidobó |

**Besorolási jelek.** Mind olcsó, mind indexeléskor fut:

- **Kamera-EXIF jelenléte.** `TAG_MAKE` + `TAG_F_NUMBER` vagy `TAG_ISO_SPEED_RATINGS`. Ha nincs, nem te fényképezted. Ez a legerősebb egyetlen jel, és gyakorlatilag ingyen van.
- **Felbontás = kijelzőfelbontás.** 1440×3088 vagy 1080×2316 → screenshot akkor is, ha átnevezted vagy átmozgattad. Mappafüggetlen, ezért megbízhatóbb, mint a path.
- **Mappa.** `DCIM/Camera` vs `Pictures/Screenshots` vs `WhatsApp Images/Sent` (a `Sent` a te tartalmad, a sima nem — ezt a legtöbb takarítóapp elrontja).
- **PNG + nincs EXIF + képernyő-képarány** → generált vagy screenshot.

A besorolás javaslat, nem ítélet: bármelyik asset kézzel átmozgatható másik osztályba, és a döntés perzisztens.

### 1.6 Cédula

Ha kidobsz valamit, nem a fájl megy fel a Drive-ra, hanem a **cédulája**: 256px-es thumbnail, a felismert szöveg, a dátum, az eredeti elérési út és a méret. Assetenként ~20 KB a 2-4 MB helyett.

Ettől a törlés visszafordíthatatlan marad, de nem lesz belőle vakfolt. Fél év múlva rákereshetsz arra, hogy „volt-e screenshotom az adóazonosítómról", és választ kapsz akkor is, ha a fájl már rég nincs meg. A padlásmetaforában ez a katalóguscédula: a tárgyat kidobtad, a cédula megmaradt.

10 000 kidobott screenshot cédulája ~200 MB. A fájlok maguk 20-30 GB lettek volna.

---

## 2. Architektúra

### 2.1 Modulok

```
:app                    Compose UI, navigáció, DI belépés
:core:design            design tokenek, motion, komponenskönyvtár
:core:database          Room entitások, DAO-k, migrációk
:core:media             MediaStore adapter, EXIF, thumbnail
:feature:index          bejáró + inkrementális szinkron
:feature:dedup          hash pipeline, csoportosítás, élességpont
:feature:transcode      Media3 Transformer wrapper, verifikáció
:feature:archive        Drive kliens, feltöltés, ellenőrzés
:feature:sort           válogató (swipe deck)
:feature:rules          szabálymotor, dry-run
:feature:audit          naplózás, visszavonás, export
```

DI: Hilt. Aszinkron: Coroutines + Flow végig, `Dispatchers.IO` a fájlműveletekre, dedikált `Dispatchers.Default.limitedParallelism(4)` a hashelésre.

### 2.2 Adatmodell (Room)

```kotlin
@Entity(indices = [
    Index("quickHash"), Index("sha256"), Index("dHash"),
    Index("bucketId"), Index("dateTaken"), Index("state")
])
data class Asset(
    @PrimaryKey val mediaStoreId: Long,
    val uri: String,
    val relativePath: String,     // "DCIM/Camera/"
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateTaken: Long,          // epoch ms, EXIF-elsőbbséggel
    val dateModified: Long,
    val width: Int, val height: Int,
    val durationMs: Long?,        // videó
    val bucketId: Long,           // MediaStore mappaazonosító
    val latitude: Double?, val longitude: Double?,

    val quickHash: String?,       // size + első 64KB + utolsó 64KB
    val sha256: String?,          // csak ütközésjelöltekre
    val dHash: Long?,             // 64 bites perceptuális
    val blurScore: Float?,        // Laplace-variancia
    val ocrText: String?,         // screenshotokra

    val valueClass: ValueClass,   // besorolás (1.5)
    val classifiedBy: String,     // "exif" | "resolution" | "path" | "manual"
    val shotKind: ShotKind?,      // screenshot-triázs eredménye (M7)

    val state: AssetState,        // LOCAL, ARCHIVED, GHOST, TRASHED, PROTECTED
    val driveFileId: String?,
    val driveMd5: String?,
    val archivedAt: Long?,
    val cardId: String?,          // cédula a Drive-on, ha kidobtuk (1.6)
    val protectedManually: Boolean = false
)

enum class AssetState { LOCAL, UPLOADING, ARCHIVED, GHOST, TRASHED, PROTECTED }
enum class ValueClass { IRREPLACEABLE, REPLACEABLE, EPHEMERAL }
enum class ShotKind { RECEIPT, TICKET, SECRET, PLACE, CHAT, OTHER }
```

`GHOST` = a Drive-on megvan, lokálisan már csak a thumbnail. Ez az app végállapota a legtöbb régi tartalomra.

További táblák:

- `DupGroup(id, kind, representativeAssetId, memberCount, reclaimableBytes)`
- `TranscodeJob(assetId, originalBytes, resultBytes, state, attempts, failureReason)`
- `UploadJob(assetId, driveFileId, uploadUri, bytesSent, state, attempts)` — resumable session URI perzisztálva
- `Rule(id, name, predicateJson, action, enabled, lastRunAt)`
- `AuditEntry(id, timestamp, action, assetId, sizeBytes, reversible, payloadJson)`

### 2.3 Indexelés

Első futás: teljes `MediaStore.Files` kurzor `EXTERNAL_CONTENT_URI`-n, projekcióval (soha `SELECT *`). Kötegelt insert 500-asával.

Inkrementális: `DATE_MODIFIED > lastSyncTimestamp` kurzor + `ContentObserver` a `MediaStore` URI-jára. Nem `MediaStore.getGeneration()`-re támaszkodunk, mert Samsungon nem mindig megbízható; `dateModified` + méret összehasonlítás a fallback.

**Teljesítménycél:** 25 000 asset hidegindexelés < 90 mp, közben az UI folyamatosan frissül (nem blokkoló splash).

### 2.4 Hash-pipeline

Naiv megközelítésben 25 000 fájl teljes SHA-256-ja I/O-korlátos és percekig tart. Kétfázisú:

1. **quickHash** minden asseten: `size | első 64KB | utolsó 64KB` → SHA-256. Gyors, mert a lemez nem olvas végig semmit.
2. **teljes SHA-256** csak azokra, ahol a quickHash ütközik. Ez a fájlok < 2%-a.

Perceptuális hash (dHash): a bitmapet 9×8 szürkeárnyalatosra dekódolod (`BitmapFactory.Options.inSampleSize` + `inPreferredConfig = ALPHA_8`), vízszintesen összehasonlítod a szomszédos pixeleket, 64 bit lesz belőle. Keresés: 16 bites prefix szerinti bucketolás, majd Hamming-távolság a bucketen belül. Küszöb: **≤ 6** azonos jelenet, **7-12** hasonló kompozíció (külön csoporttípus).

Élességpont: Laplace-kernel varianciája a 256×256-ra skálázott szürkeárnyalatos bitmapen. Csoporton belül relatív, nem abszolút — a legmagasabb pontszámú nyer.

---

## 3. Drive-integráció

### 3.1 A döntés: REST API, nem SAF

Az előző beszélgetésben a SAF (`OpenDocumentTree`) volt a nulla-setup opció. **2 TB-tal és törlésalapú munkafolyamattal ez már nem elég**, egyetlen okból: a `DocumentsContract` nem ad hash-t. Ha ellenőrzés nélkül törölsz eredetit, előbb-utóbb elveszítesz valamit.

A Drive REST v3 `files.get?fields=md5Checksum` visszaadja a szerveroldali MD5-öt. Ez a különbség a "valószínűleg feltöltődött" és a "bizonyítottan bitre azonos" között.

**Setup költsége:** GCP projekt + Android OAuth kliens (package name + debug SHA-1) ≈ 20 perc, egyszer. A `drive.file` scope **nem sensitive**, tehát nincs Google verification, nincs CASA review, nincs többhetes átfutás.

Auth: `Identity.getAuthorizationClient()` a `play-services-auth`-ból (a régi `GoogleSignIn` deprecated). Bejelentkezés `androidx.credentials` + Google ID. A `drive.file` scope csak azokat a fájlokat látja, amiket az app hozott létre — ez neked pont jó, nem fér hozzá a többi Drive-tartalmadhoz.

*Fallback, ha ragaszkodsz a nulla-setuphoz:* SAF + determinisztikus fájlnév (`<sha256[0..15]>_<eredeti>.jpg`) + visszaolvasásos ellenőrzés (letöltöd és lokálisan hasheled). Bitre pontos, de minden feltöltés dupla sávszélesség. Nem javasolt 2 TB nagyságrendben.

### 3.2 Mappastruktúra a Drive-on

```
/Padlás/
  /2024/
    /2024-07/
      IMG_20240712_143022.jpg
      .padlas-manifest.json
  /_video/
  /_dup-losers/          ← duplikátum-vesztesek, 90 nap után véglegesen törölhetők
```

A `.padlas-manifest.json` mappánként tartalmazza az assetek metaadatait (eredeti path, EXIF, hash). Ez a katasztrófa-visszaállítás alapja: ha a telefon meghal, a Drive önmagában elég a teljes rekonstrukcióhoz.

### 3.3 Feltöltési protokoll

Resumable upload minden fájlra (`uploadType=resumable`), mert mobilhálózaton a nem-resumable feltöltés megbízhatatlan:

1. `POST /upload/drive/v3/files?uploadType=resumable` → session URI. **Perzisztáld** az `UploadJob`-ba.
2. Chunkolt `PUT` 8 MB-os darabokban, `Content-Range` fejléccel.
3. Megszakadás: `PUT` üres törzzsel + `Content-Range: bytes */<total>` → a szerver megmondja, hol tart.
4. Befejezés után `files.get?fields=id,md5Checksum,size`.
5. **Verifikáció:** szerver MD5 == lokálisan számolt MD5. Csak ekkor íródik `ARCHIVED` állapot.
6. A lokális törlés ettől független, külön kötegelt lépés (lásd 4. fejezet).

Ütemezés: `WorkManager`, `NetworkType.UNMETERED`, `requiresCharging = true`, `requiresBatteryNotLow = true`. Foreground service `dataSync` típussal.

**Buktató:** Android 15+ alatt a `dataSync` foreground service napi 6 órás összesített korlát alatt van. 2 TB-nyi kezdeti feltöltésnél ez heteket jelenthet. Emiatt: az első bulk feltöltést érdemes asztali gépről elvégezni (Drive desktop kliens a leszedett fotókkal), az app pedig utána már csak a napi delta-t kezeli. Ezt a spec kifejezetten javasolja onboarding lépésként.

---

## 4. Törlési garanciák

Ez a legfontosabb fejezet. Egy app, ami képeket töröl, egyetlen hibából elveszíti a bizalmat, és a bizalom itt visszafordíthatatlan.

**INV-1.** A törléshez szükséges súrlódás az értékosztálytól függ, nem egységes:

| Osztály | Feltétel |
|---|---|
| Pótolhatatlan | `state == ARCHIVED` **és** `driveMd5` egyezik a lokálisan számolt MD5-tel. Kivétel: duplikátum-vesztes, ahol a győztes lokálisan megmarad. |
| Pótolható | kétfázisú kuka + kötegelt megerősítés, feltöltés nélkül |
| Efemer | kétfázisú kuka + tartásos megerősítés (2 mp), cédula a Drive-ra |

**INV-1b.** Automatizmus (szabálymotor, ütemezett feladat) **soha** nem törölhet véglegesen, semmilyen osztályban. Automatizmus kizárólag padlásra vihet vagy kukába rakhat. A végleges törlés mindig kézi, mindig kötegelt, mindig darabszámmal.

**INV-1c.** A `SECRET` besorolású screenshot (helyreállítási kód, mentőkulcs, jelszó) automatikusan védett, és soha nem kerül törlési ajánlásba. Ez a szabály felülír minden mást, beleértve a kézi kötegkijelölést is: külön ki kell venni a védelem alól.

**INV-2.** Minden törlés kétfázisú. Első fázis: `MediaStore.createTrashRequest()`. A rendszerkuka 30 nap után üríti magát. Az app **külön, láthatóan** jelzi, hogy a kukában lévő tartalom még helyet foglal — ez a Sponge-típusú appok tipikus hazugsága.

**INV-3.** Minden törlés kötegelt. `MediaStore.createDeleteRequest()` egyetlen rendszerdialógusba fogja az összeset. Fájlonkénti törlés = 300 dialógus = használhatatlan.

**INV-4.** Védett halmaz, ami soha nem kerül automatikus ajánlásba:
- `DCIM/Camera` utolsó 30 nap
- `is_favorite = 1` a MediaStore-ban
- manuálisan védett (long-press)
- bármi, ami 3-nál kevesebb assetet tartalmazó mappában van

**INV-5.** Minden művelet naplózva az `AuditEntry` táblában, CSV-be exportálható. A napló a Drive-ra is felmegy.

**INV-6.** A szabálymotor kizárólag dry-run után futhat. A dry-run mutatja a pontos darabszámot, a felszabaduló bájtokat, és 20 véletlen mintát.

**INV-7.** Globális kill switch a Vault képernyőn. Minden háttérmunka azonnal leáll.

### 4.1 Samsung-specifikus figyelmeztetés

Ha a Samsung Gallery ↔ OneDrive szinkron aktív, a lokális törlés **a felhőből is törölhet**. Az app első indításkor detektálja (a `com.microsoft.skydrive` jelenléte + a Samsung Gallery sync provider), és ha aktív, blokkoló figyelmeztetést mutat. A Samsung Gallery saját kukája külön él a MediaStore trashtől — mindkettőt ki kell üríteni, az app mindkettő méretét mutatja.

---

## 5. Funkcionális modulok

### M1 — Térkép (diagnosztika)

Treemap az egész `/storage/emulated/0`-ról, drill-down navigációval, plusz `StorageStatsManager` (`PACKAGE_USAGE_STATS` engedéllyel) az app / adat / cache bontáshoz.

Ez fut le legelőször, mert simán kiderülhet, hogy nem a fotók a probléma, hanem 18 GB Telegram cache.

Fontos: az `/Android/media/` **nem** korlátozott (az `/Android/data/` igen). A WhatsApp és Telegram médiakönyvtára itt van, közvetlenül olvasható és törölhető.

Cache-túra: appok cache-ét programból nem lehet üríteni (`CLEAR_APP_CACHE` signature-level). Helyette sorbaállított deep-link `ACTION_APPLICATION_DETAILS_SETTINGS`-be, méret szerint csökkenő sorrendben, "kész / kihagy" léptetéssel. Opcionális: Shizuku-integráció (`pm trim-caches`), ha vállalod az ADB-setupot.

### M2 — Duplikátumok

Három csoporttípus, külön UI-jal:

- **Azonos** (SHA-256 egyezés): nincs mit eldönteni, csak megerősítés kell.
- **Sorozat** (dHash ≤ 6, `dateTaken` 10 mp-en belül): burst-fotók. Automatikus győztes az élességpont alapján.
- **Hasonló** (dHash 7-12): kézi döntés, de csoportonként egy.

### M3 — Videó

A 4K/60 klip ~440 MB/perc. A tárhelyed nagy része ez.

Transzkódolás `androidx.media3:media3-transformer`-rel (nem nyers MediaCodec), H.264 → HEVC, hardveres enkóder. 40-60% méretcsökkenés alig látható romlással.

Kötelező részletek:
- EXIF és `DATE_TAKEN` átmentése, különben szétesik a galéria időrendje
- rotációs metaadat megőrzése
- az eredeti csak a kimenet sikeres próbadekódolása után törölhető
- csak töltőn + `requiresBatteryNotLow`

**S22 Ultra-specifikus:** az európai változatban Exynos 2200 van, ami tömeges transzkódolásnál hőfojtásba megy. A pipeline mérje a `ThermalManager` státuszát, és `THERMAL_STATUS_MODERATE` felett szüneteljen. Ez nem opcionális, különben egy éjszakai batch fél óra után lelassul a töredékére.

### M4 — Archiválás

A központi modul. Szabály- vagy kézi vezérléssel felviszi az assetet a Drive-ra, ellenőrzi, majd `GHOST` állapotba teszi: a teljes felbontású fájl eltűnik, a 512px-es thumbnail lokálisan marad az app cache-ében.

A galériában a ghost asset továbbra is látszik (az app saját böngészőjében), koppintásra streamel a Drive-ról. Vizuálisan megkülönböztetve — lásd 6.6.

### M5 — Válogató (swipe deck)

Ez jön utoljára, a maradék 6-8%-ra. Négyirányú:

- **balra** → kuka
- **jobbra** → megtart
- **fel** → padlásra (archiválás)
- **le** → később döntök

Long-press = véglegesen védett. Csoportosítás esemény szerint (>4 óra szünet = új esemény) vagy EXIF GPS-klaszter szerint ("47 kép · Balaton · 2023 július").

### M6 — Szemétmappák (inverz mód)

`Pictures/WhatsApp Images`, `Telegram`, `Download`, `Screenshots`: itt 90%+ a törlendő. A swipe rossz interakció, mert 9 balra húzás jut 1 jobbra.

Inverz UI: rács, alapból minden **kijelölve törlésre**, te azt kopogtatod ki, amit megtartasz. Egy mappa 30 mp alatt végigmegy.

### M7 — Screenshot-triázs

Az efemer halmaz zöme screenshot, és a valódi kérdés nem az, hogy törölhetők-e, hanem hogy **melyik az az 5%, ami nem.** Ezt kell megtalálni, hogy a maradék 95% egyetlen mozdulattal mehessen.

ML Kit on-device szövegfelismerés + vonalkódolvasás minden screenshoton, ebből `ShotKind`:

| Kategória | Jel | Javaslat |
|---|---|---|
| `SECRET` | „recovery code", „backup code", „mentőkulcs", kulcsszerű blokkok, seed phrase-minta | **védett**, soha nem ajánlott törlésre |
| `RECEIPT` | IBAN-minta, „számlaszám", „nyugta", összeg + dátum együtt | megtart vagy padlásra |
| `TICKET` | QR vagy Aztec kód + dátum a szövegben | lejárt dátum → kidobó, jövőbeli → megtart |
| `PLACE` | utcanév-minta, irányítószám, „útvonal" | megtart |
| `CHAT` | buborékos elrendezés, sok rövid sor, időbélyeg-minta | felülvizsgálat |
| `OTHER` | kevés szöveg, nagy képarány | kidobó |

Az OCR-szöveg emellett FTS4-be is bekerül, tehát a megmaradó screenshotok kereshetők — és ami kikerül, arról a cédula (1.6) marad meg ugyanezzel a szöveggel.

A triázs sorrendje: `SECRET` mindent felülír, utána `TICKET`, `RECEIPT`, `PLACE`, `CHAT`, végül `OTHER` mint alapértelmezés.

### M9 — Kidobó

Nem a válogató része, külön képernyő. Az efemer halmaz `ShotKind` szerint csoportosítva, minden csoport alapból **teljes egészében kijelölve törlésre**. Végigscrollozol, kikopogtatod, amit megtartasz, majd egyetlen kötegelt művelettel törlöd az egészet.

Egy 4 000 elemű screenshot-halmaz így 2-3 perc, nem 70. A swipe itt rossz interakció, mert 19 balra húzás jut 1 jobbra.

Alatta a **Kuka**, három külön sorral és külön mérettel:

- MediaStore trash (30 nap, `createTrashRequest` ide rak)
- Samsung Gallery saját kukája (külön él, nem ugyanaz)
- függő köteg (kijelölve, még nem véglegesítve)

Mindhárom mellett a foglalt méret, mert **a kukába rakott fájl még helyet foglal**, és pont ez az a hazugság, amit a legtöbb takarítóapp elkövet.

### M8 — Szabályok

Predikátum + művelet, dry-run kötelező:

```
HA relativePath ~ "WhatsApp" ÉS dateTaken < now-90d
AKKOR padlásra
→ dry-run: 3 412 fájl · 6,4 GB
```

Heti storage-delta követés: "a Telegram +4,2 GB-t nőtt". Ez a retention hook — enélkül a takarítóapp definíció szerint egyszer használatos.

---

## 6. UI / UX terv

### 6.1 Design plan

**Tárgy.** Egy archívum. Nem felhő, nem varázspálca, nem söprögetés. Az app világa a fizikai raktározásé: mérőműszerek, dobozok, katalóguscédulák, egy padlásfeljáró.

**A kockázat, amit vállalunk.** A "prémium mobilapp" alapértelmezése ma a kékesfekete háttér + egy neonzöld vagy vörös akcentus. Ehelyett a Padlás **meleg archívumsötét**: barnába hajló feketék, mintha egy este megvilágított irattár vagy egy bőr fényképezőgéptáska belseje lenne. Az akcentus nem neon, hanem **sárgaréz** — és a másodlagos akcentus ugyanannak a fémnek az oxidált állapota, **patinazöld**. Az egész paletta egyetlen fém öregedési skálája. Semmi nem véletlen szín.

**Aláírás.** A Tárolómérő: egy szegmentált ív a Vault képernyőn, ami mérőműszerként viselkedik, és amikor helyet szabadítasz fel, fizikailag **visszafolyik**, miközben a fölötte lévő szám lefelé pörög. Ez az egyetlen igazán látványos elem az appban. Minden más köré csendes.

### 6.2 Szín

```kotlin
object Ink {
    val Void       = Color(0xFF0D0B09)  // háttér, meleg feketebarna
    val Surface    = Color(0xFF16130F)  // kártya
    val Raised     = Color(0xFF1F1B16)  // emelt felület, sheet
    val Hairline    = Color(0x14FFFFFF)  // 8% fehér, 1px vonalak
    val Highlight  = Color(0x0FFFFFFF)  // 6% fehér, felső élfény

    val Paper      = Color(0xFFF2EDE4)  // elsődleges szöveg, meleg törtfehér
    val Faded      = Color(0xFF9A9186)  // másodlagos
    val Dust       = Color(0xFF645C52)  // harmadlagos, feliratok

    val Brass      = Color(0xFFC9974A)  // akcentus: felszabadított hely
    val BrassDim   = Color(0xFF8A6A36)
    val Patina     = Color(0xFF5C8A7E)  // akcentus: archivált / biztonságban
    val Rust       = Color(0xFFB4553F)  // destruktív, KIZÁRÓLAG megerősítésen
}
```

Kategóriaskála a mérőhöz és a treemaphez — sárgaréztől patináig, egyetlen hue-íven:

```kotlin
val Spectrum = listOf(
    Color(0xFFD8A85C),  // fotók
    Color(0xFFB98B4A),  // videók
    Color(0xFF95793F),  // appok
    Color(0xFF6E6B45),  // rendszer
    Color(0xFF52785F),  // egyéb
    Color(0xFF5C8A7E)   // archivált (padlás)
)
```

Két nem alkuképes részlet:

1. **Szemcse.** 3% opacitású, csempézett zajtextúra az egész háttéren. AMOLED-en enélkül a sötét gradiensek sávosodnak, és a felület "olcsó digitális"-nak látszik. A szemcse az, amitől anyaga lesz.
2. **Nincs Material-árnyék.** Helyette kétrétegű saját árnyék (szoros sötét + tág lágy) és **1px felső élfény** `Highlight` színnel a kártyák tetején. Sötét felületen ez a fényvisszaverődés adja a mélységet, nem a blur.

### 6.3 Tipográfia

Három szerep, három betűtípus, mindegyik indokolt:

| Szerep | Betű | Miért |
|---|---|---|
| Hero szám | **Instrument Serif** | A Vault egyetlen nagy száma. Talpas szám egy tárhelyappban váratlan, és a pénzügyi/óraipari világ vizuális nyelvét idézi. Ez a luxusérzet forrása. |
| UI | **Instrument Sans** | Geometrikus, enyhén karakteres, nem az Inter, amit minden AI-generált app használ. |
| Adat | **Geist Mono** | Tabuláris számjegyek. Minden bájtméret, darabszám, százalék ezzel. |

```kotlin
Display   Instrument Serif  56sp / 60  regular
Title     Instrument Sans   24sp / 30  medium
Body      Instrument Sans   15sp / 22  regular
Label     Instrument Sans   13sp / 18  medium
Micro     Instrument Sans   11sp / 14  medium, +2% letterspacing, uppercase
Numeric   Geist Mono        variábilis, tabular figures MINDIG
```

**Pörgő számjegyek technikai feltétele:** a digit-slot szélességét a legszélesebb számjegy méréséből fixálod (`TextMeasurer`), és `AnimatedContent`-tel cseréled slotonként. A Geist Mono-nál ez ingyen van. Az Instrument Serifnél kézzel kell fixálni a slotot — enélkül a hero szám remeg pörgés közben, és az azonnal olcsóvá teszi az egészet.

### 6.4 Forma és tér

Rács: 4dp alap, használt lépcsők 8 / 12 / 16 / 24 / 40 / 64.

Lekerekítés: 4 (chip), 12 (kártya), 20 (sheet), 28 (hero).

**Squircle.** A Compose `RoundedCornerShape`-je körívet használ, ami iOS-hez szokott szem mellett "megtörik". Saját `Shape` szuperellipszis-úttal (n ≈ 4,2) minden 12dp fölötti sugárnál. Apró részlet, de ez az egyik dolog, amitől drága lesz.

### 6.5 Mozgás

Ez az a fejezet, amitől az app vagy jó lesz, vagy AI-slop.

**Alapelv 1: rugók, nem időzítők.** Minden átmenet `spring()`, nem `tween()`. Az `Easing.FastOutSlowIn` a rendszer alapértelmezése, és pontosan úgy is néz ki. A rugó azért működik, mert a tömeg érzetét adja.

**Alapelv 2: nyugalomban semmi nem mozog.** Nincs lélegző gradiens, nincs végtelen shimmer, nincs pulzáló CTA, nincs ambient parallax. Ha nem érsz hozzá és nem fut háttérmunka, az app teljesen áll. Ez a legerősebb luxusjel, és pont ez hiányzik minden generált UI-ból.

**Alapelv 3: a mozgás információt hordoz.** A jóváhagyás és az elutasítás fizikailag különbözik. Az archiválás felfelé megy, a törlés lefelé. Egy elem soha nem "átúszik" egy másikba, hanem odamegy.

```kotlin
object Motion {
    // damping, stiffness
    val Snap     = spring<Float>(1.00f, 1400f)  // azonnali visszajelzés, nincs túllengés
    val Settle   = spring<Float>(0.85f,  380f)  // alap UI átmenet, épp hogy érezhető túllengés
    val Weight   = spring<Float>(0.72f,  220f)  // nagy elemek: kártya, sheet, lap
    val Snapback = spring<Float>(0.55f,  600f)  // visszapattanás: undo, elutasítás
    val Drain    = spring<Float>(0.90f,   90f)  // lassú, magabiztos: a mérő ürülése

    const val StaggerMs = 28      // testvérelemek között
    const val StaggerCap = 12     // 12 elem után egyben jön a többi
    const val MaxBlockingMs = 600 // ennél tovább semmi nem várakoztat
}
```

**Koreográfia.** Belépéskor az elemek nem egyszerre jelennek meg. 28 ms lépcsőzés, felülről lefelé, egyenként `Settle` rugóval, opacitás 0→1 és Y-eltolás +12dp→0. A 12. elem után a maradék egyben. Ez a különbség a "megjelent egy lista" és a "leült egy lista" között.

**Haptika.** Az S22 Ultra LRA-ja jó, kár nem kihasználni. Kulcs: a haptika a **mozgás csúcsához** kötődik, nem az érintéshez.

```kotlin
VibrationEffect.startComposition()
    .addPrimitive(PRIMITIVE_QUICK_RISE, 0.4f)   // kártya elszakad a decktől
    .addPrimitive(PRIMITIVE_THUD, 0.7f, 120)    // becsapódás a kukába
    .compose()
```

| Esemény | Primitív |
|---|---|
| Küszöb átlépése húzás közben | `PRIMITIVE_TICK` 0.3 |
| Kuka-becsapódás | `QUICK_RISE` 0.4 → `THUD` 0.7 |
| Padlásra emelés | `QUICK_RISE` 0.5 → `TICK` 0.2 (halkabb, "elment") |
| Undo | `SPIN` 0.4 |
| Csoportösszecsukás | `TICK` ×3, 40 ms-onként, csökkenő |
| Kötegvégi véglegesítés | `LOW_TICK` 0.3 → `THUD` 1.0 |

**Reduced motion.** `Settings.Global.ANIMATOR_DURATION_SCALE == 0` esetén minden rugó `snap()`-re vált, a stagger 0, a haptika marad. Az app működőképes marad, csak nem mozog.

### 6.6 A hét aláíró animáció

**① A Mérő ürülése** — *Vault képernyő*

Nem progress bar. 280dp átmérőjű, 270°-os ív, kategóriánként szegmentálva, `Spectrum` színekkel, a szegmensek között 2dp rés.

- *Betöltés:* a szegmensek egyenként nőnek ki nullából, kívülről befelé, 28 ms lépcsőzéssel, `Settle` rugóval. Az egész 400 ms alatt lezajlik.
- *Felszabadítás:* a felszabadult rész **visszafolyik** az óramutatóval ellentétesen, `Drain` rugóval (lassú, kis túllengéssel), miközben a mögötte lévő szegmensek utánacsúsznak. Az ív végén finom sárgaréz izzás jelzi, hol nyertél helyet — de csak a mozgás idejére, utána eltűnik.
- Fölötte az Instrument Serif szám számjegyenként pörög lefelé, jobbról balra terjedve, 40 ms lépcsőzéssel.
- A haptika a mozgás **felénél** üt, nem az elején.

Ez az app egyetlen látványos pillanata. Nem szabad máshol megismételni.

**② A Válogatás** — *Swipe deck*

- Húzás közben a kártya forgásának pivotja **a kártya alatt** van (2× magasság mélyen), mint egy alul rögzített fotó. A forgás lineáris a vízszintes eltolással, max ±8°.
- A mögötte lévő kártya `0.94 → 1.0` skálán nő, ahogy húzol. A deck fizikai.
- Küszöb (40% szélesség) átlépésekor `TICK` haptika és a célikon (kuka / pipa / padlás) `Snap` rugóval megnő 1.0→1.15-re.
- **Commit balra:** a kártya nem repül ki a képernyőről. **Beleesik** az alul lévő kukatálcába: skála 1.0→0.35, Y +180dp, közben a tálca fedele először *lefelé billen* (anticipáció, 60 ms), majd becsukódik. `THUD`.
- **Commit fel (padlás):** a kártya felfelé megy, közben **kifakul és drótvázzá válik** (szaturáció 1→0, egy 1px `Patina` körvonal rajzolódik rá), és a képernyő tetején lévő padlásnyílásba tűnik el. Fizikailag más, mint a törlés.
- **Undo:** a kártya *visszajön* a tálcából, `Snapback` rugóval. Nem újra megjelenik, hanem visszamászik.

**③ Az Összecsukás** — *Duplikátumcsoport*

8 majdnem azonos fotó. Belépéskor **kézben tartott lapokként legyezőznek szét** (egyenként ±6° forgás, 20dp eltolás, `Weight` rugó, 28 ms lépcső).

A győztes (legélesebb) 8dp-vel megemelkedik, árnyéka mélyül, körülötte 1px `Brass` keret. A vesztesek **belezuhannak** — nem elhalványulnak, hanem fizikailag összecsúsznak a győztes pozíciójába, egymás után 40 ms-onként, `Weight` rugóval, mindegyiknél egy halkuló `TICK`. A végén a győztes megrázkódik (`Snapback`, 3% skála), és megjelenik alatta a felszabadított méret, pörgő számjegyekkel.

**④ Az Emelés** — *Archiválás rácsnézetből*

A csempe leválik a rácsról: árnyék nő alatta (0→24dp blur), skála 1.0→1.06, egyszerre. Aztán felfelé gyorsul ki a képernyőből, `Weight` rugóval.

A helyén **nem lyuk marad**, hanem a szellemcsempe: 1px `Patina` körvonal, 8% opacitású thumbnail, sarkában egy apró padlás-glif. A rács nem rendezi újra magát — ez fontos, mert a rács átrendeződése elveszíti a térbeli memóriádat.

A Drive-számláló ugyanabban a pillanatban nő, a Mérő ugyanabban a pillanatban ürül. Egy művelet, három szinkron visszajelzés.

**⑤ A Tömörítés** — *Videó transzkód*

A videókártya magassága a fájlmérettel arányos. Amikor a transzkód végez, a kártya **fizikailag elvékonyodik** (magasság a tömörítési arány szerint, `Drain` rugóval, 500 ms alatt), és közben:

- az eredeti méret áthúzódik (a húzás balról jobbra rajzolódik, 200 ms)
- az új méret felülről begördül, pörgő Geist Mono számjegyekkel
- a különbség `Brass` színnel felvillan, majd 800 ms alatt `Faded`-re halványul

A tömörítés így nem absztrakció, hanem látható fizikai zsugorodás.

**⑥ A Tartás** — *Kötegelt végleges törlés*

Ez az egyetlen animáció, aminek **nem** az a dolga, hogy jó érzés legyen. 4 000 fájl végleges törlése nem koppintás, hanem tartás.

Lenyomva tartod a gombot: egy `Brass` ív rajzolódik körbe 2 mp alatt, lineárisan (itt nincs rugó, mert a rugó bizonytalanná tenné, hol tartasz). Közben `LOW_TICK` haptika gyorsuló ütemben, 400 ms-tól 80 ms-ig. A gomb felirata közben a darabszámot mutatja, nem igét.

Korai elengedés: az ív `Snapback` rugóval visszatekeredik, egyetlen halk `TICK`. Nem történt semmi, és ez látszik is.

Teljesítés: egy `THUD` 1.0, a rács elemei **nem egyszerre** esnek le, hanem ±40 ms véletlen szórással, mint a leomló homok. Utána azonnal vissza a Vaultra, ahol a Mérő ürül — a látványos rész ott van, nem itt. Két ütem, nem egy.

**⑦ A Térkép** — *Treemap drill-down*

Betöltés: a téglalapok középről nőnek ki, terület szerint csökkenő sorrendben, 28 ms lépcsővel, `Settle` rugóval.

Drill-down: **nincs átúsztatás.** A megkoppintott téglalap `Weight` rugóval kitágul a teljes képernyőre, és a gyerekei *belőle* osztódnak ki (a szülő területét felosztva, egyenként `Settle`). Vissza: pontosan a fordítottja. Shared element transition, `SharedTransitionLayout`.

A breadcrumb nem szöveg, hanem egy vízszintes sáv, ahol minden szint egy szegmens, szélessége az adott szint arányával — így a navigációs sáv maga is adatvizualizáció.

### 6.7 Képernyők

```
┌─────────────────────────────┐
│  PADLÁS            ⚙        │   ← Micro, uppercase, letterspaced
│                             │
│         ╭───────╮           │
│      ╱             ╲        │   ← ① A Mérő (280dp, 270° ív)
│     │    82,4 GB    │       │      Instrument Serif 56sp
│     │   szabad      │       │      Micro alatta
│      ╲             ╱        │
│         ╰───────╯           │
│    ▓▓▓ fotó   ▓▓▓ videó     │   ← jelmagyarázat, 11sp
│                             │
│  ┌───────────────────────┐  │
│  │ 2 341 duplikátum      │  │   ← akciósávok, sorban
│  │ 12,4 GB visszanyerhető│  │      minden sáv egy modul
│  └───────────────────────┘  │      belépés 28ms lépcsővel
│  ┌───────────────────────┐  │
│  │ 89 nagy videó         │  │
│  │ ~34 GB tömöríthető    │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ 4 102 kép > 1 év      │  │
│  │ padlásra vihető       │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ 3 877 screenshot   ↓  │  │   ← lefelé nyíl: kidobó, nem padlás
│  │ 8,1 GB kidobható      │  │
│  └───────────────────────┘  │
│                             │
│  ⌫ Kuka · 2,3 GB            │   ← mindig látszik, ha nem nulla
│                             │
│  Térkép   Padlás   Napló    │
└─────────────────────────────┘
```

Az akciósávok sorrendje **dinamikus**: mindig a legnagyobb nyereségű felül. Ez nem menü, hanem prioritási sor. A sávok jobb szélén egy apró glif mutatja az irányt: ↑ padlásra, ↓ kidobóba, ⇄ tömörítés. Ugyanaz a két irány, ami a swipe-ban és minden animációban.

A Kuka sora akkor is látszik, ha semmi más nem sürgős, mert amíg ott van 2,3 GB, addig az a hely nincs meg. Ez az egyetlen elem az appban, ami akkor is figyelmet kér, ha nem csinálsz semmit — és pontosan azért, mert a kuka a rendszer leggyakoribb hazugsága.

Nincs bottom navigation bar öt ikonnal. Három szöveges belépő alul, `Label` méretben. A luxus itt a kevés elem.

Többi képernyő: Beolvasás (első futás), Térkép, Duplikátumok, Válogató, **Kidobó**, Videók, Padlás (archívumböngésző), Szabályok, Napló.

### 6.8 Szöveg

Aktív igék, mondat eleji nagybetű, semmi töltelék. Egy művelet ugyanazt a nevet viseli végig: ami a gombon "Padlásra", az a toastban "Padláson".

- Üres állapot nem hangulat, hanem felszólítás: *"Nincs duplikátum. A beolvasás 3 napja futott. Újra?"*
- A hiba nem kér elnézést: *"A feltöltés megszakadt 1,2 GB-nál. Wi-Fi nélkül nem folytatódik."*
- Számok mindig egységgel és tagolva: `12,4 GB`, `2 341 fájl`.
- Destruktív megerősítés kimondja a visszafordíthatatlanságot: *"412 fájl végleges törlése a kukából. Nem vonható vissza."*

### 6.9 Amit nem csinálunk

Konfetti. Pulzáló vagy lélegző elemek nyugalmi állapotban. Végtelen shimmer skeleton. Ripple mindenre. Glassmorphism blur mindenhol. Szivárványgradiens. Emoji ikonográfia. Bounce minden átmeneten (a rugó nem attól jó, hogy ugrál). Parallax görgetéskor ok nélkül. Fokozatmérő, ami 99%-on megáll. Ünneplő animáció rutinműveletre — az ünneplés a Mérő ürülése, és semmi más.

---

## 7. Teljesítménybüdzsé

120 Hz LTPO → **8,33 ms frame budget**. Amivel ez elromlik és a megoldás:

| Kockázat | Megoldás |
|---|---|
| Teljes bitmap dekódolás rácshoz | `MediaStore.loadThumbnail()`, Coil 3, 5 elem prefetch |
| Nagy lista recompose | Paging 3 + stabil `key` + `@Immutable` UI modellek |
| Hash a fő szálon | dedikált `limitedParallelism(4)` dispatcher |
| Treemap újraszámolás | squarified layout memoizálva, csak `size` változásra |
| Mérő újrarajzolás | `Canvas` + `drawWithCache`, a szegmensértékek `derivedStateOf` mögött |

Mért célok: hidegindítás → első pixel < 800 ms. Index 25k asset < 90 mp. Swipe deck jank-mentes 120 Hz-en 200 kártyán át. Memória-csúcs < 320 MB.

---

## 8. Engedélyek

| Engedély | Miért |
|---|---|
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` | index |
| `MANAGE_EXTERNAL_STORAGE` | treemap, `/Android/media/` bejárás (sideload miatt vállalható) |
| `PACKAGE_USAGE_STATS` | `StorageStatsManager`, app/cache bontás |
| `POST_NOTIFICATIONS` | háttérmunka státusz |
| `FOREGROUND_SERVICE_DATA_SYNC` | feltöltés, transzkód |
| Internet | Drive |

---

## 9. Mérföldkövek

| # | Tartalom | Kimenet |
|---|---|---|
| **M0** | Room séma, MediaStore indexelő, design token réteg | mérhető, de nem cselekvő app |
| **M1** | Térkép + Mérő (① animáció) | tudod, mire megy a 128 GB |
| **M2** | Hash pipeline + duplikátumok (③ animáció) | első valódi 5-15 GB |
| **M3** | Besorolás + screenshot-triázs + Kidobó (⑥) | 8-12 GB, **Drive nélkül** |
| **M4** | Drive auth + feltöltés + verifikáció + `GHOST` (④) + cédula | a 2 TB használhatóvá válik |
| **M5** | Videó transzkód (⑤) | a legnagyobb egyszeri nyereség |
| **M6** | Válogató (②) + inverz mappamód | a maradék |
| **M7** | Szabálymotor + delta követés | önfenntartó állapot |

A sorrend nem tetszőleges: minden lépés a következő bemenetét csökkenti. Az M3 szándékosan előzi meg a Drive-integrációt — az efemer halmazt ki lehet dobni anélkül, hogy egyetlen bájtot is feltöltenél, és így az M4-ben már csak a valóban megőrzendő tartalom megy fel. Fordított sorrendben 20-30 GB szemetet szinkronizálnál a felhőbe, hogy aztán onnan is töröld.

A swipe azért van a végén, mert addigra alig marad dolga.

---

## 10. Kockázatok

**A 6 órás `dataSync` korlát Android 15+ alatt.** A kezdeti bulk feltöltés hetekig tarthat. Mitigáció: az onboarding kifejezetten asztali gépes első feltöltést javasol, az app utána deltát kezel.

**Exynos 2200 hőfojtás.** Tömeges transzkódolás lelassul. Mitigáció: `ThermalManager`-alapú szüneteltetés, éjszakai futás, `THERMAL_STATUS_MODERATE` küszöb.

**Samsung Gallery ↔ OneDrive.** Lokális törlés felhős törlést okozhat. Mitigáció: detektálás + blokkoló figyelmeztetés első induláskor.

**A `GHOST` állapot elszakadása a valóságtól.** Ha a Drive-on kívülről törölsz egy archivált fájlt, a lokális index hazudni fog. Mitigáció: heti `files.list` reconciliation a `Padlás` mappára, eltérés esetén riasztás a Naplóban.

**Az OAuth refresh token lejárata.** Ha a GCP projekt "Testing" publishing statusban marad, a token pár naponta lejár. Mitigáció: Production statusra kapcsolás — `drive.file` scope-pal ez **nem** igényel Google verification-t.

**A legnagyobb kockázat: az app működik, de nem használod.** A takarítóapp természeténél fogva egyszer használatos. Az M6 (delta követés + szabályok) nem "nice to have", hanem az egyetlen ok, amiért két hónap múlva is megnyitod.
