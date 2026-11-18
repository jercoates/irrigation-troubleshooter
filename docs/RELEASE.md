# Release guide

## 0. Decide before the first upload (permanent)

- **Application ID** is `io.github.jercoates.irrigationtroubleshooter` (`app/build.gradle.kts`, `namespace` and `applicationId`).
  It can **never** change after the first Play upload. If you own a domain, or want a different name, change both now.
- Contact email: set in docs/privacy.html (done).

## 1. Create the upload key (once)

```
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Back the `.jks` file and its passwords up **outside** this folder (password manager + a second copy). `.gitignore` already excludes `*.jks` and `keystore.properties`.
Enroll in **Play App Signing** when creating the app: Google holds the real signing key; this one is only your upload key, and can be reset by Google support if lost.

Create `keystore.properties` in the repo root (never commit):

```
storeFile=../path/to/upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

## 2. Build

```
./gradlew testDebugUnitTest lintRelease bundleRelease
node tools/test-bridge.mjs
node tools/extract.mjs --check
```

Output: `app/build/outputs/bundle/release/app-release.aab` (signed when `keystore.properties` exists).

**Editing the guide:** edit `app/src/main/assets/index.html` only, then run `node tools/extract.mjs` to regenerate `search-index.json` and `help.json` (`node tools/extract.mjs --check` fails if they are stale or the tree has a broken link). Bump `versionCode` for every upload.

## 3. Privacy policy page (free)

1. Push this repo to GitHub.
2. Settings → Pages → Deploy from branch → `main` / `/docs`.
3. URL: `https://jercoates.github.io/irrigation-troubleshooter/privacy.html`. Paste it in Play Console → App content → Privacy policy.

## 4. Play Console

1. Create app → Free, App (not game). Fill in App content using `store/listing.md`.
2. Upload the `.aab` to **Internal testing** first and install it from the Play link on your own phone.
3. New personal developer accounts must run a **closed test with at least 12 testers opted in for 14 consecutive days** before applying for production. Start this early.
4. Apply for production access, then roll out to Production.

## 5. Pre-submission checklist

- [ ] Installed the Play-delivered build on a real phone and walked the tree end-to-end
- [ ] Gate shows on first run, not again after acceptance; Back does not skip it
- [ ] Kill the app mid-tree, reopen: position is restored
- [ ] Search → tap a hit → lands on the right node, Back walks up the path
- [ ] Share on a result screen produces readable text
- [x] 4 real screenshots ready in store/screenshots
- [ ] Data safety = no data collected; Ads = No; target audience 18+
- [ ] Privacy policy URL loads and has your real contact email
