#!/bin/bash

set -euo pipefail

test_cmd="${1:-}"
test_args="${2:-}"

[ "$#" -ne 2 ] && { echo -e "Error: function requires exactly two arguments.\n\n"; exit 1;}

# Functions
function fn_die() {
  echo -e "$1" >&2
  exit "${2:-1}"
}

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
    return 0
  else
    echo "Git tag = ${1} signature is NOT valid. Codesigning will be skipped..."
    return 1
  fi
}

CURRENT_DIR="${PWD}"

# Step 1
echo "" && echo "=== Get latest prod travis build id and commit sha ===" && echo ""

zen_tag="$(curl -H "Accept: application/vnd.github.v3+json" https://api.github.com/repos/HorizenOfficial/zen/git/refs/tags | jq -r '[.[] | select(.ref | test("refs/tags/v[0-9]\\.[0-9]\\.[0-9]$"))][-1].ref' | sed -e 's|refs/tags/||')"
check_runs="$(curl -sL -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" "https://api.github.com/repos/HorizenOfficial/zen/commits/${zen_tag}/check-runs")"
travis_build_id="$(basename "$(jq -rc '.check_runs[0].details_url' <<< "${check_runs}")")"
commit_sha="$(jq -rc '.check_runs[0].head_sha' <<< "${check_runs}")"
MAINTAINER_KEYS="219f55740bbf7a1ce368ba45fb7053ce4991b669 8EDE560493C65AC1 D3A22623FF9B9F11 1FCA7260796CB902 F136264D7F4A2BB5"

travis_urls="
#amd64
https://f001.backblazeb2.com/file/ci-horizen/amd64-linux-ubuntu_focal-${travis_build_id}-${commit_sha}.tar.gz.sha256
https://f001.backblazeb2.com/file/ci-horizen/amd64-linux-ubuntu_focal-${travis_build_id}-${commit_sha}.tar.gz
"


# Step 2
echo "" && echo "=== Create folder structure ===" && echo ""

base_dir="${CURRENT_DIR}/zen_release_${zen_tag}"
if [ -d "${base_dir}" ]; then
  echo "${base_dir} folder already exists, aborting!"
  exit 1
fi
mkdir -p "${base_dir}"/{travis_files,src}

# Step 3
echo "" && echo "=== Download artifacts ===" && echo ""

cd "${base_dir}"/travis_files
echo "$travis_urls" > ./travis_urls.txt
sudo apt-get update
sudo apt-get -y --no-install-recommends install aria2
aria2c -x16 -s16 -i ./travis_urls.txt --allow-overwrite=true --always-resume=true --auto-file-renaming=false

# Step 4
echo "" && echo "=== Checksum verification===" && echo ""

if shasum -a256 -c ./*.sha256; then
  echo "Checksum verification passed."
else
  fn_die "Checksum verification failed."
fi

# Step 5
echo "" && echo "=== Extract artifacts from tar" && echo ""
tar_file="$(find "$(realpath ${base_dir}/travis_files/)" -type f -name "*.tar.gz")"

release_folder="zen-${zen_tag}-amd64"
mkdir -p "${base_dir}/src/${release_folder}"
tar -xzf "${tar_file}" -C "${base_dir}/src/${release_folder}"

# Step 6
echo "" && echo "=== Verify git tag signed by allowlisted maintainer" && echo ""

cd "${base_dir}/src/${release_folder}"
GNUPGHOME="$(mktemp -d 2>/dev/null || mktemp -d -t "GNUPGHOME")"
export GNUPGHOME
import_gpg_keys "${MAINTAINER_KEYS}"
check_signed_tag "${zen_tag}" && IS_RELEASE="true" || IS_RELEASE="false"
export IS_RELEASE
( gpgconf --kill dirmngr || true )
( gpgconf --kill gpg-agent || true )
rm -rf "${GNUPGHOME:?}"
unset GNUPGHOME

# Step 7
echo "" && echo "=== Export BITCOINCLI, BITCOIND and SIDECHAIN_SDK path as env vars, needed for python tests" && echo ""

BITCOINCLI="${base_dir}/src/${release_folder}/src/zen-cli"
BITCOIND="${base_dir}/src/${release_folder}/src/zen-cli"
SIDECHAIN_SDK="${CURRENT_DIR}"

if [[ ! -f "$BITCOINCLI" ]]; then
  fn_die "zen-cli does not exist in the given path. Exiting ..."
fi
if [[ ! -f "$BITCOIND" ]]; then
  fn_die "zend does not exist in the given path. Exiting ..."
fi
if [[ ! -d "$SIDECHAIN_SDK" ]]; then
  fn_die "Sidechain-SDK does not exist in the given path. Exiting ..."
fi

export BITCOINCLI
export BITCOIND
export SIDECHAIN_SDK

# Step 8
echo "" && echo "=== Fetch zen params" && echo ""
${base_dir}/src/${release_folder}/zcutil/fetch-params.sh || { retval="$?"; echo "Error: was not able to fetch zen params."; exit $retval; }

# Step 9
echo "" && echo "=== Building SideChain SDK ===" && echo ""
cd $CURRENT_DIR
mvn clean install -Dmaven.test.skip=true || { retval="$?"; echo "Error: was not able to complete mvn clean install of Sidechain SDK."; exit $retval; }

# Step 10
echo "" && echo "=== Installing node 16.0.0 ===" && echo ""
source ~/.nvm/nvm.sh
nvm install v16.0.0 || { retval="$?"; echo "Error: was not able to nvm install node 16.0.0"; exit $retval; }

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
"${test_cmd}" "${test_args}"