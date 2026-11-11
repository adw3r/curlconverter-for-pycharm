// Replaces curlconverter's shell/Parser.js (native tree-sitter) and shell/webParser.js
// (web-tree-sitter loaded over HTTP). Same web-tree-sitter + tree-sitter-bash.wasm that
// curlconverter.com runs, but the .wasm files are inlined so nothing is fetched at runtime.
import Parser from "web-tree-sitter";
import treeSitterWasm from "web-tree-sitter/tree-sitter.wasm";
import bashWasm from "curlconverter/dist/tree-sitter-bash.wasm";

function decode(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

let real = null;

export const ready = (async () => {
  await Parser.init({ wasmBinary: decode(treeSitterWasm) });
  const Bash = await Parser.Language.load(decode(bashWasm));
  const p = new Parser();
  p.setLanguage(Bash);
  real = p;
})();

export default {
  parse(source) {
    if (!real) throw new Error("curlconverter parser is not initialized yet");
    return real.parse(source);
  },
};
