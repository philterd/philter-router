FROM eclipse-temurin:25-jre
WORKDIR /app

# Copies the prebuilt, tested jar. Run `mvn package` before `docker build`.
RUN useradd --system --uid 10001 --create-home router
COPY target/philter-router.jar /app/philter-router.jar
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
USER router

ENV ROUTER_CONFIG=/config/router.yaml

# The self-signed certificate is generated per container by the entrypoint, never baked into the
# image: a published image would otherwise hand every user the same private key.
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
