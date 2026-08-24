# HTTP API

Enable the API with a `server` block:

```yaml
server:
  enabled: true
  port: 8080
```

The API mirrors Philter's filter contract, so the Philter SDK works against it unchanged. The router is
send-only: it redacts and returns the result. The policy is auto-selected by the [routes](routing.md)
unless `?p=` overrides it; the engine is always chosen by routing.

## Endpoints

| Endpoint | Description |
| --- | --- |
| `GET /api/health` | Liveness and version. Returns `{"status":"UP","applicationVersion":"1.0.0"}`. |
| `POST /api/filter` | Redact and return synchronously. Send a file with `?filename=` (the body is the raw bytes); otherwise the body is text. The applied policy is in the `X-Philter-Policy` header and the document id in `x-document-id`. |

When the configuration's `default` is `action: reject`, a document that matches no route is refused with
`422 Unprocessable Entity` and nothing is sent to Philter.

`applicationVersion` is the Maven project version, recorded at build time in
`META-INF/build-info.properties` by `mvn package`. Running from classes built outside Maven, such as in an
IDE, leaves that file absent and the version reports as `unknown`.

Only the filter surface is implemented. Retrieval by document id, policy management, contexts, and the
ledger are not proxied.

The API is a Spring Boot application. Spring Boot Actuator adds operational endpoints alongside the
Philter-compatible ones: `GET /actuator/health` (with liveness and readiness groups) and
`GET /actuator/prometheus` for metrics.

## OpenAPI specification

The API is described by an OpenAPI 3 specification served at `GET /openapi.yaml` (for example
`http://localhost:8080/openapi.yaml`). Open it in any OpenAPI viewer, such as
[Swagger Editor](https://editor.swagger.io/), or import it into Postman or an SDK generator.

## Parameters

| Name | Where | Purpose |
| --- | --- | --- |
| `p` | query | Override the selected policy. |
| `c` | query | Philter context. |
| `filename` | query | When present, the body is treated as a file (used for extension routing and passed to Philter). |
| `X-Source-Directory` | header | A source-directory hint for directory routing (an upload has no source directory). |
| `X-Classification` | header | Pre-computed classifier labels, as comma-separated `classifier=label` pairs (for example `doc-type=medical`). The router uses the supplied label instead of running that classifier. |
| `Authorization` | header | Forwarded to Philter for this request. When absent, the engine's configured API key (if any) is used. |

Content type is derived from the bytes via Tika. A caller that has already classified a document can
pass the result in `X-Classification` to skip the local LLM for that classifier.

## Authentication to Philter

If a Philter engine requires an API key, either configure it per engine (`engines.<name>.apiKey`) or send
an `Authorization` header with the request; the router forwards that header to Philter. A per-request
`Authorization` header takes precedence over the configured key.

## Examples

Redact text:

```
curl -X POST --data-binary 'His SSN is 123-45-6789.' localhost:8080/api/filter
```

Redact a file:

```
curl -X POST --data-binary @report.docx \
  'localhost:8080/api/filter?filename=report.docx' -o redacted.docx
```
