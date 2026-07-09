# Folder Watching

The folder watcher redacts files as they appear in watched directories. Configure one or more locations
under `watch.locations`; each is independent.

```yaml
watch:
  locations:
    - path: "/data/intake"
      mode: poll
      pollIntervalMs: 5000
      stableForMs: 2000
      recursive: true
      output: "/data/redacted"
      done:   "/data/intake/.done"
      error:  "/data/intake/.error"
```

## Watch mechanism

The mechanism is chosen per location, because the right choice depends on the filesystem.

- **`poll`** (default) periodically scans the directory. It is robust and the only reliable option on
  network shares (SMB/NFS), where OS notifications do not fire for changes made by another host.
- **`notify`** uses OS notifications for low latency. It is for local filesystems only. A `notify`
  location also runs a slower reconcile scan, because notification events can be coalesced or dropped.

`pollIntervalMs` and `stableForMs` apply to `poll`. `recursive`, `output`, `done`, and `error` apply to
both.

## Processing a file

For each detected file the watcher:

1. **Waits until the file is fully written.** A file is processed only after its size is stable for
   `stableForMs`. Prefer having producers write to a temporary name and rename into the watched
   directory, so a partially written file is never picked up.
2. **Routes and redacts.** The file is [routed](routing.md) to a policy and engine and sent to Philter.
3. **Drains.** On success the redacted output is written to `output` and the source is moved to `done`.
   On failure the source is moved to `error`. A file never remains in the watched directory
   unprocessed, and is never left neither redacted nor flagged.

## Exactly-once

Files are tracked by content hash, so identical content is processed once even when the watcher
re-observes it (duplicate events, the reconcile scan, or a restart within the process lifetime). The
`output`, `done`, and `error` directories are excluded from scanning.

The processed ledger is in-memory and per-process; a durable ledger that survives restarts is planned.
