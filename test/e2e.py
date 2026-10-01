#!/usr/bin/env python3
"""End-to-end tests for srv. Usage: test/e2e.py path/to/srv"""

import os
import socket
import subprocess
import sys
import tempfile
import time

SERVER = os.path.abspath(sys.argv[1])
results = []


def test(function):
    results.append(function)
    return function


class Server:
    def __init__(self, directory, *arguments):
        self.port = free_port()
        self.process = subprocess.Popen(
            [SERVER, "-p", str(self.port), *arguments],
            cwd=directory, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        wait_until(lambda: can_connect(self.port), 5)

    def connect(self):
        return socket.create_connection(("127.0.0.1", self.port))

    def request(self, raw):
        with self.connect() as connection:
            connection.sendall(raw)
            return read_all(connection)

    def get(self, target):
        return parse(self.request(f"GET {target} HTTP/1.1\r\nHost: test\r\n\r\n".encode("latin-1")))

    def is_running(self):
        return self.process.poll() is None

    def stop(self):
        if self.is_running():
            self.process.kill()
        self.process.wait()

    def __enter__(self):
        return self

    def __exit__(self, *exception):
        self.stop()


class Response:
    def __init__(self, status, headers, body):
        self.status = status
        self.headers = headers
        self.body = body


def parse(raw):
    head, _, body = raw.partition(b"\r\n\r\n")
    lines = head.decode("latin-1").split("\r\n")
    headers = dict(line.split(": ", 1) for line in lines[1:] if ": " in line)
    return Response(int(lines[0].split(" ")[1]), headers, body)


def read_all(connection):
    chunks = []
    chunk = connection.recv(65536)
    while chunk:
        chunks.append(chunk)
        chunk = connection.recv(65536)
    return b"".join(chunks)


def free_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def can_connect(port, host="127.0.0.1"):
    try:
        socket.create_connection((host, port), timeout=1).close()
        return True
    except OSError:
        return False


def wait_until(condition, seconds):
    deadline = time.monotonic() + seconds
    while not condition() and time.monotonic() < deadline:
        time.sleep(0.1)
    return condition()


def expect(actual, expected, what):
    if actual != expected:
        raise AssertionError(f"{what}: expected {expected!r}, got {actual!r}")


def served_directory():
    directory = tempfile.mkdtemp()
    with open(os.path.join(directory, "notes.txt"), "w") as file:
        file.write("<script>alert(1)</script>\n")
    os.mkdir(os.path.join(directory, "docs"))
    with open(os.path.join(directory, "docs", "index.html"), "w") as file:
        file.write("<h1>docs</h1>\n")
    os.mkfifo(os.path.join(directory, "pipe"))
    os.symlink("/dev/zero", os.path.join(directory, "zero"))
    return directory


@test
def serves_files_and_directories(directory):
    with Server(directory) as server:
        expect(server.get("/notes.txt").status, 200, "file")
        expect(server.get("/notes.txt").headers["Content-Type"], "text/plain; charset=utf-8", "content type")
        expect(server.get("/docs/").body, b"<h1>docs</h1>\n", "index.html")
        expect(b"notes.txt" in server.get("/").body, True, "listing")


@test
def listens_only_on_localhost_by_default(directory):
    with Server(directory) as server:
        expect(can_connect(server.port), True, "127.0.0.1")
        if has_ipv6():
            expect(can_connect(server.port, "::1"), False, "::1")


@test
def listens_on_every_address_when_bound_to_the_wildcard(directory):
    with Server(directory, "-b", "::") as server:
        expect(can_connect(server.port), True, "127.0.0.1")
        if has_ipv6():
            expect(can_connect(server.port, "::1"), True, "::1")


def has_ipv6():
    try:
        with socket.socket(socket.AF_INET6) as probe:
            probe.bind(("::1", 0))
        return True
    except OSError:
        return False


@test
def refuses_fifos_and_devices(directory):
    with Server(directory) as server:
        expect(server.get("/pipe").status, 404, "FIFO")
        expect(server.get("/zero").status, 404, "device")


@test
def rejects_nul_bytes_in_paths(directory):
    with Server(directory) as server:
        expect(server.get("/notes.txt%00.html").status, 400, "%00")
        expect(server.get("/notes.txt%-0.html").status, 404, "%-0")


@test
def rejects_control_characters(directory):
    with Server(directory) as server:
        response = parse(server.request(b"GET /docs?\nX-Injected:yes HTTP/1.1\r\n\r\n"))
        expect(response.status, 400, "line feed in target")
        expect("X-Injected" in response.headers, False, "injected header")


@test
def collapses_leading_slashes_in_redirects(directory):
    with Server(directory) as server:
        response = server.get("//docs?a=1")
        expect(response.status, 301, "status")
        expect(response.headers["Location"], "/docs/?a=1", "location")


@test
def stops_when_idle(directory):
    with Server(directory, "-i", "2") as server:
        server.get("/pipe")
        expect(wait_until(lambda: not server.is_running(), 6), True, "stopped within 6 seconds")


@test
def drops_clients_that_send_the_head_slowly(directory):
    with Server(directory) as server, server.connect() as connection:
        start = time.monotonic()
        connection.settimeout(1)
        closed = False
        while not closed and time.monotonic() - start < 20:
            try:
                connection.send(b"a")
                closed = connection.recv(1024) == b""
            except socket.timeout:
                pass
            except OSError:
                closed = True
        expect(closed, True, "connection closed")
        expect(time.monotonic() - start < 15, True, "closed within 15 seconds")


@test
def sends_no_more_than_the_content_length(directory):
    path = os.path.join(directory, "grow")
    with open(path, "wb") as file:
        file.write(b"a" * 1_000_000)
    with Server(directory, "-t", "8m") as server, server.connect() as connection:
        connection.sendall(b"GET /grow HTTP/1.1\r\n\r\n")
        time.sleep(0.3)
        with open(path, "ab") as file:
            file.write(b"b" * 1_000_000)
        response = parse(read_all(connection))
        expect(len(response.body), int(response.headers["Content-Length"]), "body length")


@test
def drops_clients_that_stop_reading(directory):
    with open(os.path.join(directory, "big"), "wb") as file:
        file.truncate(200_000_000)
    with Server(directory, "-i", "1") as server:
        connection = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        connection.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
        connection.connect(("127.0.0.1", server.port))
        connection.sendall(b"GET /big HTTP/1.1\r\n\r\n")
        expect(wait_until(lambda: not server.is_running(), 180), True, "stopped within 180 seconds")
        connection.close()


@test
def limits_concurrent_connections(directory):
    with Server(directory) as server:
        idle = [server.connect() for _ in range(130)]
        time.sleep(0.5)
        expect(status_or_reset(server, "/notes.txt") in (503, "reset"), True, "refused beyond the limit")
        for connection in idle:
            connection.close()
        expect(wait_until(lambda: server.get("/notes.txt").status == 200, 5), True, "after closing")


def status_or_reset(server, target):
    try:
        return server.get(target).status
    except ConnectionResetError:
        return "reset"


@test
def runs_commands_with_the_request_in_the_environment(directory):
    script = 'echo "$SRV_METHOD $SRV_PATH $SRV_QUERY"'
    with Server(directory, "-c", "text/x-test", "-x", "sh", "-c", script) as server:
        response = server.get("/a%20b?x=1")
        expect(response.headers["Content-Type"], "text/x-test", "content type")
        expect(response.body, b"GET /a b x=1\n", "output")


@test
def stops_commands_when_the_client_disconnects(directory):
    pid_file = os.path.join(directory, "command.pid")
    script = f'echo $$ > {pid_file}; echo started; exec sleep 30'
    with Server(directory, "-x", "sh", "-c", script) as server:
        connection = server.connect()
        connection.sendall(b"GET / HTTP/1.1\r\n\r\n")
        received = b""
        while b"started" not in received:
            received += connection.recv(4096)
        connection.close()
        with open(pid_file) as file:
            pid = int(file.read())
        expect(wait_until(lambda: not process_exists(pid), 5), True, "command stopped")


def process_exists(pid):
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False


def main():
    failures = 0
    for function in results:
        start = time.monotonic()
        try:
            function(served_directory())
            outcome = "ok"
        except Exception as exception:
            failures += 1
            outcome = f"FAILED: {exception}"
        print(f"{function.__name__} ({time.monotonic() - start:.1f}s) {outcome}", flush=True)
    print(f"{len(results) - failures} passed, {failures} failed")
    sys.exit(1 if failures else 0)


main()
