// Compares the plugin's Node bundle (web-tree-sitter, inlined wasm) with the official
// curlconverter npm package (native tree-sitter) for every case and target given.
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const cc = require("curlconverter");

const cases = JSON.parse(readFileSync(new URL("./cases.json", import.meta.url), "utf8"));
const targets = (process.argv[2] || "python,python-http").split(",");
const fnFor = { python: cc.toPythonWarn, "python-http": cc.toPythonHttpWarn, go: cc.toGoWarn, node: cc.toNodeWarn, php: cc.toPhpWarn, http: cc.toHTTPWarn };

function official(target, curl) {
  try { const [code, w] = fnFor[target](curl); return { code, warnings: w.map((x) => x[1]) }; }
  catch (e) { return { error: String(e.message || e) }; }
}
function bundle(target, curl) {
  const r = spawnSync("node", [new URL("../../plugin/src/main/resources/curlconverter/curlconverter.node.cjs", import.meta.url).pathname, target], { input: curl, encoding: "utf8" });
  const j = JSON.parse(r.stdout); delete j.version; return j;
}
let fail = 0, n = 0;
for (const t of targets) for (const [i, c] of cases.entries()) {
  n++;
  const a = JSON.stringify(official(t, c)), b = JSON.stringify(bundle(t, c));
  if (a !== b) { fail++; console.log(`DIFF [${t}] case ${i}\n official: ${a.slice(0, 300)}\n bundle:   ${b.slice(0, 300)}`); }
}
console.log(`${n - fail}/${n} identical`);
process.exit(fail ? 1 : 0);
