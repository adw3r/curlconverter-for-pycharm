// Writes the official curlconverter (npm, native tree-sitter) results for every test case, so the
// plugin's tests can check that JCEF/Node inside the IDE produce exactly the same output.
import { readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const cc = require("curlconverter");
// Our curl_cffi generator, run on the same native tree-sitter parser as the official package.
const { toPythonCurlCffiWarn } = await import("../src/curl_cffi/python.js");
const cases = JSON.parse(readFileSync(new URL("./cases.json", import.meta.url), "utf8"));
const fns = { "python-curl-cffi": (c) => toPythonCurlCffiWarn(c, []), "python-curl-cffi-async": (c) => toPythonCurlCffiWarn(c, [], { asyncClass: true }), python: cc.toPythonWarn, "python-http": cc.toPythonHttpWarn, go: cc.toGoWarn };
const out = [];
for (const [target, fn] of Object.entries(fns)) for (const curl of cases) {
  let result;
  try { const [code, w] = fn(curl); result = { code, warnings: w.map((x) => x[1]) }; }
  catch (e) { result = { error: String(e.message || e) }; }
  out.push({ target, curl, ...result });
}
const dest = process.argv[2];
writeFileSync(dest, JSON.stringify({ version: require("curlconverter/package.json").version, cases: out }, null, 1));
console.log(`${out.length} expected results -> ${dest}`);
