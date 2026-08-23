# Deployment

## Build

```
mvn package
```

This runs the tests and produces a runnable `target/philter-router.jar` (Java 25).

## Run

```
java -jar philter-router.jar /path/to/router.yaml
```

The router starts whatever the configuration enables: the HTTP API (`server`), folder watching
(`watch.locations`), or both. To also write rolling log files, set `router.log.dir`:

```
java -Drouter.log.dir=/var/log/philter-router -jar philter-router.jar router.yaml
```

On Windows the same jar runs as a Windows Service under a service wrapper; the HTTP API and folder
watching behave identically.

## Docker

The `Dockerfile` copies the prebuilt jar onto a JRE base image and runs it as a non-root user, so build
the jar first with `mvn package`. The configuration is mounted at `/config/router.yaml`, and the watched
and output directories under `/data`.

```
mvn package
docker build -t philterd/philter-router .
docker run --rm \
  -p 8080:8080 \
  -v "$PWD/config:/config:ro" \
  -v "$PWD/data:/data" \
  philterd/philter-router
```

`JAVA_OPTS` is passed through, for example `-e JAVA_OPTS=-Drouter.log.dir=/data/logs`.

### HTTPS

The container generates a self-signed certificate **at start**, not at build time, and serves the API over
HTTPS, so requests use `https://` (for example `curl -k https://localhost:8080/api/health`). Each container
generates its own keypair with a random password. Nothing is baked into the image: a certificate created at
build time would be identical in every copy of a published image, so anyone who pulled it would hold the
private key for every deployment running the default.

The practical consequences are that the certificate changes when the container is recreated, and that a
client pinning the certificate needs it re-pinned. To keep one certificate across restarts, mount a volume
at `/home/router/tls` and set `ROUTER_KEYSTORE_PASSWORD` so the existing keystore is reused; the router
fails to start if a keystore is present and that variable is missing, rather than silently generating a
second one. `ROUTER_KEYSTORE` overrides the keystore path.

The generated certificate carries `localhost`, `philter-router`, and `127.0.0.1` as subject alternative
names, so it can be verified against those names rather than only with `-k`.

To use your own certificate, mount a PKCS12 keystore and override the SSL settings, or disable HTTPS with
`-e SSL_OPTS=`:

```
docker run --rm -p 8080:8080 \
  -e SSL_OPTS="-Dserver.ssl.enabled=true -Dserver.ssl.key-store=/config/keystore.p12 -Dserver.ssl.key-store-password=secret -Dserver.ssl.key-store-type=PKCS12 -Dserver.ssl.key-alias=myalias" \
  -v "$PWD/config:/config:ro" -v "$PWD/data:/data" \
  philterd/philter-router
```

Setting `SSL_OPTS` to anything, including the empty string, hands TLS configuration entirely to you: the
entrypoint then generates no certificate and sets no SSL properties of its own.

## Docker Compose

`docker-compose.yml` runs the router. A sample `config/router.yaml` ships in the repo, so after building
the jar it comes up out of the box and reports healthy:

```
mvn package
docker compose up -d
curl -k https://localhost:8080/api/health
```

Edit `config/router.yaml` to point the engine URLs at your Philter engines (see
[Configuration](configuration.md)); the sample routes everything to the `default` policy. The router logs
to stdout by default (`docker compose logs`); uncomment the `JAVA_OPTS` line in the Compose file to also
write rolling files under `./data`.

A local Ollama for the LLM classifier is opt-in via the `ollama` profile, so a plain `up` starts only the
router. Start Ollama only when the config uses a classifier, and point the classifier endpoint at
`http://ollama:11434`:

```
docker compose --profile ollama up -d
docker compose --profile ollama exec ollama ollama pull llama3.1
```

The Philter engines the router forwards to run as their own services. Add them to the Compose file or
point the engine URLs at existing deployments.

## Scaling

The two entry points scale differently.

The **HTTP API is stateless**. Each request is independent: there is no shared or cross-request state,
configuration is read-only after startup, and authorization is per request. Run any number of instances
behind a load balancer with no session affinity. The routers forward to Philter, so Philter and the
classifier become the downstream capacity limit as instances are added.

The **folder watcher is single-instance per directory set**. Its processed-file ledger is in-memory and
per-process, so two watchers on the same directories redact each file twice and contend on the move to
the `done` directory. To scale watching, partition the directories across instances so no two watch the
same location, or run a single watcher (or the [batch client](batch.md)) that fans out to a pool of
stateless API instances.

### Multiple Philter engines

An engine names a single `url`. To run several identical Philter replicas for throughput or failover,
front them with a load balancer and point the engine at the balancer:

```yaml
engines:
  philter1: { url: "http://philter-lb:8080", readTimeoutMs: 300000 }
```

The router has no built-in engine pool by design. Balancing with health checks is what a load balancer
already does, and keeping it external leaves the retry policy in the operator's hands instead of fixing
it in the router. A minimal nginx front end:

```nginx
upstream philter {
    server philter-a:8080 max_fails=3 fail_timeout=30s;
    server philter-b:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 8080;

    location / {
        proxy_pass http://philter;
        proxy_next_upstream error http_502 http_503 http_504;
        proxy_read_timeout 300s;
        proxy_request_buffering off;
        client_max_body_size 0;
    }
}
```

Four settings matter for redaction traffic specifically:

`proxy_next_upstream` omits `timeout`. Philter sends nothing until redaction finishes, so a timeout does
not mean the replica failed to receive the document; it may still be working on it. Retrying on timeout
sends the same document to a second replica.

Redaction calls are `POST /api/filter`, and nginx does not pass a non-idempotent request to the next
server once it has been sent. Failover therefore covers connection-level failures, and `max_fails` /
`fail_timeout` take a failing replica out of rotation for subsequent requests. Adding `non_idempotent` to
`proxy_next_upstream` would also retry after the body was sent, resubmitting the document; set it only if
duplicate submission is acceptable.

`proxy_read_timeout` must cover the full processing time of the slowest document routed through it, the
same sizing rule as the engine's `readTimeoutMs`. Set the balancer's value at or above the engine's, or
the balancer will cut the connection first.

`client_max_body_size 0` removes nginx's 1 MB upload cap, and `proxy_request_buffering off` streams large
bodies through instead of spooling them to disk.

For active health checking rather than nginx's passive `max_fails`, Philter exposes `GET /api/health`.
HAProxy checks it directly:

```
backend philter
    option httpchk GET /api/health
    server philter-a philter-a:8080 check inter 10s
    server philter-b philter-b:8080 check inter 10s
```

Two related cases:

**Connection-level failover without a balancer.** If the engine hostname resolves to several addresses
(DNS round robin, a Kubernetes Service, Compose DNS), the router's HTTP client tries the next address when
a connection fails. That handles a replica that is down or unreachable, but nothing for one that is up and
returning errors or hanging, which is the case a health-checked balancer covers.

**Separate named engines are not a pool.** Define distinct engines when they differ in capability or
policy, such as a Java engine and a .NET engine for `docx`/`xlsx`/OCR, and route to them by file
attributes. Interchangeable replicas of the same engine belong behind one balancer under one name.

If the balancer terminates TLS with a self-signed certificate, `caCertPath` and `insecureSkipVerify`
apply to the balancer's certificate rather than the replicas'. See
[Configuration](configuration.md).
