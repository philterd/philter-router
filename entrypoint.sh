#!/bin/sh
#
# Copyright 2026 Philterd, LLC @ https://www.philterd.ai
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#          http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eu

KEYSTORE="${ROUTER_KEYSTORE:-/home/router/tls/keystore.p12}"

# SSL_OPTS unset means "use the built-in self-signed certificate". Set it to anything, including
# the empty string to serve plain HTTP, to take over TLS configuration entirely.
if [ -z "${SSL_OPTS+set}" ]; then

    if [ -f "$KEYSTORE" ]; then

        if [ -z "${ROUTER_KEYSTORE_PASSWORD:-}" ]; then
            echo "A keystore is present at $KEYSTORE but ROUTER_KEYSTORE_PASSWORD is not set." >&2
            echo "Set it to that keystore's password, or set SSL_OPTS to configure TLS yourself." >&2
            exit 1
        fi

    else

        # Generate the keypair here rather than in the Dockerfile. A key baked into an image is
        # shared by everyone who pulls it, which leaves the API's TLS worthless against anyone
        # holding the same image. Generating at start gives each container its own key.
        ROUTER_KEYSTORE_PASSWORD="${ROUTER_KEYSTORE_PASSWORD:-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')}"

        mkdir -p "$(dirname "$KEYSTORE")"
        keytool -genkeypair -alias philter-router -keyalg RSA -keysize 2048 -validity 3650 \
                -dname "CN=philter-router, O=Philterd" \
                -ext "san=dns:localhost,dns:philter-router,ip:127.0.0.1" \
                -storetype PKCS12 -keystore "$KEYSTORE" \
                -storepass "$ROUTER_KEYSTORE_PASSWORD" -keypass "$ROUTER_KEYSTORE_PASSWORD" >/dev/null

        echo "Generated a self-signed certificate for this container at $KEYSTORE."

    fi

    # Passed as environment rather than -D so the password stays off the java command line.
    SERVER_SSL_ENABLED=true
    SERVER_SSL_KEY_STORE="$KEYSTORE"
    SERVER_SSL_KEY_STORE_TYPE=PKCS12
    SERVER_SSL_KEY_STORE_PASSWORD="$ROUTER_KEYSTORE_PASSWORD"
    SERVER_SSL_KEY_ALIAS=philter-router
    export SERVER_SSL_ENABLED SERVER_SSL_KEY_STORE SERVER_SSL_KEY_STORE_TYPE \
           SERVER_SSL_KEY_STORE_PASSWORD SERVER_SSL_KEY_ALIAS

    SSL_OPTS=""

fi

exec java $SSL_OPTS ${JAVA_OPTS:-} -jar /app/philter-router.jar "$ROUTER_CONFIG"
