# Dump Binary

A Burp Suite extension that lets you dump the raw binary body of HTTP requests or responses to a file. Right-click a request or response, choose **Dump Binary**, and save with smart filenames derived from host, path, and Content-Type.

## Features

- **Context menu** — Right-click in the message editor (Repeater, HTTP tab), WebSocket editor, or on selected rows (Proxy history, Site map, WebSocket table).
- **Keyboard shortcut** — **Ctrl+Shift+D** (Windows/Linux) or **⌘+Shift+D** (macOS) in the message editor or Proxy history.
- **Context-aware menu** — When in the message editor, shows only the relevant option (request or response).
- **Request and response** — Dump body only or full raw message (headers + body).
- **Dump selection** — When text is selected in the message editor, dump only the selected bytes.
- **Smart filenames** — Derived from host, path, and Content-Disposition; falls back to magic-byte detection.
- **Content-Type aware** — Suggests file extensions from Content-Type; falls back to magic-byte detection for unknown types.
- **Content decoding** — Automatically decompresses gzip-, deflate-, brotli-, and zstd-encoded responses before saving.
- **Copy to clipboard** — Copy body as base64 or hex.
- **Base64 decode** — When body decodes as base64, option to dump the decoded binary.
- **Chained decode** — base64→gzip or gzip→base64 for double-encoded bodies.
- **JSON/XML pretty-print** — Format JSON and XML before saving.
- **MessagePack/CBOR** — Dump as JSON when binary formats detected.
- **Binary diff** — Compare two selected items (hex view with diff markers) for HTTP or WebSocket.
- **Custom magic bytes** — User-defined hex:ext patterns in Settings.
- **Batch dump** — Dump all selected items at once when multiple rows are selected (HTTP or WebSocket).
- **Multipart extraction** — Extract individual parts from multipart bodies to separate files.
- **Settings panel** — Default save directory, auto-save, open after save, overwrite confirmation, success notifications.
- **Background file I/O** — Large saves run off the EDT to keep Burp responsive.
- **Extended MIME map** — Supports fonts (woff, woff2), JavaScript, CSS, HTML, WASM, and more.

## Requirements

- Java 17 or later
- Burp Suite with Montoya API support

## Building

```bash
./gradlew jar
```

The JAR is written to `build/libs/dump-binary.jar`.

## Testing

```bash
./gradlew test
```

## Installation

1. In Burp, go to **Extensions > Installed**.
2. Click **Add** and select `dump-binary.jar`.
3. Click **Next** to load the extension.

To reload after changes: hold **Ctrl** (Windows/Linux) or **⌘** (macOS) and click the **Loaded** checkbox.

## Usage

1. Right-click a request, response, or WebSocket message:
   - **In the message editor** — Click inside the request or response panel (Repeater, HTTP tab, etc.).
   - **In the WebSocket editor** — Click inside a WebSocket message in the WebSockets tab.
   - **In a table** — Select a row in Proxy history, Site map, or WebSocket message table, then right-click.
2. Choose **Dump Binary** → **Dump request body**, **Dump response body**, **Dump full message**, **Copy to clipboard** (base64/hex), **Dump pretty-printed** (JSON/XML), **Dump chained decode**, **Dump MessagePack/CBOR as JSON**, or **Extract multipart parts** (when applicable).
3. For a selection: select text in the message editor, then right-click → **Dump Binary** → **Dump selection**.
4. For binary diff: select exactly two rows (HTTP or WebSocket), then right-click → **Dump Binary** → **Binary diff (compare 2 selected)**.
5. For WebSocket: right-click a WebSocket message → **Dump Binary** → **Dump WebSocket payload**, **Dump selection**, **Copy to clipboard**, chained decode, MessagePack/CBOR as JSON, etc.
6. Or press **Ctrl+Shift+D** (Windows/Linux) or **⌘+Shift+D** (macOS) to dump the current HTTP request or response.
7. Pick a save location (or use the default directory if configured).
8. The raw body or WebSocket payload is written to the selected file.

## Settings

Go to **Settings > Dump Binary** to configure:

- **Default save directory** — Pre-fill the file chooser or auto-save when "Use default directory" is enabled.
- **Use default directory** — Skip the file chooser and save directly to the default directory.
- **Open after save** — Open the saved file in the default application.
- **Show success notification** — Popup after a successful save.
- **Confirm before overwriting** — Ask before overwriting an existing file.
- **Custom magic bytes** — Hex:ext patterns (one per line), e.g. `89 50 4E 47:png` for custom file type detection.

## Content Encoding

The extension automatically decompresses responses with these `Content-Encoding` values before saving:

| Content-Encoding | Description |
|------------------|-------------|
| gzip | Gzip compression |
| deflate | Deflate (zlib or raw) compression |
| br | Brotli compression |
| zstd | Zstandard compression |

## Supported Content Types

The extension maps MIME types to file extensions and uses magic-byte detection when Content-Type is missing or `application/octet-stream`:

| Content-Type | Extension |
|--------------|-----------|
| image/*, application/pdf, application/zip, etc. | .png, .jpg, .pdf, .zip, etc. |
| font/woff, font/woff2 | .woff, .woff2 |
| application/javascript, text/css, text/html | .js, .css, .html |
| application/wasm | .wasm |
| application/octet-stream, unknown | Magic-byte detection or .bin |

## Project Structure

```
src/main/java/
├── Extension.java              # Entry point, registers settings, context menu, hotkey
├── DumpBinaryContextMenu.java # Context menu, dump logic, content decoding, multipart
├── DumpBinarySettings.java    # Settings panel (default dir, open after save, etc.)
├── DumpBinaryUtils.java       # MIME mapping, magic-byte detection, Content-Disposition
├── DumpBinaryVersion.java     # Version constant
└── DumpBinaryHotKeyHandler.java # Ctrl+Shift+D handler
```

## Resources

- [Montoya API JavaDoc](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html)
- [Burp extension documentation](https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating)
- [PortSwigger Discord](https://discord.com/channels/1159124119074381945/1164175825474686996)
