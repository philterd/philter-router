# Routing

For each file the router evaluates the ordered `routes` top to bottom and takes the first that matches,
producing an engine and a policy. If none match, it uses the `default`.

## Match conditions

A route's `match` block can contain any of:

| Field | Matches on |
| --- | --- |
| `contentTypes` | The content type detected from the file's bytes by Apache Tika, for example `application/pdf`. This is more reliable than the extension because it inspects the content. |
| `extensions` | The filename extension, for example `.docx`. |
| `directories` | The source directory (a path prefix). |
| `classification` | A label returned by a named classifier: `{ classifier: <name>, label: <label> }`. |

Within a route, all specified fields must match (AND). Within a single field, a list is any-of (OR). To
express OR across fields, write two routes; ordering makes this natural.

## The language gate

Each route carries a `languages` list of allowed ISO 639-3 codes. A route applies only if the
document's detected language is in that list. The gate is AND-ed with the `match`.

- When `languages` is omitted it defaults to `[eng]`, so a route with no explicit languages applies to
  English documents only.
- Use `any` to accept all languages (this also skips language detection for that route).
- Language is detected with Apache OpenNLP, locally, on the extracted text. If the language cannot be
  confidently determined, or is not in the list, the route does not apply.

The `default` is not language-gated, so a document whose language matches no route is still redacted
under the default policy.

## Evaluation tiers

Routing runs cheap work first and reaches for expensive work only when a route needs it:

1. **Metadata** - content type, extension, directory. No text is extracted.
2. **Language** - requires extracting text (lossy, an excerpt), then a local OpenNLP call.
3. **Classification** - requires extracted text and a local LLM call.

A route that fails a cheaper tier never triggers a more expensive one. Text is extracted at most once
per file and shared by the language and classification tiers; a classifier runs at most once per file
and its label is cached. Order extension and directory routes ahead of classification routes so most
files never invoke a classifier.

## Safe default

When no route matches, or language detection or a classifier is unavailable or inconclusive, the router
applies the mandatory `default` outcome. The default either redacts with a policy that redacts, or, with
`action: reject`, refuses the document (moved to `error` by the watcher, `422` over the HTTP API). Either
way an unmatched or unclassifiable file is never passed through without redaction. See
[Configuration](configuration.md#default).

## Overrides (HTTP API)

On the [HTTP API](http-api.md), a caller may override the selected policy with `?p=<policy>` and supply
attributes the router cannot derive from an upload, such as a source directory via the
`X-Source-Directory` header. The engine is always chosen by routing.
