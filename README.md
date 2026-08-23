# Padlás

A telefon a szoba, amiben élsz. A Drive a padlás fölötte. A kuka meg kuka.

Nem takarítóapp: a 128 GB-os készüléket meleg réteggé, a 2 TB-os Drive-ot hideg réteggé
fokozza le, és úgy mozgat közöttük tartalmat, hogy közben semmi ne vesszen el. A termék-
és UI-specifikáció a [`padlas-spec.md`](padlas-spec.md), a modulhatárok a
[`CONTRACT.md`](CONTRACT.md).

Android 14+ (minSdk 34, target 36), sideload terjesztés, Samsung Galaxy S22 Ultra az
elsődleges eszköz.

## Modulok

```
:app                    Compose UI, navigáció, Vault képernyő, DI belépés
:core:common            dispatcherek, formázás
:core:design            design tokenek, motion, haptika, komponenskönyvtár
:core:database          Room entitások, DAO-k
:core:media             MediaStore adapter, EXIF, hash, thumbnail, törlési műveletek
:feature:index          bejáró + inkrementális szinkron
:feature:map            treemap, cache-túra
:feature:dedup          hash pipeline, csoportosítás, élességpont
:feature:triage         screenshot-triázs, Kidobó, inverz mappamód
:feature:transcode      Media3 Transformer, hőfojtás-kezelés
:feature:archive        Drive REST kliens, resumable feltöltés, verifikáció, cédula
:feature:browse         mappa és hónap szerinti válogatás, kötegelt művelettel
:feature:sort           válogató (swipe deck)
:feature:rules          szabálymotor, dry-run, delta követés
:feature:audit          napló, visszavonás, CSV export
```

## Fordítás

```bash
./gradlew :app:assembleDebug        # telepíthető debug APK
./gradlew testDebugUnitTest         # unit tesztek
./gradlew :app:assembleRelease      # release APK (aláírásról lásd lent)
```

JDK 21 kell (az Android Studio JBR is jó), Android SDK platform 36 + build-tools 36.0.0.
A `local.properties` az SDK útvonalát tartalmazza, és nincs verziókövetve.

## Aláírás

A release build ebben a sorrendben keresi az aláíró anyagot:

1. `keystore.properties` a repó gyökerében (git-ignorált):

   ```properties
   storeFile=/abszolut/ut/padlas.jks
   storePassword=...
   keyAlias=padlas
   keyPassword=...
   ```

2. `PADLAS_KEYSTORE_FILE`, `PADLAS_KEYSTORE_PASSWORD`, `PADLAS_KEY_ALIAS`,
   `PADLAS_KEY_PASSWORD` környezeti változók (ezt használja a CI).

Ha egyik sincs, a release build a debug kulccsal íródik alá, hogy a `assembleRelease`
helyben is fusson.

Kulcs létrehozása:

```bash
keytool -genkeypair -v -keystore padlas.jks -storetype PKCS12 \
  -alias padlas -keyalg RSA -keysize 4096 -validity 10000
```

## CI

`.github/workflows/release.yml`:

- **check**: `testDebugUnitTest` + `:app:lintRelease`, riportok artifactként.
- **release-apk**: aláírt release APK, `apksigner verify` ellenőrzéssel, `padlas-<verzió>.apk`
  néven artifactként; `v*` tag esetén GitHub Release-be is felkerül a SHA-256 mellett.

Repository secretek (Settings → Secrets and variables → Actions):

| secret | tartalom |
|---|---|
| `PADLAS_KEYSTORE_BASE64` | `base64 -w0 padlas.jks` kimenete |
| `PADLAS_KEYSTORE_PASSWORD` | store jelszó |
| `PADLAS_KEY_ALIAS` | kulcs alias |
| `PADLAS_KEY_PASSWORD` | kulcs jelszó |

Ha a secretek nincsenek beállítva, a CI **eldobható kulcsot generál**, hogy az APK akkor is
aláírt legyen — de két ilyen build nem frissíti egymást a készüléken, mert más az aláírás.

Kiadás:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## Válogatás mappa és hónap szerint

A Vault „x kép egy évnél régebbi" sávja, illetve alul a **Válogatás** belépő ide vezet.
A képernyő tetején két sín van: **mappák** és **hónapok**, mindkettő többszörös
kijelöléssel és a rájuk eső mérettel, plusz gyorsszűrők (1 évnél régebbi, fél évnél
régebbi, csak fotó, csak videó). A rács lapozva tölt (Paging 3), tehát 25 000 elemnél is
azonnal nyílik. A kijelölésre három művelet jár:

- **Padlásra** — feltöltési sorba teszi (a helyi példány marad, amíg a Padláson nem
  ellenőrzött a másolat, INV-1),
- **Kukába** — kétfázisú rendszerkuka egyetlen dialógusban (INV-2, INV-3),
- **Védetté teszem** — kiveszi minden automatizmus alól (INV-4).

A „Mind kijelöl" a szűrő teljes találathalmazát jelöli ki, 2 000 elemnél megállva, és ezt
ki is írja — egy koppintás ne jelenthessen 25 000 fájlt.

## Telepítés a telefonra

A build **sideload**, tehát az APK-t a rendszer ismeretlen forrásnak látja. Ha a
telepítés elakad, az szinte mindig nem az aláírás, hanem a készülék védelme:

1. **Samsung Auto Blocker** (One UI 6.1+, alapból bekapcsolva) minden oldalról telepítést
   blokkol: *Beállítások → Biztonság és adatvédelem → Auto Blocker* → kikapcsolás, vagy az
   "Alkalmazások telepítése ismeretlen forrásból" kapcsoló engedélyezése.
2. **Ismeretlen alkalmazások telepítése**: *Beállítások → Alkalmazások → (a fájlkezelő,
   amiből telepítesz) → Ismeretlen alkalmazások telepítése* → engedélyezés.
3. **Play Protect** ("Nem biztonságos alkalmazás blokkolva"): a Play Áruházban
   *Profil → Play Protect → Beállítások* alatt kapcsold ki az ismeretlen appok
   vizsgálatát, vagy a figyelmeztetésnél válaszd a "Mégis telepítem" opciót.
4. Ha korábban telepítve volt egy másik kulccsal aláírt Padlás, előbb távolítsd el:
   ugyanaz a package más aláírással `INSTALL_FAILED_UPDATE_INCOMPATIBLE` hibát ad.

Ellenőrzés géppel, ha bizonytalan a hiba oka:

```bash
adb install -r padlas-1.0.0.apk   # a pontos hibakódot ez írja ki
```

## Google Drive

A Drive REST v3-at használjuk, nem SAF-ot, egyetlen okból: a `files.get?fields=md5Checksum`
szerveroldali MD5-öt ad, és eredetit csak bizonyítottan bitre azonos másolat után törlünk.

Beállítás (kb. 20 perc, egyszer):

1. GCP projekt, Drive API bekapcsolva.
2. OAuth kliens: Android típus, package `hu.padlas`, az aláíró kulcsod SHA-1-ével.
   A helyi release kulcs ujjlenyomatát így kapod meg:

   ```bash
   keytool -list -v -keystore padlas.jks -alias padlas | grep SHA1
   ```
3. Egy Web kliens azonosító is kell az Identity API-hoz; tedd be a
   `drive_web_client_id` string resource-ba (`app/src/main/res/values/strings.xml`)
   vagy adj hozzá egy `app/src/main/res/values/secrets.xml`-t, amit nem commitolsz.
4. A publishing status legyen **Production** — a `drive.file` scope nem sensitive, így nem
   kell Google verification, viszont Testing státuszban pár naponta lejárna a token.

A `drive.file` scope csak azokat a fájlokat látja, amiket ez az app hozott létre.

## Törlési garanciák

A spec 4. fejezete kötelező érvényű, és a kód is így épül:

- **INV-1** a súrlódás az értékosztálytól függ; pótolhatatlan tartalom csak ellenőrzött
  Drive-másolat után törölhető.
- **INV-1b** automatizmus soha nem töröl véglegesen, csak padlásra visz vagy kukába rak.
- **INV-1c** a `SECRET` besorolású screenshot védett, és nem kerül törlési ajánlásba.
- **INV-2** minden törlés kétfázisú (`MediaStore.createTrashRequest`), és az app kiírja,
  hogy a kukában lévő fájl még helyet foglal.
- **INV-3** minden törlés kötegelt: egy rendszerdialógus, nem háromszáz.
- **INV-4** védett halmaz: `DCIM/Camera` utolsó 30 nap, kedvencek, kézzel védett, és a
  háromnál kevesebb elemű mappák.
- **INV-5** minden művelet naplózva, CSV-be exportálható.
- **INV-6** a szabálymotor csak dry-run után futhat.
- **INV-7** globális kill switch a Vault képernyőn.

## Samsung-figyelmeztetés

Ha a Samsung Gallery ↔ OneDrive szinkron aktív, a lokális törlés a felhőből is törölhet.
Az app ezt indításkor detektálja, és blokkoló figyelmeztetést mutat. A Samsung Gallery
saját kukája külön él a MediaStore trashtől; az app mindkettő méretét külön mutatja.
