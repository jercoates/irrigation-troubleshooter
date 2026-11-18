# Irrigation Troubleshooter — Android app spec

Build spec for Claude Code. Target: free Google Play release, Android only, no server, no network.

---

## 1. What already exists

`irrigation-troubleshooter.html` — 649 lines, self-contained, no dependencies.

- `NODES` — the decision tree. Each node is either a question or a result.
  - Question node: `{crumb, q, detail, opts:[{l, s, go}]}`
  - Result node: `{end:1, h, help?, b:[...]}`
- `HELP` — reference sections (meter types, dial symbols, how to use a meter), keyed by name; result nodes reference one via `help:"mechanical"`.
- `stack` — array of visited node ids. `render()` draws the current node. Back = `stack.pop()`.

This is the content and the engine. Do not rewrite it in Kotlin. Wrap it.

---

## 2. Architecture

Single-activity Android app. A `WebView` serves the existing HTML from `assets/` through `WebViewAssetLoader`. Native shell provides navigation, search, export, and the app chrome.

**Do not load via `file://`.** Use `WebViewAssetLoader` with the default `https://appassets.androidplatform.net/assets/` path so the page runs on a proper origin.

**Declare no permissions at all.** Specifically no `INTERNET`. This is load-bearing:
- guarantees offline behavior
- makes the Data Safety declaration "no data collected, no data shared"
- removes an entire class of review questions

The WebView settings: JavaScript on, file access off, content access off, no geolocation, no DOM storage needed (state is held natively — see §4).

---

## 3. Project setup

| Setting | Value |
|---|---|
| Language | Kotlin |
| `targetSdk` | 36 (Android 16) — Play requires this for new apps as of 2026-08-31 |
| `compileSdk` | 36 |
| `minSdk` | 26 |
| Application ID | `com.<yourdomain>.irrigationtroubleshooter` — pick it now, it can never change |
| Build output | Android App Bundle (`.aab`) |

Dependencies: `androidx.webkit` (for `WebViewAssetLoader` and `WebViewCompat`), `androidx.appcompat`, `androidx.core`. Nothing else.

Signing: generate an upload keystore and **back it up outside the project directory**. Enroll in Play App Signing. Losing this keystore means losing the ability to update the app.

---

## 4. Native shell behavior

### Back button
System back must walk the tree back one node, not close the app.

Wire `OnBackPressedCallback` → call into the page → if the stack has more than one entry, pop and re-render; if it's at `start`, let the back event pass through and close the app.

This is the single biggest difference between "a web page in a frame" and "an app."

### Resume where you left off
Persist the `stack` array to `SharedPreferences` on every node change. On launch, restore it. A phone call, a notification, or Android killing the process in the background must not lose the position.

Add a "Start over" action in the toolbar that clears the stack to `["start"]`.

### Keep screen on
`FLAG_KEEP_SCREEN_ON` on the window while the app is foreground. You're at a valve box with a meter in one hand. Make it a toggle in settings, default on.

### Acknowledgement gate
The HTML carries a full-screen gate (`#gate`) shown before the guide, stating that it assumes working knowledge of electricity and carrying the liability language.

Shown on first run and acknowledged by tapping the button. Persist the acknowledgement in `SharedPreferences`; do not show it again on later launches. The back button must not skip it before it has been accepted.

Interaction with the resume behavior above: restoring the saved position happens *behind* the gate. The tech acknowledges, then lands back where they were — the gate does not reset their place in the tree.

Also expose it from the overflow menu as "Disclaimer" so it can be re-read mid-session.

### Toolbar
Title plus the current node's `crumb` as a subtitle. Overflow menu: Start over, Meter reference, About.

---

## 5. JS ↔ native bridge

Use `WebViewCompat.addWebMessageListener` (not `addJavascriptInterface` — it is the older, looser API).

Add these to the HTML, all small:

```js
// called by native on system back; returns true if it handled it
function androidBack() {
  if (stack.length > 1) { stack.pop(); render(); return true; }
  return false;
}

// called by native to jump to a node (from search)
function androidGoto(id) {
  if (!NODES[id]) return false;
  stack = [id]; render(); return true;
}

// called by native to restore a saved position
function androidRestore(stackJson) { ... }

// called by native to build the share text
function androidSummary() { ... }  // returns the path taken + final result as plain text
```

And post a message out to native on every `render()` with `{node, crumb, stack, isEnd}` so the shell can update the toolbar and persist state.

---

## 6. Native screens

Two, to carry the shell beyond "frame around a page" (see §8).

### Symptom search
A native search screen over an index generated from `NODES` at build time.

Write a small build-time script that extracts every node id, its `q`/`h`, its `detail`, and all option labels `l`/`s` into `assets/search-index.json`. Do not hand-maintain this — it must regenerate from the HTML so the tree stays the single source of truth.

Search matches against those strings, lists hits with their crumb trail, and tapping one calls `androidGoto(id)`.

This is what someone does when they already know roughly what's wrong and don't want to walk the tree from the top.

### Meter reference
Native screen rendering the `HELP` sections. Extract `HELP` to `assets/help.json` by the same build-time script. Reachable from the overflow menu at any time, not just from a result node that happens to link it.

### Share the result
On an end node, a share action that calls `androidSummary()` and fires an `ACTION_SEND` text intent. Gives a tech a way to text what he found to whoever sent him out. No file, no storage permission, nothing retained.

---

## 7. Play Console requirements

**Listing assets**
- Adaptive icon: foreground + background SVG → export to the density buckets. Something unmistakably irrigation — a solenoid, a meter probe. Must not look like the Backflow Vault gauge; different app, different mark.
- Feature graphic: 1024 × 500
- Phone screenshots: minimum 2, 16:9 or 9:16. Use a question screen and a result screen.
- Short description: 80 characters — draft in the appendix
- Full description: up to 4000 — draft in the appendix

**Declarations**
- Data safety: no data collected, no data shared. (True only if §2's no-INTERNET rule holds.)
- Content rating questionnaire: tools/reference app, no objectionable content
- Ads: **NO.** Do not tick anything suggesting ads, now or ever — it flags the developer account as monetized and publishes the full address on every app on it.
- In-app purchases: none
- App access: no login required
- Target audience: 18+, or 13+ — not a children's app either way

**Privacy policy URL** — required even for an app that collects nothing. Host a static page on GitHub Pages; costs nothing and runs no service. Draft text in the appendix.

**Release track**
New personal developer accounts need 12 testers opted in to a closed test for 14 consecutive days before applying for production access. Each app runs its own 14 days, but the same 12 people can be on both. If Backflow Vault is also going free on this account, start both closed tests together.

---

## 8. Policy risk

WebView wrappers are the classic rejection under Play's minimum-functionality policy. The exposure here is low — the content is bundled locally, not a remote site being reframed — but the native pieces in §6 are what make that argument obvious rather than something a reviewer has to infer. Build them before the first submission, not after a rejection.

Do not add a "visit our website" link or any remote content. That is the pattern the policy targets.

---

## 9. Build order

1. Project skeleton, WebView host, asset loader, HTML renders
2. Back button, resume, keep-screen-on — the app feels like an app
3. Build-time extraction script → `search-index.json` + `help.json`
4. Search screen, meter reference screen, share action
5. Icon, screenshots, listing text, privacy policy page
6. Internal testing track → closed test with 12 → production

Stop after step 2 and use it on a real job before building 3–6. Everything after that is store work, not product work.

---

## 10. Out of scope

- iOS
- Any network call, analytics, crash reporting, or ads
- Any server component
- Login, accounts, sync
- Content edits to the tree itself (separate task, done in the HTML)

---

## Appendix — store listing draft

The audience statement goes near the top, not the bottom. It does its job before the install, not after.

**Short description (77 chars):**

> Step-by-step electrical troubleshooting for sprinkler systems. Works offline.

**Full description, opening:**

> Walks you through the common electrical faults on a traditional sprinkler controller one question at a time: a zone that won't start, a valve that pulses, a valve that won't shut off. Each step tells you what to measure and what the reading means, with reference sections on using a meter if you need them.
>
> **You don't need to be an irrigation technician, but you do need to be safe around electricity.** The guide assumes you understand basic electrical safety and can take a reading without putting yourself at risk. If you're not confident doing that on a controller connected to power, this isn't the right tool.
>
> Works entirely offline. No account, no network access, no data collected.

Then features, then this at the bottom:

> Provided as-is, with no warranty of any kind. The developer is not liable for any damage, loss, or injury arising from use of this app or reliance on its content. You are responsible for verifying any result against your own equipment, manufacturer documentation, and applicable code.

---

## Appendix — privacy policy draft

Host as a static page. Replace the bracketed parts.

> **Privacy Policy — Irrigation Electrical Troubleshooting**
>
> Last updated: [date]
>
> This app does not collect, transmit, or share any personal information.
>
> The app has no internet access. It does not request the network permission and cannot send data anywhere. All content is stored on the device as part of the app.
>
> The app saves your position in the troubleshooting guide on your own device so it can resume where you left off. This is stored locally and is removed when the app is uninstalled. It is never transmitted.
>
> The app does not use analytics, advertising, or third-party services of any kind.
>
> If you share a troubleshooting result using your device's share feature, that text goes wherever you choose to send it. The app is not involved beyond handing the text to the app you pick.
>
> Questions: [contact email]
