# Logging

Philter Router uses log4j2. Two streams are kept separate: operational logs and the audit trail.

## No un-redacted content

The router handles un-redacted files, extracted text, and classifier prompts and responses. None of
that is logged. Log records never contain extracted text, prompts, responses, document bodies, or file
tokens. Filenames and paths can themselves contain sensitive information, so the audit trail identifies
a file by its content hash rather than its name.

## Audit trail

Every routed or failed file produces one structured JSON record on a dedicated `audit` logger, separate
from operational logging so it can be shipped and retained on its own.

A routed record includes the content hash, the matched route (or `default`), the engine, the policy,
whether the default was used, the detected language, and any classifier labels that were computed. A
failed record includes the content hash and the reason.

```json
{"event":"routed","hash":"9b40...","matchedRoute":"medical-records","engine":"philter1","policy":"hipaa","isDefault":false,"language":"eng","classifications":{"doc-type":"medical"}}
```

## Appenders

Console output is the default, which suits containers. To also write rolling files (for on-premises or
Windows-service deployments), set the log directory:

```
java -Drouter.log.dir=/var/log/philter-router -jar philter-router.jar router.yaml
```

Operational logs and the audit trail roll independently, with the audit trail retained longer.

Metrics are separate from logs. When the HTTP API is enabled, Spring Boot Actuator exposes them at
`GET /actuator/prometheus`.
