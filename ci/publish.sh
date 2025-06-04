#!/bin/bash
set -eo pipefail

retval=0
projects_to_publish=('sdk' 'tools/sctool' 'tools/sidechains-sdk-account_sctools' 'tools/sidechains-sdk-utxo_sctools')
workdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null && pwd )"

# Functions
function publish_project () {
  local project="${1}"
  if [ ! -d "${project}" ]; then
    echo "Error: project directory ${project} does not exist. Aborting publishing ..."
    exit 1
  fi

  # Building and publishing
  cd "${project}"
  if [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT){1}[0-9]*$ ]]; then
    echo "" && echo "=== Publishing DEVELOPMENT release of '${project}' project on Maven Central Portal repository. Timestamp is: $(date '+%a %b %d %H:%M:%S %Z %Y') ===" && echo ""
    mvn deploy -P sign,build-extras --settings "${workdir}"/ci/mvn_settings.xml -DskipTests=true -B || { retval="$?"; echo "Error: was not able to publish ${project} project version = ${TRAVIS_TAG} on public repository."; }
 elif [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-RC[0-9]+){1}$ ]]; then
    echo "" && echo "=== Publishing RC release of '${project}' project on Maven repository. Timestamp is: $(date '+%Y-%m-%d %H:%M') ===" && echo ""
    mvn deploy -P sign,build-extras --settings "${workdir}"/ci/mvn_settings.xml -DskipTests=true -B || { retval="$?"; echo "Error: was not able to publish ${project} project version = ${TRAVIS_TAG} on public repository."; }
  elif [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]$ ]]; then
    echo "" && echo "=== Publishing PRODUCTION release of '${project}' project on Maven repository. Timestamp is: $(date '+%Y-%m-%d %H:%M') ===" && echo ""
    mvn deploy -P sign,build-extras --settings "${workdir}"/ci/mvn_settings.xml -DskipTests=true -B || { retval="$?"; echo "Error: was not able to publish ${project} project version = ${TRAVIS_TAG} on public repository."; }
  else
    echo "" && echo "=== Not going to publish!!! Release tag = ${TRAVIS_TAG} did not match either DEV or PROD format requirements ===" && echo ""
  fi
}

# Publishing projects
for project in "${projects_to_publish[@]}"; do
  cd "${workdir}"
  publish_project "${project}"
done

exit "$retval"