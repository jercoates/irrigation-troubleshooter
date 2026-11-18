# Irrigation Troubleshooter (Android)

Offline decision-tree guide for electrical faults on traditional sprinkler controllers. A thin native shell (Kotlin) around a bundled single-file guide (`app/src/main/assets/index.html`).

- **No permissions, no network.** Everything is local.
- **Single source of truth:** the tree lives in `index.html`. `tools/extract.mjs` generates the search index and meter reference from it.
- Original build spec: [docs/irrigation-app-spec.md](docs/irrigation-app-spec.md). Release steps: [docs/RELEASE.md](docs/RELEASE.md). Store text: [store/listing.md](store/listing.md).

## Develop

Requires JDK 17, Android SDK 36 (`ANDROID_HOME` set), Node 18+.

```
./gradlew testDebugUnitTest lintRelease bundleRelease   # tests + lint + .aab
./gradlew installDebug                                  # on a connected phone
node tools/test-bridge.mjs                              # page <-> native bridge logic
node tools/extract.mjs                                  # regenerate search/help JSON after editing the guide
```

## Where things are

| Path | What |
|---|---|
| `app/src/main/assets/index.html` | The guide (content + engine + native-bridge functions) |
| `app/.../MainActivity.kt` | WebView host, back/resume, gate persistence, share |
| `app/.../Protocol.kt` | Validation of everything crossing the JS boundary |
| `app/.../SearchActivity.kt`, `HelpActivity.kt` | Native search and meter-reference screens |

## License and credit

MIT License, Copyright (c) 2026 jercoates. You are free to use, adapt and share this project; the license requires that the copyright notice stays with any copy, so please keep the credit. See [LICENSE](LICENSE).
