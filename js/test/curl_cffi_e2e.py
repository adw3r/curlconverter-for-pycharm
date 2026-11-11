#!/usr/bin/env python3
"""
End-to-end check of the "Python (curl_cffi)" target: every case is sent twice to a local recording
server, once by the real `curl` binary and once by the Python code the plugin generates (run with
curl_cffi). The two recorded requests must match: method, path + query, the headers given with -H/-b,
Content-Type, and the body (multipart compared part by part, since boundaries are random).

    python3 test/curl_cffi_e2e.py [path/to/curlconverter.node.cjs]
"""
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
from email.parser import BytesParser
from email.policy import HTTP
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qsl

BUNDLE = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "../../plugin/src/main/resources/curlconverter/curlconverter.node.cjs"))

RECORDED = []


class Recorder(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _handle(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        RECORDED.append({"method": self.command, "path": self.path, "headers": list(self.headers.items()), "body": body})
        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"ok")

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = do_OPTIONS = _handle

    def do_HEAD(self):  # noqa: F811 - no body in HEAD responses
        RECORDED.append({"method": "HEAD", "path": self.path, "headers": list(self.headers.items()), "body": b""})
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, *args):
        pass


def header(rec, name):
    vals = [v for k, v in rec["headers"] if k.lower() == name.lower()]
    return vals[0] if vals else None


def multipart_parts(rec):
    ctype = header(rec, "content-type")
    msg = BytesParser(policy=HTTP).parsebytes(b"Content-Type: " + ctype.encode() + b"\r\n\r\n" + rec["body"])
    parts = []
    for p in msg.iter_parts():
        parts.append({
            "name": p.get_param("name", header="content-disposition"),
            "filename": p.get_param("filename", header="content-disposition"),
            "type": p.get_content_type() if p.get("content-type") else None,
            "data": p.get_payload(decode=True),
        })
    return parts


def summary(rec, user_headers):
    ctype = header(rec, "content-type") or ""
    out = {
        "method": rec["method"],
        "path": rec["path"],
        "content-type": re.sub(r";\s*boundary=.*", "; boundary=*", ctype) if "multipart/" in ctype else ctype,
        "cookie": header(rec, "cookie"),
        "authorization": header(rec, "authorization"),
        "headers": {h: header(rec, h) for h in user_headers},
    }
    if ctype.startswith("multipart/form-data"):
        out["parts"] = multipart_parts(rec)
    else:
        out["body"] = rec["body"]
    return out


def user_header_names(curl):
    names = re.findall(r"""-H\s+\$?['"]([^:'"]+):""", curl)
    skip = {"content-type", "cookie", "accept-encoding", "content-length", "host", "user-agent"}
    return sorted({n.lower() for n in names} - skip)


CASES = [
    # multipart
    "curl {URL}/upload -F 'file=@photo.jpg' -F 'description=avatar'",
    "curl {URL}/upload -F 'doc=@report.pdf;type=application/pdf;filename=q3.pdf' -F 'meta={{\"tag\":\"q3\"}};type=application/json' -F 'note=<notes.txt' -F 'a=1' -F 'a=2'",
    "curl {URL}/upload -F 'blob=@sub/dir/data.bin'",
    "curl {URL}/upload -F 'img=@pic.png' -F 'txt=@notes.txt'",
    "curl {URL}/upload -F 'text=hello;filename=hello.txt' -F 'empty='",
    "curl {URL}/upload -X PUT -F 'file=@photo.jpg;type=image/jpeg' -H 'X-Token: abc'",
    # Chrome's own multipart (body inlined, boundary in the header)
    "curl {URL}/upload -H 'content-type: multipart/form-data; boundary=----WebKitFormBoundaryX7' --data-raw $'------WebKitFormBoundaryX7\\r\\nContent-Disposition: form-data; name=\"field\"\\r\\n\\r\\nvalue\\r\\n------WebKitFormBoundaryX7--\\r\\n'",
    # bodies
    "curl {URL}/api -H 'content-type: application/json' --data-raw '{{\"user\":\"a\",\"remember\":true,\"n\":[1,2,null],\"u\":\"\\u043f\"}}'",
    # Same trade-off curlconverter makes for Requests: readable data= dict, "%20" becomes "+", with a warning.
    ("curl {URL}/form --data-raw 'a=1&b=two%20words&c='", "form-equivalent", "which will be sent as \"+\""),
    "curl {URL}/form -X PUT --data-raw 'a=1&a=2'",
    "curl {URL}/raw --data-raw 'just text, not a form'",
    "curl {URL}/raw -H 'content-type: application/octet-stream' --data-binary @dump.bin",
    "curl {URL}/raw -H 'content-type: text/plain' --data-binary @notes.txt",
    "curl {URL}/put -T dump.bin",
    # query, cookies, headers, auth, methods
    "curl -G {URL}/search --data-urlencode 'q=hello world' --data-urlencode 'lang=uk'",
    "curl '{URL}/items?page=2&sort=-date' -b 'session=abc; theme=dark' -H 'X-Requested-With: XMLHttpRequest' -H 'Referer: https://example.com/'",
    "curl {URL}/private -u 'user:p@ss'",
    "curl {URL}/item/1 -X DELETE -H 'Authorization: Bearer t0k3n'",
    "curl -I {URL}/head",
    # Non-compact JSON: json= plus the exact body as a commented-out data= alternative (as for Requests).
    ("curl {URL}/patch -X PATCH -H 'content-type: application/json' --data-raw '{{\"a\": 1}}'", "json-equivalent", "#data = '{\"a\": 1}'"),
]


def main():
    work = tempfile.mkdtemp(prefix="curl_cffi_e2e_")
    os.makedirs(os.path.join(work, "sub/dir"))
    files = {
        "photo.jpg": b"\xff\xd8\xff\xe0JFIF-fake" + bytes(range(256)),
        "report.pdf": b"%PDF-1.4 fake\n",
        "notes.txt": "привет, notes\n".encode(),
        "pic.png": b"\x89PNG\r\n\x1a\nfake",
        "dump.bin": bytes(range(256)) * 8,
        "sub/dir/data.bin": b"\x00\x01binary",
    }
    for name, data in files.items():
        with open(os.path.join(work, name), "wb") as f:
            f.write(data)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Recorder)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{server.server_address[1]}"

    failures = 0
    for i, case in enumerate(CASES):
        template, mode, must_mention = case if isinstance(case, tuple) else (case, "exact", None)
        curl = template.format(URL=url)
        conv = json.loads(subprocess.run(["node", BUNDLE, "python-curl-cffi", json.dumps({"impersonate": "none"})],
                                         input=curl.encode(), capture_output=True, check=True).stdout)
        if "error" in conv:
            print(f"[{i}] CONVERT ERROR {conv['error']}\n    {curl}")
            failures += 1
            continue

        RECORDED.clear()
        subprocess.run(["bash", "-c", curl + " -s -o /dev/null"], cwd=work, check=True)
        by_curl = RECORDED[-1]

        RECORDED.clear()
        run = subprocess.run([sys.executable, "-c", conv["code"]], cwd=work, capture_output=True, text=True)
        if run.returncode != 0 or not RECORDED:
            print(f"[{i}] PYTHON FAILED\n    {curl}\n{conv['code']}\n{run.stderr[-1500:]}")
            failures += 1
            continue
        by_python = RECORDED[-1]

        names = user_header_names(curl)
        a, b = summary(by_curl, names), summary(by_python, names)
        if mode != "exact":
            decode = (lambda body: sorted(parse_qsl(body.decode(), keep_blank_values=True))) if mode == "form-equivalent" \
                else (lambda body: json.loads(body))
            same_meaning = decode(a.pop("body")) == decode(b.pop("body"))
            mentioned = must_mention in conv["code"] or any(must_mention in w for w in conv["warnings"])
            if not (same_meaning and mentioned):
                a["body-meaning"], b["body-meaning"] = True, f"same={same_meaning} mentioned={mentioned}"
        if a == b:
            print(f"[{i}] ok   {'' if mode == 'exact' else '(' + mode + ') '}{template[:110]}")
        else:
            failures += 1
            print(f"[{i}] DIFF {curl}\n--- generated:\n{conv['code']}")
            for k in a:
                if a[k] != b.get(k):
                    print(f"    {k}:\n      curl:      {a[k]!r}\n      curl_cffi: {b.get(k)!r}")
        if conv["warnings"]:
            print(f"       warnings: {conv['warnings']}")

    # The default (impersonated) code must run as well.
    conv = json.loads(subprocess.run(["node", BUNDLE, "python-curl-cffi"], input=CASES[0].format(URL=url).encode(),
                                     capture_output=True, check=True).stdout)
    RECORDED.clear()
    run = subprocess.run([sys.executable, "-c", conv["code"]], cwd=work, capture_output=True, text=True)
    ua = header(RECORDED[-1], "user-agent") if RECORDED else None
    if run.returncode != 0 or "impersonate='chrome'" not in conv["code"] or not ua or "Chrome/" not in ua:
        failures += 1
        print(f"impersonate run FAILED: rc={run.returncode} ua={ua!r}\n{run.stderr[-800:]}")
    else:
        print(f"impersonate='chrome' run ok, User-Agent: {ua[:60]}…")

    server.shutdown()
    print(f"\nall {len(CASES)} cases match curl" if failures == 0 else f"\n{failures} failure(s)")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
