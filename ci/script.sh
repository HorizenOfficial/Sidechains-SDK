#!/bin/bash
# shellcheck disable=SC2046
set -euo pipefail

command -v docker &> /dev/null && have_docker="true" || have_docker="false"
# absolute path to project from relative location of this script
workdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null && pwd )"
# defaults if not provided via env
DOCKER_ORG="${DOCKER_ORG:-zencash}"
IMAGE_NAME="${IMAGE_NAME:-sc-ci-base}"
IMAGE_TAG="${IMAGE_TAG:-bionic_jdk-11_latest}"
TESTS="${TESTS:-}"
IS_A_RELEASE="${IS_A_RELEASE:-false}"
image="${DOCKER_ORG}/${IMAGE_NAME}:${IMAGE_TAG}"


# Run tests
if [ -n "${TESTS:-}" ]; then
  echo "" && echo "=== Running tests ===" && echo ""

  if [ "$have_docker" = "true" ]; then
    if [ -n "${DOCKER_USERNAME:-}" ] && [ -n "${DOCKER_PASSWORD:-}" ]; then
      echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
    fi
    docker run --rm -t -v "$workdir":/build -w /build \
      --entrypoint /build/ci/docker/entrypoint.sh \
      -v "${HOME}"/key.asc:/key.asc \
      -e IS_A_RELEASE \
      -e TESTS \
      -e LOCAL_USER_ID="$(id -u)" \
      -e LOCAL_GRP_ID="$(id -g)"\
      "${image}" ci/run_tests.sh
  else
    cd "${workdir:-.}" && ci/run_tests.sh
  fi
fi

# Publish java project stage
if [ "${IS_A_RELEASE}" = "true" ]; then
  echo "" && echo "=== Publishing java project on public repository ===" && echo ""

  if [ "$have_docker" = "true" ]; then
    if [ -n "${DOCKER_USERNAME:-}" ] && [ -n "${DOCKER_PASSWORD:-}" ]; then
      echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
    fi
    docker run --rm -t -v "$workdir":/build -w /build \
      --entrypoint /build/ci/docker/entrypoint.sh \
      -v "${HOME}"/key.asc:/key.asc \
      -e IS_A_RELEASE \
      -e TRAVIS_TAG \
      -e LOCAL_USER_ID="$(id -u)" \
      -e LOCAL_GRP_ID="$(id -g)"\
      $(env | grep -E '^CONTAINER_' | sed -n '/^[^\t]/s/=.*//p' | sed '/^$/d' | sed 's/^/-e /g' | tr '\n' ' ') \
      "${image}" ci/publish.sh
  else
    cd "${workdir:-.}" && ci/publish.sh
  fi
fi