# srv

Serve the current directory over HTTP. A small, dependency-free native take on `python3 -m http.server`, written in Kotlin/Native.

Requires macOS 11 (Big Sur) or later on Apple Silicon or Intel.

## Install

### Homebrew

```sh
brew install rognlien/tap/srv
```

### Install script

```sh
curl -fsSL https://raw.githubusercontent.com/rognlien/srv/main/install.sh | sh
```

The script downloads the latest release for your CPU, verifies its SHA-256 checksum and installs `srv` into `~/.local/bin` and the man page into `~/.local/share/man/man1`. No `sudo` is needed. Set `SRV_INSTALL_DIR` to install somewhere else, or `SRV_VERSION` (such as `v0.3.1`) to install a specific release:

```sh
curl -fsSL https://raw.githubusercontent.com/rognlien/srv/main/install.sh | SRV_INSTALL_DIR=/usr/local/bin sh
```

### Manual download

Download `srv-<version>-macos-arm64.tar.gz` (Apple Silicon) or `srv-<version>-macos-x86_64.tar.gz` (Intel) from [Releases](https://github.com/rognlien/srv/releases), extract it and move `srv` to a directory on your `PATH`. Downloading with a browser marks the file as quarantined, so remove that mark before the first run:

```sh
xattr -d com.apple.quarantine srv
```

## Update

- Homebrew: `brew upgrade srv`
- Install script: run the same command again.

## Uninstall

- Homebrew: `brew uninstall srv && brew untap rognlien/tap`
- Install script: `rm ~/.local/bin/srv ~/.local/share/man/man1/srv.1`

## Usage

```sh
srv            # serves the current directory on port 8000
srv 9000       # serves on port 9000
srv -i 10m     # stops after 10 minutes without requests
srv -t 3g      # simulates a slow connection (56k, edge, 3g, 4g or a rate like 500k)
srv --help     # shows usage
srv --version  # shows the version
man srv        # shows the manual
```

- Serves `index.html` for directories that have one, otherwise a directory listing.
- Handles each connection on its own thread.
- Logs each request in the same format as Python's `http.server`.

## Build

```sh
./gradlew linkReleaseExecutableMacosArm64
./build/bin/macosArm64/releaseExecutable/srv.kexe
```

## Release

1. Push a tag such as `v0.4.0`. The release workflow builds arm64 and x86_64 binaries and attaches them, together with the man page and license, to a GitHub release. The install script picks up the new release immediately.
2. Update the version in the URLs and the checksums from `checksums.txt` in `Formula/srv.rb` in [rognlien/homebrew-tap](https://github.com/rognlien/homebrew-tap). Homebrew users get the new version on their next `brew upgrade`.

## License

MIT. See [LICENSE](LICENSE).
