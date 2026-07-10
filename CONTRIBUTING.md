# Contributing to Philter Router

## Code of Conduct

In the interest of fostering an open and welcoming environment, we as contributors and maintainers pledge to making participation in our project and our community a harassment-free experience for everyone, regardless of age, body size, disability, ethnicity, gender identity and expression, level of experience, nationality, personal appearance, race, religion, or sexual identity and orientation.

Please read and understand the [Code of Conduct](CODE_OF_CONDUCT.md).

## GitHub Workflow

We prefer to take contributions as GitHub pull requests.

1. Create a fork of philterd/philter-router
2. Create a feature branch
3. Build and test your changes locally
4. Commit changes to your feature branch
5. Open a pull request
6. Participate in code review

## Building and Testing

### Required tools

* Java 25
* Maven
* Docker (optional, to build and run the container image)

No external services are required to build or test. The test suite stubs the Philter engine with an in-process mock server and loads the bundled language-detection model, so `mvn test` runs offline once dependencies are cached. Ollama is only needed at runtime if you configure an LLM classifier; the tests do not use it, and there is no Redis or database dependency.

Any Linux distribution works (Ubuntu is the daily driver); the jar also runs on macOS and Windows anywhere Java 25 is available.

### Build

```
mvn package
```

This runs the full test suite and produces the runnable jar at `target/philter-router.jar`. Run only the tests with `mvn test`.

## Documentation

User documentation lives under [`docs/`](docs/) and is published with MkDocs. Preview it locally:

```
cd docs
pip install -r requirements.txt
mkdocs serve
```

When a change alters behavior, configuration, or the HTTP API, update the guides under `docs/docs/` and the OpenAPI specification (`src/main/resources/openapi.yaml`) in the same pull request. Keep code comments short.
