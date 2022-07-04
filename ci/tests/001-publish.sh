#!/bin/bash

set -eo pipefail
retval=0

# Publishing if a release build
if [ "$CONTAINER_PUBLISH" = "true" ]; then
  cd sdk
  echo "======================================================================"
  echo "|              Deploying sdk bundle to maven repository              |"
  echo "======================================================================"
  echo
  mvn deploy -P sign,build-extras --settings ../ci/mvn_settings.xml -DskipTests=true -B || retval="$?"
else
  echo "" && echo "=== This is NOT a release build. CONTAINER_PUBLISH variable is set to ${CONTAINER_PUBLISH}. ===" && echo ""
fi

exit "$retval"

