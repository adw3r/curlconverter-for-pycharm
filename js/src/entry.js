import * as cc from "curlconverter";
import { ready } from "./parser.js";
import { toPythonCurlCffiWarn } from "./curl_cffi/python.js";

// [id (same as the curlconverter CLI --language), label, generator]
const TARGETS = [
  ["python-curl-cffi", "Python (curl_cffi)", (curl, warnings, options) => toPythonCurlCffiWarn(curl, warnings, options)],
  ["python", "Python (requests)", cc.toPythonWarn],
  ["python-http", "Python (http.client)", cc.toPythonHttpWarn],
  ["ansible", "Ansible", cc.toAnsibleWarn],
  ["c", "C", cc.toCWarn],
  ["cfml", "ColdFusion", cc.toCFMLWarn],
  ["clojure", "Clojure", cc.toClojureWarn],
  ["csharp", "C#", cc.toCSharpWarn],
  ["dart", "Dart", cc.toDartWarn],
  ["elixir", "Elixir", cc.toElixirWarn],
  ["go", "Go", cc.toGoWarn],
  ["har", "HAR", cc.toHarStringWarn],
  ["http", "HTTP", cc.toHTTPWarn],
  ["httpie", "HTTPie", cc.toHttpieWarn],
  ["java", "Java (HttpClient)", cc.toJavaWarn],
  ["java-httpurlconnection", "Java (HttpURLConnection)", cc.toJavaHttpUrlConnectionWarn],
  ["java-jsoup", "Java (jsoup)", cc.toJavaJsoupWarn],
  ["java-okhttp", "Java (OkHttp)", cc.toJavaOkHttpWarn],
  ["javascript", "JavaScript (fetch)", cc.toJavaScriptWarn],
  ["javascript-jquery", "JavaScript (jQuery)", cc.toJavaScriptJqueryWarn],
  ["javascript-xhr", "JavaScript (XHR)", cc.toJavaScriptXHRWarn],
  ["json", "JSON", cc.toJsonStringWarn],
  ["julia", "Julia", cc.toJuliaWarn],
  ["kotlin", "Kotlin", cc.toKotlinWarn],
  ["lua", "Lua", cc.toLuaWarn],
  ["matlab", "MATLAB", cc.toMATLABWarn],
  ["node", "Node.js (fetch)", cc.toNodeWarn],
  ["node-axios", "Node.js (Axios)", cc.toNodeAxiosWarn],
  ["node-got", "Node.js (got)", cc.toNodeGotWarn],
  ["node-http", "Node.js (http)", cc.toNodeHttpWarn],
  ["node-ky", "Node.js (Ky)", cc.toNodeKyWarn],
  ["node-request", "Node.js (request)", cc.toNodeRequestWarn],
  ["node-superagent", "Node.js (SuperAgent)", cc.toNodeSuperAgentWarn],
  ["objc", "Objective-C", cc.toObjectiveCWarn],
  ["ocaml", "OCaml", cc.toOCamlWarn],
  ["perl", "Perl", cc.toPerlWarn],
  ["php", "PHP (curl)", cc.toPhpWarn],
  ["php-guzzle", "PHP (Guzzle)", cc.toPhpGuzzleWarn],
  ["php-requests", "PHP (Requests)", cc.toPhpRequestsWarn],
  ["powershell", "PowerShell (Invoke-RestMethod)", cc.toPowershellRestMethodWarn],
  ["powershell-webrequest", "PowerShell (Invoke-WebRequest)", cc.toPowershellWebRequestWarn],
  ["r", "R (httr)", cc.toRWarn],
  ["r-httr2", "R (httr2)", cc.toRHttr2Warn],
  ["ruby", "Ruby (Net::HTTP)", cc.toRubyWarn],
  ["ruby-httparty", "Ruby (HTTParty)", cc.toRubyHttpartyWarn],
  ["rust", "Rust", cc.toRustWarn],
  ["swift", "Swift", cc.toSwiftWarn],
  ["wget", "Wget", cc.toWgetWarn],
];
const BY_ID = Object.fromEntries(TARGETS.map((t) => [t[0], t[2]]));

// options: { impersonate: "auto" | "none" | "chrome" | ... } (only the curl_cffi target uses it)
function convert(target, curl, options) {
  const gen = BY_ID[target];
  if (!gen) return { error: `Unknown target language: ${target}` };
  try {
    const [code, warnings] = gen(curl, [], options || {});
    return { code, warnings: warnings.map((w) => w[1]) };
  } catch (e) {
    return { error: String((e && e.message) || e) };
  }
}

export const api = {
  version: CURLCONVERTER_VERSION,
  ready,
  targets: TARGETS.map(([id, label]) => ({ id, label })),
  convert,
};
globalThis.__curlconverter = api;
