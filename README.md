# curlconverter for PyCharm

[![CI](https://github.com/adw3r/curlconverter-for-pycharm/actions/workflows/ci.yml/badge.svg)](https://github.com/adw3r/curlconverter-for-pycharm/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/adw3r/curlconverter-for-pycharm)](https://github.com/adw3r/curlconverter-for-pycharm/releases/latest)

Paste a `curl` command into PyCharm, right-click, and get Python code. The plugin uses the converter behind
[curlconverter.com](https://curlconverter.com) ([curlconverter](https://github.com/curlconverter/curlconverter)
4.12.0) and runs it **inside the IDE, offline**. By default it writes Python for
[curl_cffi](https://github.com/lexiforest/curl_cffi), with browser impersonation and file uploads as
`CurlMime`. An async variant (an `AsyncSession` subclass), Python (requests) and 46 other targets are one setting away.

```bash
curl 'https://example.com/upload' -H 'user-agent: Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0' \
  -F 'file=@photo.jpg' -F 'description=avatar'
```

becomes

```python
from curl_cffi import CurlMime, requests

headers = {
    'user-agent': 'Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0',
}

multipart = CurlMime()
multipart.addpart(name='file', local_path='photo.jpg')
multipart.addpart(name='description', data=b'avatar')

response = requests.post('https://example.com/upload', headers=headers, multipart=multipart, impersonate='firefox')
```

## Install

**With auto-updates (recommended):**

1. PyCharm → **Settings → Plugins → ⚙ → Manage Plugin Repositories… → +**, paste
   ```
   https://github.com/adw3r/curlconverter-for-pycharm/releases/latest/download/updatePlugins.xml
   ```
2. **Marketplace** tab → search `curlconverter` → **Install**. Restart if PyCharm asks.

New releases then show up under **Plugins → Installed** and in the IDE's update notifications. Adding the
URL also works for a plugin that was installed from disk earlier.

**From a zip:**

1. Download `curlconverter-intellij-<version>.zip` from
   [Releases](https://github.com/adw3r/curlconverter-for-pycharm/releases/latest). Don't unzip it.
2. PyCharm → **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the zip.
3. Restart if PyCharm asks.

Works in PyCharm and every other IntelliJ-based IDE, version **2024.2 or newer** (tested on 2024.2.6 and
2026.2.4). No Node.js or network access needed.

Without the repository URL, update by installing the newer zip the same way.

## Use

**Scratch file**

1. `Ctrl+Alt+Shift+Insert` (New Scratch File) → *Plain Text*.
2. Paste the command.
3. Right-click → **Convert cURL to Python (curl_cffi)**.

The command is replaced with code, and the scratch is renamed to `.py`, so it is highlighted and runnable.

**Any file**

Put the caret on a `curl` command (or right after it), or select it, then right-click → the same item. The
item only shows up when there is a `curl` command under the caret. Indentation is kept, so you can
convert inside a function.

**Straight from the clipboard**

**Tools → curlconverter → New Python (curl_cffi) Scratch from Clipboard cURL** opens a new `.py` scratch
with the converted code.

**Keyboard shortcut**

None by default. **Settings → Keymap**, search for `Convert cURL`, then *Add Keyboard Shortcut*.

**Where to copy commands from**

- Chrome/Edge/Firefox DevTools → Network → right-click a request → Copy → **Copy as cURL (bash)**. The
  Windows-only *(cmd)* variant isn't supported; curlconverter reads bash syntax.
- Burp Suite → right-click a request → **Copy as curl command (bash)**.

## Settings

**Settings → Tools → curlconverter**

| Setting | Default | |
|---|---|---|
| Convert to | Python (curl_cffi) | Also Python (curl_cffi, async class), Python (requests), Python (http.client), Go, Node.js, PHP, Rust, … (49 targets) |
| curl_cffi impersonate | auto | `auto` reads the copied User-Agent: Firefox → `firefox`, Safari → `safari`/`safari_ios`, Android Chrome → `chrome_android`, anything else → `chrome`. Or pick one, or `none` |
| Engine | Automatic | JCEF (the IDE's built-in Chromium), falling back to Node.js |
| Node.js executable | empty | Only for the Node.js engine; empty means `node` from PATH |
| Rename scratch files | on | `scratch.txt` → `scratch.py` after converting |
| Show curlconverter warnings | on | Things the generated code does differently, e.g. "%20 will be sent as +" |

## The curl_cffi target

[`js/src/curl_cffi/python.js`](js/src/curl_cffi/python.js) is a fork of curlconverter's Python (Requests)
generator; every change is marked `curl_cffi:`. It works on curlconverter's parsed request rather than
rewriting the generated text. What differs from the Requests output, and why (checked against curl_cffi 0.17):

| curl | Python (requests) | Python (curl_cffi) |
|---|---|---|
| `-F 'f=@a.png'`, `-F 'x=1'` | `files={'f': open(...), 'x': (None, '1')}` | `multipart.addpart(name='f', local_path='a.png')`, `addpart(name='x', data=b'1')`; curl_cffi has no `files=` |
| `-F 'f=@a.pdf;type=application/pdf;filename=b.pdf'` | `('b.pdf', open(...), 'application/pdf')` | `addpart(name='f', content_type='application/pdf', filename='b.pdf', local_path='a.pdf')` |
| `-F 'f=<a.txt'` | `(None, open(...))` | `addpart(name='f', data=Path('a.txt').read_bytes())`, no filename, like curl |
| Chrome's multipart (`--data-raw` + `boundary=` header) | header kept | header commented out, because libcurl writes its own boundary |
| compact JSON body (what browsers send) | `json=` + the exact body as a commented-out `data=` | `json=` only, because curl_cffi serializes with `separators=(",", ":")` |
| repeated form keys | `data={'a': ['1', '2']}` | `data=[('a', '1'), ('a', '2')]`, because curl_cffi urlencodes `data=` without `doseq` |
| `-T file` | `data=` | `data=` + `'content-type': None`, because curl sends no Content-Type |
| `-m 30 --connect-timeout 5` | `timeout=(5, 30)` | `timeout=(5, 25)`, because curl_cffi's total is connect + read |
| `--max-redirs 5` | `requests.Session()` + `max_redirects` | `max_redirects=5` |
| `--http3` | warning | `http_version='v3'` |
| `--digest`, `--ntlm`, … | `HTTPDigestAuth(...)`, … | warning: curl_cffi `auth=` is Basic only |

[`js/test/curl_cffi_e2e.py`](js/test/curl_cffi_e2e.py) checks this end to end. Each case is sent to a local
recording server twice: once by the real `curl`, once by the generated curl_cffi code. The script then
compares method, path, headers, Content-Type and body; multipart bodies are compared part by part.

### Python (curl_cffi, async class)

The same generator, wrapped in a class: `class ExampleApi(AsyncSession)` (named after the host) with one
`async def` per request (`--next` adds more), named after the method and the last path segment
(`POST /api/addReview` → `post_add_review`). Plain values of `params`, `data` and `json_data` become keyword
arguments whose defaults are the values from the curl command; nested values, files and repeated keys stay
inline. A `main()` that calls every method with the defaults is generated too, and the e2e test runs every
case through it as well.

```python
class ExampleApi(AsyncSession):
    async def get_items(self, page='2', page_size='10'):
        params = {
            'page': page,
            'pageSize': page_size,
        }

        response = await self.get('https://api.example.com/v1/items', params=params, impersonate='chrome')
        return response
```

## How it works

curlconverter is JavaScript plus WebAssembly (tree-sitter-bash). The plugin ships it as an esbuild bundle
with the `.wasm` inlined and runs it in an off-screen JCEF page:

- The page starts on the first conversion (~0.2–0.6 s) and closes after 10 idle minutes.
- It makes no network requests.
- Output is identical to the npm package's.

If JCEF is unavailable, the plugin falls back to `node` on PATH running the same bundle. Since 2026.2,
JCEF comes from the bundled *Web Browser (JCEF)* plugin; the dependency on it is optional, so older IDEs
keep working.

## Development

```
js/                         curlconverter bundling
  src/parser.js             swaps curlconverter's tree-sitter loader for web-tree-sitter + inlined wasm
  src/curl_cffi/python.js   curl_cffi generator (fork of curlconverter's Requests generator)
  src/entry.js              target table + convert() for the JCEF bundle
  src/node-cli.js           stdin → JSON CLI for the Node.js engine
  build.mjs                 writes plugin/src/main/resources/curlconverter/*
  test/                     bundle vs official package (Node, headless Chromium), curl vs curl_cffi e2e
plugin/                     IntelliJ plugin (Kotlin, Gradle, IntelliJ Platform Gradle Plugin 2.x)
```

The built bundles are committed, so building the plugin needs only JDK 21.

```bash
# plugin
cd plugin
./gradlew buildPlugin                     # → build/distributions/curlconverter-intellij-<version>.zip
xvfb-run -a ./gradlew test                # tests in PyCharm 2024.2.6 with real JCEF (drop xvfb-run on a desktop)
./gradlew test -PlocalIde=/path/to/pycharm          # same tests against an installed IDE
./gradlew verifyPlugin -PverifyIde=/path/to/pycharm # check the built zip against another IDE

# bundle (Node 22)
cd js
npm ci
npm run build                             # rebuild the bundles after changing js/src
npm run test:diff                         # bundle == official curlconverter
npm i --no-save playwright && npx playwright install chromium && npm run test:browser
pip install curl_cffi && npm run test:e2e # real curl vs generated curl_cffi code
npm run expected                          # refresh plugin/src/test/resources/expected.json
```

CI runs all of the above on every push.

**Releasing:**

1. Bump `pluginVersion` in `plugin/gradle.properties` and add a `## [X.Y.Z]` section to
   [CHANGELOG.md](CHANGELOG.md).
2. Push a tag `vX.Y.Z`, or create a release with that tag on GitHub.
3. CI builds and tests the zip and attaches it to the release, with notes from the changelog, together with
   `updatePlugins.xml` (generated by `.github/scripts/update-plugins-xml.sh`). Because IDEs read that file
   from `releases/latest`, the release must not be a draft or a pre-release, or nobody gets the update.

**Updating curlconverter:**

1. Bump the version in `js/package.json` and run `npm install`.
2. Re-apply the `curl_cffi:` changes in `js/src/curl_cffi/python.js` onto the new upstream
   `generators/python/python.js`.
3. Run `npm run build` and `npm run expected`.
4. Update `CURLCONVERTER_VERSION` in `Targets.kt`.

## Credits

- [curlconverter](https://github.com/curlconverter/curlconverter) by Nick Carneiro and contributors (MIT).
  It is bundled in the plugin, and `js/src/curl_cffi/python.js` is derived from it. See
  [`LICENSE-curlconverter`](js/src/curl_cffi/LICENSE-curlconverter).
- [curl_cffi](https://github.com/lexiforest/curl_cffi) is the target library; it is not bundled.

---

## Коротко по-русски

**Установка**

1. Скачать zip из [Releases](https://github.com/adw3r/curlconverter-for-pycharm/releases/latest).
2. PyCharm → Settings → Plugins → ⚙ → **Install Plugin from Disk…** → выбрать zip.

Нужен PyCharm 2024.2 или новее.

**Использование**

- **Scratch:** `Ctrl+Alt+Shift+Insert` → Plain Text → вставить curl → ПКМ → **Convert cURL to Python (curl_cffi)**. Scratch сам станет `.py`.
- **Любой файл:** курсор на curl (или выделение) → тот же пункт контекстного меню.
- **Из буфера сразу в scratch:** Tools → curlconverter → **New Python (curl_cffi) Scratch from Clipboard cURL**.
- **Хоткей:** Settings → Keymap → `Convert cURL`.
- **Async-класс:** Settings → Tools → curlconverter → Convert to → **Python (curl_cffi, async class)**: класс от `AsyncSession`, по методу `async def` на каждый запрос, значения из `params`/`data`/`json_data` становятся аргументами с дефолтами из curl.
- **Откуда копировать:** DevTools → Network → Copy → **Copy as cURL (bash)**, или Burp → **Copy as curl command (bash)**.

**Настройки:** Settings → Tools → curlconverter. Там выбирается язык (requests и ещё 46 вариантов, всего 48), режим
`impersonate` для curl_cffi (auto — по User-Agent) и движок.
