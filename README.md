# srv

Serve the current directory over HTTP. A small, dependency-free native take on `python3 -m http.server`, written in Kotlin/Native.

## Install

```sh
brew install rognlien/tap/srv
```

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

Push a tag such as `v0.2.0`. The release workflow builds arm64 and x86_64 binaries and attaches them, together with the man page, to a GitHub release. Then update the version, URLs and checksums in `Formula/srv.rb` in [rognlien/homebrew-tap](https://github.com/rognlien/homebrew-tap).

## License

MIT. See [LICENSE](LICENSE).
