#!/bin/bash

set -eo pipefail

pom_version="$(xpath -q -e '/project/version/text()' ./pom.xml)"
simpleapp_version="$(xpath -q -e '/project/version/text()' ./examples/simpleapp/pom.xml)"
sdk_version="$(xpath -q -e '/project/version/text()' ./sdk/pom.xml)"
sctool_version="$(xpath -q -e '/project/version/text()' ./tools/sctool/pom.xml)"

echo "TRAVIS_TAG:                           $TRAVIS_TAG"
echo "./pom.xml version:                    $pom_version"
echo "./examples/simpleapp/pom.xml version: $pom_version"
echo "./sdk/pom.xml version:                $pom_version"
echo "./tools/sctool/pom.xml version:       $pom_version"

export CONTAINER_PUBLISH="false"

# Functions
function import_gpg_keys() {
  # shellcheck disable=SC2145
  printf "%s\n" "Tagged build, fetching keys:" "${@}" ""
  # shellcheck disable=SC2207
  declare -r my_arr=( $(echo "${@}" | tr " " "\n") )

  for key in "${my_arr[@]}"; do
    echo "Importing key: ${key}"
    gpg -v --batch --keyserver hkps://keys.openpgp.org --recv-keys "${key}" ||
    gpg -v --batch --keyserver hkp://keyserver.ubuntu.com --recv-keys "${key}" ||
    gpg -v --batch --keyserver hkp://pgp.mit.edu:80 --recv-keys "${key}" ||
    gpg -v --batch --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys "${key}"
  done
}

function check_signed_tag() {
  # Checking if git tag signed by the maintainers
  if git verify-tag -v "${1}"; then
    echo "${1} is a valid signed tag"
  else
    echo "Git tag's = ${1} gpg signature is NOT valid. The build is not going to be released..."
  fi
}

# empty key.asc file in case we're not signing
touch "${HOME}/key.asc"

if [ -n "${TRAVIS_TAG}" ]; then
  # checking if MAINTAINER_KEYS is set
  if [ -z "${MAINTAINER_KEYS}" ]; then
    echo "MAINTAINER_KEYS variable is not set. Make sure to set it up for release build!!!"
  fi

  # shellcheck disable=SC2155
  export GNUPGHOME="$(mktemp -d 2>/dev/null || mktemp -d -t 'GNUPGHOME')"
  import_gpg_keys "${MAINTAINER_KEYS}"

  # Checking git tag gpg signature requirement
  if (check_signed_tag "${TRAVIS_TAG}"); then
    if [ "${pom_version}" != "$simpleapp_version" ] || [ "${pom_version}" != "$sdk_version" ] || [ "${pom_version}" != "$sctool_version" ]; then
      echo "Aborting, mismatch in at least one of the pom.xml version number."
      exit 1
    elif ! [[ "${pom_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
      echo "Aborting, pom version is in the wrong format."
      exit 1
    fi

    if ! [[ "${TRAVIS_TAG}" =~ "${pom_version}"[0-9]*$ ]]; then
      echo "Aborting, tag format differs from the pom file."
      exit 1
    else
      echo "" && echo "=== Release build ===" && echo ""
      export CONTAINER_PUBLISH="true"
      echo "Fetching maven gpg signing keys."
      curl -sLH "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github.v3.raw" "${MAVEN_KEY_ARCHIVE_URL}" |
        openssl enc -d -aes-256-cbc -md sha256 -pass pass:"${MAVEN_KEY_ARCHIVE_PASSWORD}" |
        tar -xzf- -C "${HOME}"
    fi
  fi
fi

# unset credentials if not publishing
if [ "${CONTAINER_PUBLISH}" = "false" ]; then
  export CONTAINER_OSSRH_JIRA_USERNAME=""
  export CONTAINER_OSSRH_JIRA_PASSWORD=""
  export CONTAINER_GPG_KEY_NAME=""
  export CONTAINER_GPG_PASSPHRASE=""
  unset CONTAINER_OSSRH_JIRA_USERNAME
  unset CONTAINER_OSSRH_JIRA_PASSWORD
  unset CONTAINER_GPG_KEY_NAME
  unset CONTAINER_GPG_PASSPHRASE
  echo "" && echo "=== NOT a release build ===" && echo ""
fi

# unset credentials after use
export GITHUB_TOKEN=""
export MAVEN_KEY_ARCHIVE_URL=""
export MAVEN_KEY_ARCHIVE_PASSWORD=""
unset GITHUB_TOKEN
unset MAVEN_KEY_ARCHIVE_URL
unset MAVEN_KEY_ARCHIVE_PASSWORD

set +eo pipefail
