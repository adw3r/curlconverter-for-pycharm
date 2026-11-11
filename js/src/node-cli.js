// One-shot CLI used by the plugin's Node.js engine:
//   node curlconverter.node.cjs <target>   (curl command on stdin, JSON result on stdout)
// Options (JSON, e.g. {"impersonate":"auto"}) come from $CURLCONVERTER_OPTIONS or the 2nd argument;
// the environment avoids Windows command-line quoting of JSON.
import { api } from "./entry.js";

const target = process.argv[2] || "python-curl-cffi";
let options = {};
try { options = JSON.parse(process.env.CURLCONVERTER_OPTIONS || process.argv[3] || "{}"); } catch (e) { /* keep defaults */ }
const chunks = [];
process.stdin.on("data", (c) => chunks.push(c));
process.stdin.on("end", async () => {
  const curl = Buffer.concat(chunks).toString("utf8");
  let result;
  try {
    await api.ready;
    result = api.convert(target, curl, options);
  } catch (e) {
    result = { error: String((e && e.message) || e) };
  }
  process.stdout.write(JSON.stringify({ version: api.version, ...result }));
});
