# Dump Binary

A Burp Suite extension that lets you dump the raw binary body of HTTP requests or responses to a file. Right-click a request or response, choose **Dump Binary**, and save the body with a filename derived from the Content-Type header.

## Features

- **Context menu integration** — Right-click in the message editor (Repeater, HTTP tab) or on a selected row (Proxy history, Site map) to access **Dump Binary**.
- **Request and response** — Dump either the request body or the response body.
- **Content-Type aware** — Suggests file extensions based on Content-Type (e.g. `image/png` → `.png`, `application/pdf` → `.pdf`).
- **Raw binary** — Writes the exact bytes to disk with no decoding or transformation.

## Requirements

- Java 17 or later
- Burp Suite with Montoya API support

## Building

```bash
./gradlew jar
```

The JAR is written to `build/libs/dump-binary.jar`.

## Installation

1. In Burp, go to **Extensions > Installed**.
2. Click **Add** and select `dump-binary.jar`.
3. Click **Next** to load the extension.

To reload after changes: hold **Ctrl** (Windows/Linux) or **⌘** (macOS) and click the **Loaded** checkbox.

## Usage

1. Right-click a request or response:
   - **In the message editor** — Click inside the request or response panel (Repeater, HTTP tab, etc.).
   - **In a table** — Select a row in Proxy history or Site map, then right-click.
2. Choose **Dump Binary** → **Dump request body** or **Dump response body**.
3. Pick a save location in the file chooser (filename is pre-filled from Content-Type).
4. The raw body is written to the selected file.

## Supported Content Types

The extension maps common MIME types to file extensions:

| Content-Type | Extension |
|--------------|-----------|
| image/png, image/jpeg, image/gif, etc. | .png, .jpg, .gif, etc. |
| application/pdf | .pdf |
| application/zip | .zip |
| application/json | .json |
| application/xml, text/xml | .xml |
| application/octet-stream, unknown | .bin |

## Project Structure

```
src/main/java/
├── Extension.java           # Entry point, registers context menu
└── DumpBinaryContextMenu.java  # Context menu provider, file save logic
```

## Resources

- [Montoya API JavaDoc](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html)
- [Burp extension documentation](https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating)
- [PortSwigger Discord](https://discord.com/channels/1159124119074381945/1164175825474686996)
