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

A multi-stage `Dockerfile` builds the jar and runs it on a JRE base image as a non-root user. The
configuration is mounted at `/config/router.yaml` and watched and output directories under `/data`.

```
docker build -t philterd/philter-router .
docker run --rm \
  -p 8080:8080 \
  -v "$PWD/config:/config:ro" \
  -v "$PWD/data:/data" \
  philterd/philter-router
```

`JAVA_OPTS` is passed through, for example `-e JAVA_OPTS=-Drouter.log.dir=/data/logs`.

### HTTPS

The image generates a self-signed certificate at build time and serves the API over HTTPS, so requests
use `https://` (for example `curl -k https://localhost:8080/api/health`). To use your own certificate,
mount a PKCS12 keystore and override the SSL settings, or disable HTTPS with `-e SSL_OPTS=`:

```
docker run --rm -p 8080:8080 \
  -e SSL_OPTS="-Dserver.ssl.enabled=true -Dserver.ssl.key-store=/config/keystore.p12 -Dserver.ssl.key-store-password=secret -Dserver.ssl.key-store-type=PKCS12 -Dserver.ssl.key-alias=myalias" \
  -v "$PWD/config:/config:ro" -v "$PWD/data:/data" \
  philterd/philter-router
```

## Docker Compose

`docker-compose.yml` runs the router with a local Ollama for classification. Provide
`./config/router.yaml` (see [Configuration](configuration.md)); in it, point the classifier endpoint at
`http://ollama:11434` and the engine URLs at your Philter engines.

```
docker compose up -d
docker compose exec ollama ollama pull llama3.1
curl -sS localhost:8080/api/health
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
