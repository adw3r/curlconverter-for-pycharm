# Changelog

## [1.1.0] - 2026-11-03

### Added
- **Python (curl_cffi)** target, now the default: `from curl_cffi import requests` with `impersonate=`.
- `-F` file uploads become `multipart=CurlMime()` with `addpart(...)`. Part order, repeated names,
  `type=`, `filename=` and `<file` fields are kept.
- *curl_cffi impersonate* setting: `auto` (from the copied User-Agent), a fixed browser, or `none`.

### Changed
- Python (requests) and the other targets still work; pick them in **Settings → Tools → curlconverter**.

### Checked
- Every generated case is replayed against a local server by both the real `curl` and the generated
  curl_cffi code, and the recorded requests must match (`js/test/curl_cffi_e2e.py`).
- Tests pass in PyCharm 2024.2.6 and 2026.2.4; Plugin Verifier: compatible with both.

## [1.0.0] - 2026-11-01

### Added
- curlconverter 4.12.0 bundled and run in the IDE's JCEF, offline, with a Node.js fallback. Output is
  identical to the npm package.
- Editor context menu **Convert cURL to …**: converts the selection or the command at the caret.
  Scratch files are renamed to the target language.
- **Tools → curlconverter → New … Scratch from Clipboard cURL**.
- Settings: target language (47 curlconverter targets), engine, Node.js path, scratch renaming, warnings.
- Works on IntelliJ-based IDEs 2024.2+, including 2026.2, where JCEF moved to a bundled plugin.
