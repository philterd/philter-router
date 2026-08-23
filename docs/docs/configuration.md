# Configuration

Philter Router reads a single YAML file, passed as the first argument:

```
java -jar philter-router.jar router.yaml
```

Configuration is validated on startup and is fail-closed: any problem (a missing `default`, an unknown
engine or classifier, an invalid port) prevents the router from starting rather than letting it run with
a gap. At least one entry point must be enabled: the HTTP API (`server`), folder watching
(`watch.locations`), or both.

## Blocks

### `server`

Enables the [HTTP API](http-api.md).

```yaml
server:
  enabled: true
  port: 8080
```

### `watch.locations`

The directories to watch (see [Folder Watching](folder-watching.md)). Each location sets its own watch
mechanism and drainage directories.

```yaml
watch:
  locations:
    - path: "/data/intake"
      mode: poll            # poll (default; required on network shares) or notify (local only)
      pollIntervalMs: 5000
      stableForMs: 2000
      recursive: true
      output: "/data/redacted"
      done:   "/data/intake/.done"
      error:  "/data/intake/.error"
```

### `engines`

Named Philter engines, referenced by routes and the default.

```yaml
engines:
  philter1: { url: "http://philter1:8080" }
  philter2: { url: "http://philter2:8080", context: "batch", readTimeoutMs: 300000 }
```

`apiKey` and `context` are optional. Do not embed secrets in the policy; resolve an API key from the
environment where possible.

An engine has one `url`. To balance across several identical Philter replicas, front them with a load
balancer and point the engine at it; see
[Multiple Philter engines](deployment.md#multiple-philter-engines). Define separate engines for engines
that differ in capability or policy, not for interchangeable replicas.

Timeouts for the forwarded request are per engine (all in milliseconds):

| Field | Default | Purpose |
| --- | --- | --- |
| `connectTimeoutMs` | 10000 | TCP connect. |
| `writeTimeoutMs` | 60000 | Inactivity while uploading the file body. |
| `readTimeoutMs` | 120000 | Inactivity while awaiting Philter's response. |
| `callTimeoutMs` | 0 (off) | Hard end-to-end ceiling for the whole call. |

Philter sends nothing until redaction finishes, so `readTimeoutMs` must cover the full processing time of
the **slowest** document routed to that engine, not the average. Raise it for large files. `callTimeoutMs`
is an optional upper bound so a stuck engine cannot hold a router thread indefinitely; a value of `0`
disables it. If a proxy sits in front of the router, raise its read timeout to match.

When a Philter engine serves HTTPS with a self-signed certificate, the forwarded request fails the default
TLS check. Two per-engine options handle it:

| Field | Default | Purpose |
| --- | --- | --- |
| `caCertPath` | (none) | Path to a PEM certificate (or its issuing CA) to trust. Verification stays on: the engine is still authenticated. |
| `insecureSkipVerify` | `false` | Disable TLS verification entirely. |

```yaml
engines:
  philter1: { url: "https://philter1:8443", caCertPath: /etc/philter-router/philter1.crt }
```

Prefer `caCertPath`: it trusts the specific self-signed certificate while keeping the connection
authenticated and resistant to interception. `insecureSkipVerify` disables authentication and exposes the
forwarded document and API key to interception, so use it only on a trusted network or for testing; the
router logs a warning at startup when it is on. If both are set, `insecureSkipVerify` wins.

### `classifiers`

Named local LLM classifiers. Each runs one prompt that returns a single label from `labels`, at most
once per file (cached). The `{{text}}` placeholder is replaced with an excerpt of the extracted text.

```yaml
classifiers:
  doc-type:
    type: ollama
    endpoint: "http://ollama:11434"
    model: llama3.1
    timeoutMs: 2000
    labels: [medical, financial, legal, general]
    prompt: |
      Classify this document into exactly one of: medical, financial, legal, general.
      Reply with only the label.
      {{text}}
```

The endpoint must be local: a classifier reads un-redacted document text, which must not leave your
boundary. On timeout, failure, or an unrecognized label, the router applies the default.

### `routes`

An ordered list, evaluated first match wins. Each route has a `match`, an optional `languages` list, and
an outcome (`engine` + `policy`).

```yaml
routes:
  - name: office-docs
    match: { extensions: [".xlsx", ".docx"] }
    languages: [eng, spa]
    engine: philter2
    policy: office-default

  - name: medical-records
    match: { classification: { classifier: doc-type, label: medical } }
    languages: [any]
    engine: philter1
    policy: hipaa
```

Match fields: `contentTypes`, `extensions`, `directories`, and `classification`
(`{ classifier: <name>, label: <label> }`). Within a route, all specified fields must match (AND);
within a single field, a list is any-of (OR). For OR across fields, use two routes.

`languages` is a list of allowed ISO 639-3 codes, AND-ed with the match. When omitted it defaults to
`[eng]`. Use `any` to accept all languages. See [Routing](routing.md).

### `default`

The mandatory catch-all outcome, applied when no route matches or when language detection or a
classifier cannot decide. The router refuses to start without it. It takes one of two forms.

Redact with a policy (point it at a policy that redacts, so any unmatched file is still redacted):

```yaml
default:
  engine: philter1
  policy: default
```

Or reject unmatched documents instead of redacting them with a generic policy:

```yaml
default:
  action: reject
```

With `action: reject` the router does not guess a policy for a file it has no route for. In the folder
watcher a rejected file is moved to the location's `error` directory and never written to `output`; over
the HTTP API the request returns `422 Unprocessable Entity` and nothing is sent to Philter. A rejecting
default must not set `engine` or `policy`. Either form upholds the guarantee that an unmatched file is
never emitted unredacted: it is either redacted by the default or refused.

## Examples

The [`examples/`](https://github.com/philterd/philter-router/tree/main/examples) directory has complete,
commented configurations:

- `minimal.yaml` - one watched directory, one policy.
- `folder-watching.yaml` - multiple locations, two engines, a classifier, and routes.
- `network-share.yaml` - `poll` on an SMB/NFS share, `notify` on a local disk.
- `http-api.yaml` - the HTTP API with classification routing.
- `watch-and-api.yaml` - the API and folder watching from one config.
- `classifier-routing.yaml` - routing by classification and by language.
