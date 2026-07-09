FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn --batch-mode --update-snapshots dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn --batch-mode --update-snapshots -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home router
COPY --from=build /build/target/philter-router.jar /app/philter-router.jar
RUN keytool -genkeypair -alias philter-router -keyalg RSA -keysize 2048 -validity 3650 \
        -dname "CN=philter-router, O=Philterd" \
        -storetype PKCS12 -keystore /app/keystore.p12 \
        -storepass changeit -keypass changeit \
    && chown router:router /app/keystore.p12
USER router

ENV ROUTER_CONFIG=/config/router.yaml
ENV SSL_OPTS="-Dserver.ssl.enabled=true -Dserver.ssl.key-store=/app/keystore.p12 -Dserver.ssl.key-store-type=PKCS12 -Dserver.ssl.key-store-password=changeit -Dserver.ssl.key-alias=philter-router"
ENTRYPOINT ["sh", "-c", "exec java $SSL_OPTS $JAVA_OPTS -jar /app/philter-router.jar \"$ROUTER_CONFIG\""]
