// Headless test of the page's native-bridge functions (no browser needed).
//   node tools/test-bridge.mjs
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = fs.readFileSync(path.join(root, "app/src/main/assets/index.html"), "utf8");
const script = html.match(/<script>([\s\S]*)<\/script>/)[1];

function el() {
  return { hidden: false, scrollTop: 0, innerHTML: "", textContent: "", style: {},
    addEventListener(t, f) { this.handlers = { ...this.handlers, [t]: f }; }, setAttribute() {}, removeAttribute() {}, querySelectorAll() { return []; }, querySelector() { return null; },
    parentNode: { removeChild() {} } };
}
const els = { gate: el(), gatebtn: el(), app: el() };
const posted = [];
const sandbox = {
  document: { getElementById: (id) => els[id] ?? el(), querySelectorAll: () => [] },
  scrollTo() {},
  androidBridge: { postMessage: (m) => posted.push(JSON.parse(m)) },
  DOMParser: class { parseFromString(s) { return { body: { textContent: String(s).replace(/<[^>]*>/g, "") } }; } },
};
sandbox.window = sandbox; // browser globals live on window
vm.createContext(sandbox);
vm.runInContext(script, sandbox);
const run = (code) => vm.runInContext(code, sandbox);

// loads, announces itself, and posts NO state until native restores it (would clobber saved position)
assert.deepEqual(posted.map((m) => m.type), ["ready"]);

// unacknowledged: gate shown, back must not skip it
assert.equal(run("androidInit(['start'], false)"), true);
assert.equal(els.gate.hidden, false);
assert.equal(run("androidBack()"), false);

// acknowledged restore lands on the saved node behind the gate
const start = run("NODES.start.opts[0].go");
assert.equal(run(`androidInit(['start','${start}'], true)`), true);
assert.equal(els.gate.hidden, true);
assert.equal(run("stack.length"), 2);
assert.equal(posted.at(-1).type, "state");
assert.equal(posted.at(-1).node, start);

// back walks the tree, then passes through at the start node
assert.equal(run("androidBack()"), true);
assert.equal(run("stack.length"), 1);
assert.equal(run("androidBack()"), false);

// disclaimer can be re-read; back closes it first
run("androidShowDisclaimer()");
assert.equal(els.gate.hidden, false);
assert.equal(run("androidBack()"), true);
assert.equal(els.gate.hidden, true);

// bad input is rejected / falls back safely
assert.equal(run("androidGoto(['start','nope'])"), false);
assert.equal(run("androidGoto(['__proto__'])"), false);
assert.equal(run("androidGoto(['start','start'])"), false);       // not a legal transition
assert.equal(run("androidRestore('start')"), false);
run("androidInit(['start','nope'], true)");
assert.equal(run("stack.join()"), "start");

// every search-index path is accepted by the page, and summary works on a result
const index = JSON.parse(fs.readFileSync(path.join(root, "app/src/main/assets/search-index.json"), "utf8"));
for (const e of index) {
  sandbox.__p = e.path;
  assert.equal(run("androidGoto(__p)"), true, `goto ${e.id}`);
  const s = run("androidSummary()");
  assert.ok(s.includes(e.end ? "Result:" : "Currently at:"), `summary ${e.id}`);
}

// the real gate button handler hides the gate and tells native
run("androidInit(['start'], false)");
assert.equal(els.gate.hidden, false);
els.gatebtn.handlers.click();
assert.equal(els.gate.hidden, true);
assert.equal(posted.at(-1).type, "ack");
assert.equal(run("androidBack()"), false); // at start, gate closed: exits

console.log(`bridge OK (${index.length} nodes round-tripped)`);
