#!/bin/bash
set -eo pipefail

export CONTAINER_PUBLISH="false"
PUBLISH_BUILD="${PUBLISH_BUILD:-false}"
prod_release="false"
mapfile -t prod_release_br_list < <(echo "${PROD_RELEASE_BRANCHES}" | tr " " "\n")

pom_version="$(xpath -q -e '/project/version/text()' ./pom.xml)"
sdk_version="$(xpath -q -e '/project/version/text()' ./sdk/pom.xml)"
dbtool_version="$(xpath -q -e '/project/version/text()' ./tools/dbtool/pom.xml)"
sctool_version="$(xpath -q -e '/project/version/text()' ./tools/sctool/pom.xml)"
sidechains_sdk_account_sctools_version="$(xpath -q -e '/project/version/text()' ./tools/sidechains-sdk-account_sctools/pom.xml)"
sidechains_sdk_utxo_sctools_version="$(xpath -q -e '/project/version/text()' ./tools/sidechains-sdk-utxo_sctools/pom.xml)"
signingtool_version="$(xpath -q -e '/project/version/text()' ./tools/signingtool/pom.xml)"
simpleapp_version="$(xpath -q -e '/project/version/text()' ./examples/utxo/simpleapp/pom.xml)"

if [ -z "${TRAVIS_TAG}" ]; then
  echo "TRAVIS_TAG:                                             No TAG"
else
  echo "TRAVIS_TAG:                                             $TRAVIS_TAG"
fi
echo "Production release branch(es):                          ${prod_release_br_list[*]}"
echo "./pom.xml version:                                      $pom_version"
echo "./sdk/pom.xml version:                                  $sdk_version"
echo "./tools/dbtool/pom.xml version:                         $dbtool_version"
echo "./tools/sctool/pom.xml version:                         $sctool_version"
echo "./tools/sidechains-sdk-account_sctools/pom.xml version: $sidechains_sdk_account_sctools_version"
echo "./tools/sidechains-sdk-utxo_sctools/pom.xml version:    $sidechains_sdk_utxo_sctools_version"
echo "./tools/signingtool/pom.xml version:                    $signingtool_version"
echo "./examples/utxo/simpleapp/pom.xml version:              $simpleapp_version"

if [ -d "${TRAVIS_BUILD_DIR}/libevm" ]; then
  lib_evm_version="$(xpath -q -e '/project/version/text()' ./libevm/pom.xml)"
  evmapp_version="$(xpath -q -e '/project/version/text()' ./examples/account/evmapp/pom.xml)"

  echo "./libevm/pom.xml version:             ${lib_evm_version}"
  echo "./examples/account/evmapp/pom.xml version:    ${evmapp_version}"
fi

# Functions
function import_gpg_keys () {
  # shellcheck disable=SC2207
  declare -r my_arr=( $(echo "${@}" | tr " " "\n") )

  if [ "${#my_arr[@]}" -eq 0 ]; then
    echo "Warning: there are ZERO gpg keys to import. Please check if *MAINTAINERS_KEYS variable(s) are set correctly. The build is not going to be released ..."
    export CONTAINER_PUBLISH="false"
  else
    # shellcheck disable=SC2145
    printf "%s\n" "Tagged build, fetching keys:" "${@}" ""
    for key in "${my_arr[@]}"; do
      gpg -v --batch --keyserver hkps://keys.openpgp.org --recv-keys "${key}" ||
      gpg -v --batch --keyserver hkp://keyserver.ubuntu.com --recv-keys "${key}" ||
      gpg -v --batch --keyserver hkp://pgp.mit.edu:80 --recv-keys "${key}" ||
      gpg -v --batch --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys "${key}" ||
      { echo -e "Warning: ${key} can not be found on GPG key servers. Please upload it to at least one of the following GPG key servers:\nhttps://keys.openpgp.org/\nhttps://keyserver.ubuntu.com/\nhttps://pgp.mit.edu/"; export CONTAINER_PUBLISH="false"; }
    done
  fi
}

function check_signed_tag () {
  local tag="${1}"
  # Checking if git tag signed by the maintainers
  if git verify-tag -v "${tag}"; then
    echo "${tag} is a valid signed tag"
    return 0
  fi

  echo "Git tag's = ${tag} gpg signature is NOT valid. The build is not going to be released..."
  return 1
}

function  check_versions_match () {
  local versions_to_check=("$@")

  if [ "${#versions_to_check[@]}" -eq 1 ]; then
    echo "Warning: ${FUNCNAME[0]} requires more than one version to be able to compare with.  The build is not going to be released ..."
    export CONTAINER_PUBLISH="false" && return
  fi

  for (( i=0; i<((${#versions_to_check[@]}-1)); i++ )); do
    [ "${versions_to_check[$i]}" != "${versions_to_check[(($i+1))]}" ] &&
    { echo -e "Warning: one or more module(s) versions do NOT match. The build is not going to be released ... !!!\nThe versions are ${versions_to_check[*]}"; export IS_A_RELEASE="false" && break; }
  done

  export CONTAINER_PUBLISH="true"
}

function release_prep () {
  echo "" && echo "=== ${1} release build ===" && echo ""
  echo "Fetching maven gpg signing keys."
  curl -sLH "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github.v3.raw" "${MAVEN_KEY_ARCHIVE_URL}" |
    openssl enc -d -aes-256-cbc -md sha256 -pass pass:"${MAVEN_KEY_ARCHIVE_PASSWORD}" |
    tar -xzf- -C "${HOME}"
  export CONTAINER_PUBLISH="true"
}

# empty key.asc file in case we're not signing
touch "${HOME}/key.asc"

if [ -n "${TRAVIS_TAG}" ] && [ "${PUBLISH_BUILD}" = "true" ]; then
  # checking if MAINTAINER_KEYS is set
  if [ -z "${MAINTAINER_KEYS}" ]; then
    echo "MAINTAINER_KEYS variable is not set. Make sure to set it up for release build!!!"
  fi

  # shellcheck disable=SC2155
  export GNUPGHOME="$(mktemp -d 2>/dev/null || mktemp -d -t 'GNUPGHOME')"
  import_gpg_keys "${MAINTAINER_KEYS}"

  # Checking git tag gpg signature requirement
  if ( check_signed_tag "${TRAVIS_TAG}" ); then
    # Checking evm versions if exist
    if [ -d "${TRAVIS_BUILD_DIR}/libevm" ]; then
      check_versions_match "${pom_version}" "${lib_evm_version}" "${evmapp_version}"
    fi

    # Checking versions match
    check_versions_match "${pom_version}" "${sdk_version}" "${dbtool_version}" "${sctool_version}" "${sidechains_sdk_account_sctools_version}" "${sidechains_sdk_utxo_sctools_version}" "${signingtool_version}" "${simpleapp_version}"

    # Prod vs dev release
    for release_branch in "${prod_release_br_list[@]}"; do
      if ( git branch -r --contains "${TRAVIS_TAG}" | grep -xqE ". origin\/${release_branch}$" ); then
        # Checking format of production release pom version
        if ! [[ "${pom_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-RC[0-9]+)?$ ]]; then
          echo "Warning: package(s) version is in the wrong format for PRODUCTION} release. Expecting: d.d.d(-RC[0-9]+)?. The build is not going to be released !!!"
          export CONTAINER_PUBLISH="false"
        fi

        # Checking Github tag format
        if ! [[ "${TRAVIS_TAG}" == "${pom_version}" ]]; then
          echo "" && echo "=== Warning: GIT tag format differs from the pom file version. ===" && echo ""
          echo -e "Github tag name: ${TRAVIS_TAG}\nPom file version: ${pom_version}.\nThe build is not going to be released !!!"
          export CONTAINER_PUBLISH="false"
        fi

        # Announcing PROD release
        if [ "${CONTAINER_PUBLISH}" == "true" ]; then
          prod_release="true"
          release_prep Production
        fi
      fi
    done
    if [ "${prod_release}" = "false" ]; then
      # Checking if package version matches DEV release version
      if ! [[ "${pom_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-RC[0-9]+)?(-SNAPSHOT){1}$ ]]; then
        echo "Warning: package(s) version is in the wrong format for DEVELOPMENT release. Expecting: d.d.d(-RC[0-9]+)?(-SNAPSHOT){1}. The build is not going to be released !!!"
        export CONTAINER_PUBLISH="false"
      fi

      # Checking Github tag format
      if ! [[ "${TRAVIS_TAG}" =~ "${pom_version}"[0-9]*$ ]]; then
        echo "" && echo "=== Warning: GIT tag format differs from the pom file version. ===" && echo ""
        echo -e "Github tag name: ${TRAVIS_TAG}\nPom file version: ${ROOT_POM_VERSION}.\nThe build is not going to be released !!!"
        export CONTAINER_PUBLISH="false"
      fi

      # Announcing DEV release
      if [ "${CONTAINER_PUBLISH}" == "true" ]; then
        release_prep Development
      fi
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
