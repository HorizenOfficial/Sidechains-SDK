#!/bin/bash

set -euo pipefail

# Functions
function fn_die() {
  echo -e "$1" >&2
  exit "${2:-1}"
}

function import_gpg_keys() {
  # shellcheck disable=SC2207
  declare -r my_arr=( $(echo "${@}" | tr " " "\n") )

  if [ "${#my_arr[@]}" -eq 0 ]; then
    fn_die "Error: There are ZERO gpg keys to import. ZEN_REPO_MAINTAINER_KEYS variable is not set. Exiting ..."
  else
    # shellcheck disable=SC2145
    printf "%s\n" "Tagged build, fetching keys:" "${@}" ""
    for key in "${my_arr[@]}"; do
      gpg -v --batch --keyserver hkps://keys.openpgp.org --recv-keys "${key}" ||
      gpg -v --batch --keyserver hkp://keyserver.ubuntu.com --recv-keys "${key}" ||
      gpg -v --batch --keyserver hkp://pgp.mit.edu:80 --recv-keys "${key}" ||
      gpg -v --batch --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys "${key}" ||
      { fn_die "Error: ${key} can not be found on GPG key servers. Please upload it to at least one of the following GPG key servers:\nhttps://keys.openpgp.org/\nhttps://keyserver.ubuntu.com/\nhttps://pgp.mit.edu/ Exiting ...";}
    done
  fi
}

function check_signed_tag() {
  local tag="${1}"

  if git verify-tag -v "${tag}"; then
    echo "${tag} is a valid signed tag"
  else
    fn_die "Error: ${tag} signature is NOT valid. Exiting ..."
  fi
}


if [ -z "${NODE_VERSION:-}" ]; then
  fn_die "Error: NODE_VERSION variable is not set. Exiting ..."
fi

if [ -z "${TEST_CMD:-}" ]; then
  fn_die "Error: TEST_CMD variable is not set. Exiting ..."
fi

if [ -z "${TEST_ARGS:-}" ]; then
  fn_die "Error: TEST_ARGS variable is not set. Exiting ..."
fi

if [ -z "${API_ZEN_REPO_URL:-}" ]; then
  fn_die "Error: API_ZEN_REPO_URL variable is not set. Exiting ..."
fi

if [ -z "${ZEN_REPO_MAINTAINER_KEYS:-}" ]; then
  fn_die "Error: ZEN_REPO_MAINTAINER_KEYS variable is not set. Exiting ..."
fi

CURRENT_DIR="${PWD}"

# Step 1
echo "" && echo "=== Get latest ZEN repo PROD build id and commit hash ===" && echo ""

zen_tag="$(curl -H "Accept: application/vnd.github.v3+json" https://api.github.com/repos/HorizenOfficial/zen/git/refs/tags | jq -r '[.[] | select(.ref | test("refs/tags/v[0-9]\\.[0-9]\\.[0-9]$"))][-1].ref' | sed -e 's|refs/tags/||')"
check_runs="$(curl -sL -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" "${API_ZEN_REPO_URL}/commits/${zen_tag}/check-runs")"
travis_build_id="$(basename "$(jq -rc '.check_runs[0].details_url' <<< "${check_runs}")")"
commit_sha="$(jq -rc '.check_runs[0].head_sha' <<< "${check_runs}")"

travis_urls="
#amd64
https://f001.backblazeb2.com/file/ci-horizen/amd64-linux-ubuntu_focal-${travis_build_id}-${commit_sha}.tar.gz.sha256
https://f001.backblazeb2.com/file/ci-horizen/amd64-linux-ubuntu_focal-${travis_build_id}-${commit_sha}.tar.gz
"


# Step 2
echo "" && echo "=== Create folder structure ===" && echo ""

base_dir="${CURRENT_DIR}/zen_release"
is_cached=false

if [ -d "${base_dir}/travis_files" ]; then
  echo "${base_dir} folder already exists, using cache!"
  cd "${base_dir}"/travis_files
  is_cached=true
else
  mkdir -p "${base_dir}"/{travis_files,src}

  # Step 3
  echo "" && echo "=== Downloading ZEN artifacts from remote bucket ===" && echo ""

  cd "${base_dir}"/travis_files
  echo "$travis_urls" > ./travis_urls.txt
  sudo apt-get update
  sudo apt-get -y --no-install-recommends install aria2
  aria2c -x16 -s16 -i ./travis_urls.txt --allow-overwrite=true --always-resume=true --auto-file-renaming=false
fi


# Step 4
echo "" && echo "=== Checksum verification ===" && echo ""

if shasum -a256 -c ./*.sha256; then
  echo "Checksum verification passed."
else
  fn_die "Error: checksum verification failed. Exiting ..."
fi

# Step 5
release_folder="zen-${zen_tag}-amd64"
if [ "$is_cached" = false ]; then
  echo "" && echo "=== Extract artifacts from tar ===" && echo ""
  tar_file="$(find "$(realpath ${base_dir}/travis_files/)" -type f -name "*.tar.gz")"

  mkdir -p "${base_dir}/src/${release_folder}"
  tar -xzf "${tar_file}" -C "${base_dir}/src/${release_folder}"
fi

# Step 6
echo "" && echo "=== Verify git tag signed by allowlisted maintainer ===" && echo ""

cd "${base_dir}/src/${release_folder}"
GNUPGHOME="$(mktemp -d 2>/dev/null || mktemp -d -t "GNUPGHOME")"
export GNUPGHOME
import_gpg_keys "${ZEN_REPO_MAINTAINER_KEYS}"
check_signed_tag "${zen_tag}"
( gpgconf --kill dirmngr || true )
( gpgconf --kill gpg-agent || true )
rm -rf "${GNUPGHOME:?}"
unset GNUPGHOME

# Step 7
echo "" && echo "=== Export BITCOINCLI, BITCOIND and SIDECHAIN_SDK path as env vars, needed for python tests ===" && echo ""

BITCOINCLI="${base_dir}/src/${release_folder}/src/zen-cli"
BITCOIND="${base_dir}/src/${release_folder}/src/zend"
SIDECHAIN_SDK="${CURRENT_DIR}"

if [[ ! -f "$BITCOINCLI" ]]; then
  fn_die "Error: zen-cli does not exist in the given path. Exiting ..."
fi
if [[ ! -f "$BITCOIND" ]]; then
  fn_die "Error: zend does not exist in the given path. Exiting ..."
fi
if [[ ! -d "$SIDECHAIN_SDK" ]]; then
  fn_die "Error: Sidechain-SDK does not exist in the given path. Exiting ..."
fi

export BITCOINCLI
export BITCOIND
export SIDECHAIN_SDK

# Step 8
echo "" && echo "=== Fetch zen params ===" && echo ""
${base_dir}/src/${release_folder}/zcutil/fetch-params.sh || { retval="$?"; echo "Error: was not able to fetch zen params."; exit $retval; }

# Step 9
echo "" && echo "=== Building SideChain SDK ===" && echo ""
cd $CURRENT_DIR
mvn clean install -Dmaven.test.skip=true || { retval="$?"; echo "Error: was not able to complete mvn clean install of Sidechain SDK."; exit $retval; }

# Step 10
echo "" && echo "=== Installing nodejs ===" && echo ""
source ~/.nvm/nvm.sh
nvm install "${NODE_VERSION}" || { retval="$?"; echo "Error: was not able to nvm install node ${NODE_VERSION}"; exit $retval; }

# Step 11
echo "" && echo "=== Installing yarn ===" && echo ""
npm install --global yarn || { retval="$?"; echo "Error: was not able to install yarn with npm install."; exit $retval; }

# Step 12
echo "" && echo "=== Installing Python dependencies ===" && echo ""
pip install --no-cache-dir --upgrade pip
pip install --no-cache-dir -r ./requirements.txt
cd qa/
pip install --no-cache-dir -r ./SidechainTestFramework/account/requirements.txt

# Step 13
echo "" && echo "=== Run tests ===" && echo ""
"${TEST_CMD}" "${TEST_ARGS}"