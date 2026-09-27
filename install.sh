#!/bin/sh
# Installs srv from the latest GitHub release:
#   curl -fsSL https://raw.githubusercontent.com/rognlien/srv/main/install.sh | sh
#
# SRV_INSTALL_DIR  directory for the binary (default: ~/.local/bin)
# SRV_VERSION      release to install, such as v0.3.1 (default: latest)

set -eu

REPOSITORY="rognlien/srv"

fail() {
    echo "srv installer: $*" >&2
    exit 1
}

detect_architecture() {
    if [ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" = "1" ]; then
        echo "arm64"
    elif [ "$(uname -m)" = "x86_64" ]; then
        echo "x86_64"
    else
        fail "unsupported architecture $(uname -m)"
    fi
}

latest_version() {
    curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/$REPOSITORY/releases/latest" | sed 's|.*/tag/||'
}

warn_if_not_on_path() {
    case ":$PATH:" in
        *":$1:"*) ;;
        *) printf '\n%s is not on your PATH. Add it with:\n  echo '\''export PATH="%s:$PATH"'\'' >> ~/.zshrc\n' "$1" "$1" ;;
    esac
}

main() {
    [ "$(uname -s)" = "Darwin" ] || fail "only macOS is supported"
    command -v curl >/dev/null || fail "curl is required"

    install_dir="${SRV_INSTALL_DIR:-$HOME/.local/bin}"
    man_dir="$(dirname "$install_dir")/share/man/man1"
    architecture="$(detect_architecture)"
    version="${SRV_VERSION:-$(latest_version)}"
    case "$version" in v*) ;; *) fail "could not determine the latest version" ;; esac

    archive="srv-$version-macos-$architecture.tar.gz"
    base_url="https://github.com/$REPOSITORY/releases/download/$version"
    temporary_dir="$(mktemp -d)"
    trap 'rm -rf "$temporary_dir"' EXIT

    echo "Downloading srv $version for $architecture..."
    curl -fsSL "$base_url/$archive" -o "$temporary_dir/$archive" || fail "download failed: $base_url/$archive"
    curl -fsSL "$base_url/checksums.txt" -o "$temporary_dir/checksums.txt" || fail "download failed: $base_url/checksums.txt"
    (cd "$temporary_dir" && grep " $archive\$" checksums.txt | shasum -a 256 -c -s -) || fail "checksum verification failed"

    tar -xzf "$temporary_dir/$archive" -C "$temporary_dir"
    mkdir -p "$install_dir" "$man_dir"
    install -m 755 "$temporary_dir/srv" "$install_dir/srv"
    install -m 644 "$temporary_dir/srv.1" "$man_dir/srv.1"

    echo "Installed $("$install_dir/srv" --version) to $install_dir/srv"
    warn_if_not_on_path "$install_dir"
}

main "$@"
