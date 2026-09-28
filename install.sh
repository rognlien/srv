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

detect_platform() {
    case "$(uname -s)" in
        Darwin) echo "macos-$(detect_macos_architecture)" ;;
        Linux) echo "linux-$(detect_linux_architecture)" ;;
        *) fail "unsupported operating system $(uname -s)" ;;
    esac
}

detect_macos_architecture() {
    if [ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" = "1" ]; then
        echo "arm64"
    else
        echo "x86_64"
    fi
}

detect_linux_architecture() {
    case "$(uname -m)" in
        x86_64 | amd64) echo "x86_64" ;;
        aarch64 | arm64) echo "arm64" ;;
        *) fail "unsupported architecture $(uname -m)" ;;
    esac
}

verify_checksum() {
    if command -v sha256sum >/dev/null; then
        sha256sum -c - >/dev/null
    else
        shasum -a 256 -c - >/dev/null
    fi
}

latest_version() {
    curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/$REPOSITORY/releases/latest" | sed 's|.*/tag/||'
}

warn_if_not_on_path() {
    case ":$PATH:" in
        *":$1:"*) ;;
        *) printf '\n%s is not on your PATH. Add this line to your shell profile (such as ~/.zshrc or ~/.bashrc):\n  export PATH="%s:$PATH"\n' "$1" "$1" ;;
    esac
}

main() {
    command -v curl >/dev/null || fail "curl is required"

    install_dir="${SRV_INSTALL_DIR:-$HOME/.local/bin}"
    man_dir="$(dirname "$install_dir")/share/man/man1"
    platform="$(detect_platform)"
    version="${SRV_VERSION:-$(latest_version)}"
    case "$version" in v*) ;; *) fail "could not determine the latest version" ;; esac

    archive="srv-$version-$platform.tar.gz"
    base_url="https://github.com/$REPOSITORY/releases/download/$version"
    temporary_dir="$(mktemp -d)"
    trap 'rm -rf "$temporary_dir"' EXIT

    echo "Downloading srv $version for $platform..."
    curl -fsSL "$base_url/$archive" -o "$temporary_dir/$archive" || fail "download failed: $base_url/$archive"
    curl -fsSL "$base_url/checksums.txt" -o "$temporary_dir/checksums.txt" || fail "download failed: $base_url/checksums.txt"
    (cd "$temporary_dir" && grep " $archive\$" checksums.txt | verify_checksum) || fail "checksum verification failed"

    tar -xzf "$temporary_dir/$archive" -C "$temporary_dir"
    mkdir -p "$install_dir" "$man_dir"
    install -m 755 "$temporary_dir/srv" "$install_dir/srv"
    install -m 644 "$temporary_dir/srv.1" "$man_dir/srv.1"

    installed_version="$("$install_dir/srv" --version 2>/dev/null)" ||
        fail "installed $install_dir/srv, but it cannot run. On Alpine Linux, install glibc compatibility first: apk add gcompat libgcc"
    echo "Installed $installed_version to $install_dir/srv"
    warn_if_not_on_path "$install_dir"
}

main "$@"
