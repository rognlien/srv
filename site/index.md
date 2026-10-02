---
title: srv
tagline: Serve a directory, or the output of a command, over HTTP. A small, dependency-free native take on `python3 -m http.server`.
description: Serve a directory, or the output of a command, over HTTP. A small, dependency-free native take on python3 -m http.server for macOS and Linux.
icon: icon.svg
install: brew install rognlien/tap/srv
requirements: macOS 11 or later and Linux with glibc 2.17 or later, on arm64 and x86_64. [Source on GitHub](https://github.com/rognlien/srv)
license: MIT License
links:
  - label: GitHub
    url: https://github.com/rognlien/srv
  - label: Releases
    url: https://github.com/rognlien/srv/releases
  - label: Issues
    url: https://github.com/rognlien/srv/issues
---

## Usage

```sh
srv            # serves the current directory on port 8000
srv public     # serves the public directory
srv -p 9000    # serves on port 9000
srv -b 0.0.0.0 # lets other machines on the network connect
srv -i 10m     # stops after 10 minutes without requests
srv -t 3g      # simulates a slow connection
man srv        # shows the manual
```

## What it does

- Serves `index.html` for directories that have one, otherwise a directory listing.
- Listens on localhost by default, or on any IPv4 or IPv6 address with `-b`, handles each connection on its own thread, and sends files with `sendfile`.
- Simulates slow networks with presets for `56k`, `edge`, `3g` and `4g`, or a rate such as `500k`.
- Stops by itself after a period without requests, with `-i`.
- Logs each request in the same format as Python's `http.server`.

## Serve the output of a command

With `-x`, srv runs a command for every request and streams its output while it runs:

```sh
srv -x git log --oneline -20     # the latest commits
srv -x tail -f app.log           # a log file, live
srv -x sh -c 'ps aux | grep java'
srv -c application/json -x ./report.sh
```

Everything after `-x` belongs to the command. The command gets the request in `SRV_METHOD`, `SRV_PATH`, `SRV_QUERY` and `SRV_CLIENT`, and is stopped when the client disconnects. Set the content type of the output with `-c`, such as `application/json` or `application/xml`. The default is `text/plain`.

## Install

### Homebrew

```sh
brew install rognlien/tap/srv
```

### Install script

Works on macOS and Linux. Downloads the latest release for your system, verifies its SHA-256 checksum and installs `srv` into `~/.local/bin` and the man page into `~/.local/share/man/man1`. No `sudo` is needed.

```sh
curl -fsSL https://raw.githubusercontent.com/rognlien/srv/main/install.sh | sh
```

Set `SRV_INSTALL_DIR` to install somewhere else, or `SRV_VERSION` (such as `v0.7.0`) to install a specific release. Run the same command again to update.

### Manual download

Download the archive for your system from [Releases](https://github.com/rognlien/srv/releases/latest), extract it and move `srv` to a directory on your `PATH`.

| System | Archive |
| --- | --- |
| macOS, Apple Silicon | `aarch64-apple-darwin` |
| macOS, Intel | `x86_64-apple-darwin` |
| Linux, x86_64 | `x86_64-unknown-linux-gnu` |
| Linux, arm64 | `aarch64-unknown-linux-gnu` |

On Alpine Linux, which uses musl, install glibc compatibility first with `apk add gcompat libgcc`.
