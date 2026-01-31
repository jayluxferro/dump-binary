# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2025-01-31

### Added

- WebSocket support: dump payload, selection, batch dump, binary diff from WebSocket message editor and table
- Content decoding: zstd (Content-Encoding: zstd)
- Chained decoding: base64→gzip and gzip→base64 (for double-encoded bodies)
- JSON/XML pretty-print option
- Binary diff: compare two selected items (hex view with diff markers)
- Custom magic bytes: user-defined hex:ext patterns in Settings
- MessagePack: dump as JSON when MessagePack detected
- CBOR: dump as JSON when CBOR detected

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
