# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-01-31

### Added

- Initial release
- Context menu: Dump request/response body, full message, copy to clipboard
- Content-Type aware filenames with Content-Disposition support
- Content decoding: gzip, deflate, brotli (br)
- Magic-byte detection for unknown types
- Batch dump for multiple selections
- Multipart extraction
- Settings panel: default save dir, open after save, overwrite confirmation, success toast
- Keyboard shortcut: Ctrl+Shift+D (HTTP message editor, Proxy history)
- Copy to clipboard: base64, hex
- Filename collision handling (suffix -2, -3, etc.)
- Extended MIME map (woff, woff2, js, css, html, wasm)
- Dump selection only (when text selected in message editor)
- Base64 decode option (when body decodes successfully)
- Unit tests for DumpBinaryUtils
- Checksum/hash: Copy MD5, Copy SHA-256 to clipboard
- URL-safe Base64 decode
- Hex string decode
- Dump to temp (one-click to temp dir)
- WebSocket support: dump payload, selection, batch dump, binary diff from WebSocket message editor and table
- Content decoding: zstd (Content-Encoding: zstd)
- Chained decoding: base64→gzip and gzip→base64 (for double-encoded bodies)
- JSON/XML pretty-print option
- Binary diff: compare two selected items (hex view with diff markers)
- Custom magic bytes: user-defined hex:ext patterns in Settings
- MessagePack: dump as JSON when MessagePack detected
- CBOR: dump as JSON when CBOR detected
- **String extraction** — Extract printable strings from binary (URLs, paths, tokens); dialog with copy-to-clipboard
- **Send to Comparer** — Send body/selection to Burp's Comparer; when 2 items selected, send both for comparison
- **Send to Decoder** — Send body/selection to Burp's Decoder for further analysis
- **LZ4 / Snappy** — Decompression for Content-Encoding: lz4, snappy, x-snappy
- **Multiple Content-Encoding** — Handle chained encodings (e.g. `gzip, br`) by applying decompression in reverse order
- **Auto-detect encoding** — "Dump auto-decoded" menu item tries gzip, deflate, br, zstd, LZ4, Snappy and dumps first successful result
- **Protobuf support** — Decode protobuf payloads to JSON when a pre-compiled descriptor file (`.desc`) is configured; settings for descriptor path and default message type; supports nested message types
