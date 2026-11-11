// Runs the JCEF bundle in headless Chromium the way the plugin does (script injected into
// about:blank, no network) and compares every case with the official npm package.
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
// `npm i --no-save playwright const { chromium } = require("/opt/npm-tools/node_modules/playwright");const { chromium } = require("/opt/npm-tools/node_modules/playwright"); npx playwright install chromium`, or point PLAYWRIGHT_MODULE at an installed copy.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const cc = require("curlconverter");

const bundle = readFileSync(new URL("../../plugin/src/main/resources/curlconverter/curlconverter.browser.js", import.meta.url), "utf8");
const cases = JSON.parse(readFileSync(new URL("./cases.json", import.meta.url), "utf8"));
const fns = { python: cc.toPythonWarn, "python-http": cc.toPythonHttpWarn, go: cc.toGoWarn };

const browser = await chromium.launch();
const page = await browser.newPage();
const requests = [];
page.on("request", (r) => requests.push(r.url()));
await page.goto("about:blank");
const t0 = Date.now();
await page.evaluate(bundle);
await page.evaluate(() => globalThis.__curlconverter.ready);
console.log(`init ${Date.now() - t0} ms`);
let fail = 0, n = 0;
for (const t of Object.keys(fns)) for (const [i, c] of cases.entries()) {
  n++;
  let off; try { const [code, w] = fns[t](c); off = { code, warnings: w.map((x) => x[1]) }; } catch (e) { off = { error: String(e.message || e) }; }
  const got = await page.evaluate(([t, c]) => globalThis.__curlconverter.convert(t, c), [t, c]);
  if (JSON.stringify(off) !== JSON.stringify(got)) { fail++; console.log(`DIFF [${t}] ${i}\n ${JSON.stringify(off).slice(0, 200)}\n ${JSON.stringify(got).slice(0, 200)}`); }
}
console.log(`${n - fail}/${n} identical; network requests: ${requests.filter((u) => u !== "about:blank").length}`);
await browser.close();
process.exit(fail ? 1 : 0);
