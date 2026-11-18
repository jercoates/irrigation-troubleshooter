// Generates app/src/main/assets/{search-index,help}.json from index.html.
// index.html is the single source of truth for the tree; never edit the JSON by hand.
//
//   node tools/extract.mjs          regenerate
//   node tools/extract.mjs --check  exit 1 if generated files are stale or the tree is broken
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const assets = path.join(root, "app/src/main/assets");
const html = fs.readFileSync(path.join(assets, "index.html"), "utf8");

// Evaluate only the data section (HELP + NODES), never the DOM code.
const from = html.indexOf("var HELP");
const to = html.indexOf("var stack");
if (from < 0 || to < 0 || to < from) throw new Error("Cannot locate HELP/NODES in index.html");
const ctx = vm.createContext({});
vm.runInContext(html.slice(from, to) + "\n;this.HELP=HELP;this.NODES=NODES;", ctx, { timeout: 2000 });
const { HELP, NODES } = ctx;

const ENT = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " ", mdash: "\u2014", ndash: "\u2013",
  hellip: "\u2026", rsquo: "\u2019", lsquo: "\u2018", rdquo: "\u201d", ldquo: "\u201c", deg: "\u00b0", times: "\u00d7" };
const plain = (s) => String(s ?? "")
  .replace(/<[^>]*>/g, " ")
  .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
  .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
  .replace(/&([a-z]+);/gi, (m, n) => (n.toLowerCase() in ENT ? ENT[n.toLowerCase()] : m))
  .replace(/\u2011/g, "-").replace(/\s+/g, " ").trim();

// ---- validate the tree ----
const errors = [];
if (!NODES.start) errors.push("missing 'start' node");
for (const [id, n] of Object.entries(NODES)) {
  if (!/^[A-Za-z0-9_]+$/.test(id)) errors.push(`bad node id '${id}'`);
  if (n.end) {
    if (!n.h || !Array.isArray(n.b)) errors.push(`result '${id}' needs h and b[]`);
  } else {
    if (!n.q || !Array.isArray(n.opts) || !n.opts.length) errors.push(`question '${id}' needs q and opts[]`);
    for (const o of n.opts ?? []) if (!NODES[o.go]) errors.push(`'${id}' -> unknown node '${o.go}'`);
  }
  for (const k of [n.help, n.help2]) if (k && !HELP[k]) errors.push(`'${id}' references unknown help '${k}'`);
}

// ---- shortest path from start to every node (so Back works after a search jump) ----
const prev = { start: null };
const queue = ["start"];
while (queue.length) {
  const id = queue.shift();
  for (const o of NODES[id]?.opts ?? []) {
    if (NODES[o.go] && !(o.go in prev)) { prev[o.go] = id; queue.push(o.go); }
  }
}
for (const id of Object.keys(NODES)) if (!(id in prev)) errors.push(`node '${id}' is unreachable from start`);
if (errors.length) { console.error("Tree problems:\n - " + errors.join("\n - ")); process.exit(1); }

const pathTo = (id) => { const p = []; for (let c = id; c; c = prev[c]) p.unshift(c); return p; };

const index = Object.keys(NODES).sort().map((id) => {
  const n = NODES[id];
  const p = pathTo(id);
  const text = n.end
    ? n.b.map(plain).join(" ")
    : [n.detail, ...(n.opts ?? []).flatMap((o) => [o.l, o.s])].map(plain).join(" ");
  return {
    id,
    end: !!n.end,
    title: plain(n.end ? n.h : n.q),
    crumb: plain(n.end ? "Result" : n.crumb),
    trail: p.slice(0, -1).map((x) => plain(NODES[x].crumb ?? "")).filter(Boolean),
    path: p,
    text,
  };
});

const help = Object.keys(HELP).map((key) => ({ key, title: plain(HELP[key].t), body: HELP[key].b }));

const out = {
  "search-index.json": JSON.stringify(index, null, 1) + "\n",
  "help.json": JSON.stringify(help, null, 1) + "\n",
};

let stale = false;
for (const [name, data] of Object.entries(out)) {
  const f = path.join(assets, name);
  if (process.argv.includes("--check")) {
    if (!fs.existsSync(f) || fs.readFileSync(f, "utf8") !== data) { console.error(`${name} is stale`); stale = true; }
  } else {
    fs.writeFileSync(f, data);
    console.log(`wrote ${name}`);
  }
}
if (stale) { console.error("Run: node tools/extract.mjs"); process.exit(1); }
console.log(`${index.length} nodes, ${help.length} help sections OK`);
