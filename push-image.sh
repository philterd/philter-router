#!/bin/bash
set -e

# Pushes the images built by build-image.sh to Docker Hub and joins them into a
# single multi-architecture tag. It builds nothing.
#
# Run this by hand, from a machine holding the credential.

# The version defaults to the Maven project version in pom.xml, so the image tag
# matches the version the router reports on /api/health.
pom_version() {
    awk '/<artifactId>philter-router<\/artifactId>/ { found = 1; next }
         found && match($0, /<version>[^<]+<\/version>/) {
             print substr($0, RSTART + 9, RLENGTH - 19); exit }' "$(dirname "$0")/pom.xml"
}

VERSION=${1:-$(pom_version)}
if [ -z "${VERSION}" ]; then
    echo "No version given and none found in pom.xml." >&2
    exit 1
fi
IMAGE=${IMAGE:-philterd/philter-router}
ARCHES=${ARCHES:-"amd64 arm64"}

for arch in $ARCHES; do
    docker push "${IMAGE}:${VERSION}-${arch}"
done

# Joins the pushed per-architecture images under one tag, in the registry.
sources=""
for arch in $ARCHES; do
    sources="${sources} ${IMAGE}:${VERSION}-${arch}"
done

docker buildx imagetools create -t "${IMAGE}:${VERSION}" ${sources}

echo
echo "Pushed ${IMAGE}:${VERSION} for ${ARCHES}"
