import * as esbuild from "esbuild";
import { readFileSync, copyFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

const require = createRequire(import.meta.url);
const ccPkgPath = require.resolve("curlconverter/package.json");
const ccDir = path.dirname(ccPkgPath);
const version = JSON.parse(readFileSync(ccPkgPath, "utf8")).version;
const out = process.argv[2] || "../plugin/src/main/resources/curlconverter";

const swapParser = {
  name: "swap-parser",
  setup(b) {
    b.onResolve({ filter: /^\.\/(web)?Parser\.js$/ }, (args) =>
      args.importer.includes(`${path.sep}shell${path.sep}`)
        ? { path: path.resolve("src/parser.js") }
        : undefined);
    b.onResolve({ filter: /^curlconverter\/dist\/tree-sitter-bash\.wasm$/ }, () =>
      ({ path: path.join(ccDir, "dist", "tree-sitter-bash.wasm") }));
  },
};

const common = {
  bundle: true,
  minify: true,
  legalComments: "none",
  loader: { ".wasm": "base64" },
  define: { CURLCONVERTER_VERSION: JSON.stringify(version) },
  plugins: [swapParser],
  logLevel: "warning",
};

await esbuild.build({
  ...common,
  entryPoints: ["src/entry.js"],
  outfile: `${out}/curlconverter.browser.js`,
  platform: "browser",
  format: "iife",
  target: "chrome110",
  external: ["fs", "path", "fs/promises", "module", "url"],
});

await esbuild.build({
  ...common,
  entryPoints: ["src/node-cli.js"],
  outfile: `${out}/curlconverter.node.cjs`,
  platform: "node",
  format: "cjs",
  target: "node16",
});

copyFileSync(path.join(ccDir, "LICENSE"), `${out}/LICENSE-curlconverter.txt`);
console.log(`curlconverter ${version} -> ${out}`);
