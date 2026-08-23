FROM eclipse-temurin:25-jre
WORKDIR /app

# The TLS directory is created here so a volume mounted over it inherits router's ownership. A volume
# mounted at a path absent from the image is created root-owned, and the entrypoint runs as router.
RUN useradd --system --uid 10001 --create-home router \
    && install -d -o router -g router /home/router/tls

# Copies the prebuilt, tested jar. Run `mvn package` before `docker build`.
COPY target/philter-router.jar /app/philter-router.jar
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
USER router

ENV ROUTER_CONFIG=/config/router.yaml

# The self-signed certificate is generated per container by the entrypoint, never baked into the
# image: a published image would otherwise hand every user the same private key.
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
