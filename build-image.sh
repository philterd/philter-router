#!/bin/bash
set -e

# Builds the Philter Router Docker image for amd64 and arm64. Pushing it is a separate,
# manual step: see push-image.sh.
#
# Each architecture is built and loaded under its own tag, so both are here to
# run and test. push-image.sh pushes those tags and joins them into one
# multi-architecture tag.

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

# The Dockerfile copies the prebuilt jar rather than building it, so build and test it here.
# Set SKIP_MVN=1 to image a jar that is already in target/.
if [ -z "${SKIP_MVN}" ]; then
    mvn package
fi

if [ ! -f target/philter-router.jar ]; then
    echo "target/philter-router.jar not found. Run 'mvn package' first." >&2
    exit 1
fi

# The default builder cannot cross-build, so use a container builder.
docker buildx inspect philter-router-builder > /dev/null 2>&1 ||
    docker buildx create --name philter-router-builder --driver docker-container > /dev/null

for arch in $ARCHES; do
    docker buildx build --builder philter-router-builder \
        --platform "linux/${arch}" --load \
        -t "${IMAGE}:${VERSION}-${arch}" .
done

echo
for arch in $ARCHES; do
    echo "Built ${IMAGE}:${VERSION}-${arch}"
done
echo "Push them with: ./push-image.sh ${VERSION}"
