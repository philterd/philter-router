# Philter Router

Philter Router routes files to a [Philter](https://github.com/philterd/philter) redaction policy and
engine based on file attributes, then forwards each file for redaction. It is the single front door in
front of one or more Philter engines: hand it a file and it selects the policy and the engine.

A file's route is chosen from its attributes:

- **content type**, detected from the file's bytes with Apache Tika (not just the extension),
- **filename** extension,
- **containing directory**, and
- a **classification** from a local LLM (Ollama).

A file that matches no route, or whose language is not allowed, or whose classifier is unavailable,
falls to a mandatory **default** policy. Every file is sent to Philter for redaction under some policy;
no file is skipped.

## Entry points

Two entry points share one routing pipeline. Enable either or both in the configuration; the router
starts whatever is configured.

- **[Folder watching](folder-watching.md)** watches directories and redacts files as they arrive.
- **[HTTP API](http-api.md)** is a Philter-compatible, send-only front end (`/api/health` and
  `/api/filter` for text and files).

## How routing works

Routing is tiered so expensive work runs only when needed:

1. cheap metadata (content type, extension, directory),
2. language detection (Apache OpenNLP; needs extracted text, runs locally),
3. classification (a local LLM; needs extracted text).

A route that fails a cheaper tier never triggers a more expensive one. See [Routing](routing.md).

## Data boundary

Text extracted for classification and language detection is lossy, used only to choose a route, and is
never written as output or logged. The language detector and the classifier run against local
endpoints, so un-redacted content stays inside your boundary. See [Logging](logging.md) for what the
router does and does not record.

## Next steps

- [Configuration](configuration.md) - the YAML file and its blocks.
- [Deployment](deployment.md) - build, run, Docker, and Compose.
