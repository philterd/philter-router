# Example configurations

Pass any of these to the router:

```
java -jar philter-router.jar examples/<file>.yaml
```

| File | Entry point | Shows |
| --- | --- | --- |
| `minimal.yaml` | Folder watching | The smallest config: one watched directory, one policy. |
| `folder-watching.yaml` | Folder watching | Multiple locations, two engines, a classifier, and routes. |
| `network-share.yaml` | Folder watching | `poll` on an SMB/NFS share, `notify` on a local disk. |
| `http-api.yaml` | HTTP API | The Philter-compatible API with classification routing. |
| `watch-and-api.yaml` | Both | The API and folder watching from one config. |
| `classifier-routing.yaml` | HTTP API | Routing by local-LLM classification and by language. |

See the [configuration guide](../docs/docs/configuration.md) for every field.
