# srv

Serve a directory, or the output of a command, over HTTP. A small, dependency-free native take on `python3 -m http.server`, written in Kotlin/Native.

Runs on:

- macOS 11 (Big Sur) or later, Apple Silicon or Intel
- Linux on x86_64 or arm64 with glibc 2.17 or later, which covers practically every distribution from the last decade (Debian, Ubuntu, Fedora, RHEL/CentOS 7+, Arch and others). On Alpine Linux, which uses musl, install glibc compatibility first: `apk add gcompat libgcc`

## Install

### Homebrew

```sh
brew install rognlien/tap/srv
```

### Install script

```sh
curl -fsSL https://raw.githubusercontent.com/rognlien/srv/main/install.sh | sh
```

The script works on macOS and Linux. It downloads the latest release for your system, verifies its SHA-256 checksum and installs `srv` into `~/.local/bin` and the man page into `~/.local/share/man/man1`. No `sudo` is needed. Set `SRV_INSTALL_DIR` to install somewhere else, or `SRV_VERSION` (such as `v0.3.1`) to install a specific release:

```sh
curl -fsSL https://raw.githubusercontent.com/rognlien/srv/main/install.sh | SRV_INSTALL_DIR=/usr/local/bin sh
```

### Manual download

Download the archive for your system from [Releases](https://github.com/rognlien/srv/releases), extract it and move `srv` to a directory on your `PATH`:

| System | Archive |
|---|---|
| macOS, Apple Silicon | `srv-<version>-aarch64-apple-darwin.tar.gz` |
| macOS, Intel | `srv-<version>-x86_64-apple-darwin.tar.gz` |
| Linux, x86_64 | `srv-<version>-x86_64-unknown-linux-gnu.tar.gz` |
| Linux, arm64 | `srv-<version>-aarch64-unknown-linux-gnu.tar.gz` |

On macOS, downloading with a browser marks the file as quarantined, so remove that mark before the first run:

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
srv public     # serves the public directory
srv -p 9000    # serves on port 9000
srv -i 10m     # stops after 10 minutes without requests
srv -t 3g      # simulates a slow connection (56k, edge, 3g, 4g or a rate like 500k)
srv --help     # shows usage
srv --version  # shows the version
man srv        # shows the manual
```

- Serves `index.html` for directories that have one, otherwise a directory listing.
- With `-c`, runs a command for every request and streams its output:

  ```sh
  srv -c git log --oneline -20     # the latest commits
  srv -c tail -f app.log           # a log file, live
  srv -c sh -c 'ps aux | grep java'
  ```

  Everything after `-c` belongs to the command. The command gets the request in `SRV_METHOD`, `SRV_PATH`, `SRV_QUERY` and `SRV_CLIENT`, and is stopped when the client disconnects. Use `--content-type text/html` for commands that produce HTML.
- Handles each connection on its own thread.
- Logs each request in the same format as Python's `http.server`.

## Build

On macOS, all four targets can be built, since Kotlin/Native cross-compiles to Linux. On Linux, only the Linux targets can be built.

```sh
./gradlew linkReleaseExecutableMacosArm64    # or MacosX64, LinuxX64, LinuxArm64
./build/bin/macosArm64/releaseExecutable/srv.kexe
```

## Release

1. Push a tag such as `v0.4.0`. The release workflow builds arm64 and x86_64 binaries for macOS and Linux and attaches them, together with the man page and license, to a GitHub release. The install script picks up the new release immediately.
2. Update the version in the URLs and the checksums from `checksums.txt` in `Formula/srv.rb` in [rognlien/homebrew-tap](https://github.com/rognlien/homebrew-tap). Homebrew users get the new version on their next `brew upgrade`.

## License

MIT. See [LICENSE](LICENSE).
