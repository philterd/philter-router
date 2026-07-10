# Batch Redaction

To redact an existing directory tree, drive the [HTTP API](http-api.md) from a client that walks the
tree and POSTs each file. Philter Router stays a stateless gateway: it routes and forwards, the client
handles the filesystem. A reference client, [`scripts/redact-tree.sh`](https://github.com/philterd/philter-router/blob/main/scripts/redact-tree.sh),
ships with the repository and needs only `curl` and `find`.

```
scripts/redact-tree.sh --in /data/src --out /data/redacted --url https://localhost:8080 --insecure
```

It walks `--in` recursively and writes each redacted file to `--out` under the same relative path, so the
output mirrors the input tree. The router selects the policy and engine per file.

| Option | Purpose |
| --- | --- |
| `--in DIR` | Input directory to walk (required). |
| `--out DIR` | Output directory; the input tree is mirrored here (required). |
| `--url URL` | Router base URL (default `https://localhost:8080`). |
| `--jobs N` | Files to process at once (default 4). |
| `--api-key KEY` | Philter API key, sent as `Authorization` and forwarded to Philter. |
| `--policy NAME` | Force a policy, overriding routing. |
| `--context NAME` | Philter context. |
| `--force` | Re-redact even when the output already exists. |
| `--insecure` | Skip TLS verification, for the router's self-signed certificate. |

## Resumable

A file whose output already exists is skipped, so an interrupted run can be re-run to finish the
remainder. `--force` re-redacts everything. There is no separate state to manage: the presence of the
output file is the record of what is done. Output is written atomically (a temporary file is moved into
place only on success), so a failed or interrupted file is never mistaken for completed.

## Failures

A file that the router rejects is logged to stderr with its HTTP status and left without an output file;
the run continues. The script prints a `redacted / skipped / failed` summary and exits non-zero if any
file failed, so it works in a pipeline or CI job.

## Other clients

The script is only a convenience. Any tool that can POST bytes works. For example, with GNU `parallel`:

```
find /data/src -type f -print0 \
  | parallel -0 -j8 curl -sS -k -X POST --data-binary @{} \
      'https://localhost:8080/api/filter?filename={/}' -o /data/redacted/{/}
```

Directory-based routing needs the source directory: the script sends it in the `X-Source-Directory`
header (an uploaded file has no path of its own). A hand-rolled client that relies on directory routes
should do the same.
