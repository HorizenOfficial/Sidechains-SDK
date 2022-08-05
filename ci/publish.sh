#!/bin/bash
set -eo pipefail
retval=0

cd sdk
if [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT){1}[0-9]*$ ]]; then
  echo "" && echo "=== Publishing development release on Sonatype Nexus repository. Timestamp is: $(date '+%a %b %d %H:%M:%S %Z %Y') ===" && echo ""
  mvn deploy -P sign,build-extras --settings ./ci/mvn_settings.xml -DskipTests=true -B || retval="$?"
elif [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "" && echo "=== Publishing production release on Maven repository. Timestamp is: $(date '+%Y-%m-%d %H:%M') ===" && echo ""
  mvn deploy -P sign,build-extras --settings ./ci/mvn_settings.xml -DskipTests=true -B || retval="$?"
else
  echo "" && echo "=== Not going to publish!!! Release tag = ${TRAVIS_TAG} did not match either DEV or PROD format requirements ===" && echo ""
fi

exit "$retval"