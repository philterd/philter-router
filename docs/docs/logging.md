# Logging

Philter Router uses log4j2. Two streams are kept separate: operational logs and the audit trail.

## No un-redacted content

The router handles un-redacted files, extracted text, and classifier prompts and responses. None of
that is logged. Log records never contain extracted text, prompts, responses, document bodies, or file
tokens. Filenames and paths can themselves contain sensitive information, so the audit trail identifies
a file by its content hash rather than its name.

## Audit trail

Every file produces one structured JSON record on a dedicated `audit` logger, separate from operational
logging so it can be shipped and retained on its own. The `event` field is one of:

- `routed` - sent to an engine. Includes the content hash, the matched route (or `default`), the engine,
  the policy, whether the default was used, the detected language, and any computed classifier labels. A
  file that matched no route but was redacted by a redacting default has `matchedRoute: "default"` and
  `isDefault: true`.
- `rejected` - matched no route and was refused by a rejecting default (`action: reject`). Includes the
  content hash, `matchedRoute`, language, and classifications.
- `failed` - processing or the engine call failed. Includes the content hash and the reason.

```json
{"event":"routed","hash":"9b40...","matchedRoute":"medical-records","engine":"philter1","policy":"hipaa","isDefault":false,"language":"eng","classifications":{"doc-type":"medical"}}
{"event":"rejected","hash":"3a6e...","matchedRoute":"default","language":"eng","classifications":{}}
{"event":"failed","hash":"1c2d...","reason":"HTTP 502"}
```

Shipping this stream to a log aggregator lets you alert on `failed`, on `rejected`, or on `isDefault: true`
(files you have no explicit route for).

### Failed API requests

A failed `POST /api/filter` request is also logged to the **operational** log (not the audit trail) with
the request's filename, the reason, and a timestamp, so a failure can be tied to the file that caused it:

```
2026-07-10T13:34:35,115 ERROR [http-nio-8080-exec-1] a.p.r.a.FilterController - Redaction engine call failed for 'secret-report.pdf': HTTP 502
```

The filename comes from the request's `?filename=` (a text request logs `(text request)`). Filenames are
kept out of the hash-keyed audit trail on purpose, so this diagnostic detail lives in the operational log.
The caller itself receives only a generic error body, not the reason.

## Appenders

Console output is the default, which suits containers. To also write rolling files (for on-premises or
Windows-service deployments), set the log directory:

```
java -Drouter.log.dir=/var/log/philter-router -jar philter-router.jar router.yaml
```

Operational logs and the audit trail roll independently, with the audit trail retained longer.

## Metrics

Metrics are separate from logs. When the HTTP API is enabled, Spring Boot Actuator exposes them at
`GET /actuator/prometheus`, including a per-outcome document counter:

```
philter_router_documents_total{outcome="routed|default|rejected|failed", route="<name>|default|none"}
```

`outcome` is `routed` (matched a route), `default` (redacted by the default), `rejected` (refused by a
rejecting default), or `failed` (processing or engine error). `route` is the matched route name, or
`default` / `none`. Labels are bounded and never include the content hash or filename. Alert on the
`failed` and `rejected` series, or on a rising `default` share, for example:

```
rate(philter_router_documents_total{outcome="failed"}[5m]) > 0
```

The counter records in every mode, but the scrape endpoint is only reachable when the HTTP API is enabled.
