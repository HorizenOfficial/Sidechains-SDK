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

echo "=== Checking if GITHUB_TOKEN is set ==="
if [ -z "${ZEN_REPO_TOKEN:-}" ]; then
  fn_die "ZEN_REPO_TOKEN variable is not set. Exiting ..."
fi

CURRENT_DIR="${PWD}"

# Step 1
echo "" && echo "=== Pull latest zen release ===" && echo ""

json_data=$(curl -sL -H "Accept: application/vnd.github+json" -H "Authorization: Bearer ${ZEN_REPO_TOKEN}" -H "X-GitHub-Api-Version: 2022-11-28" https://api.github.com/repos/HorizenOfficial/zen/releases)

deb_url=""
deb_asc_url=""
deb_sha256_url=""
deb_name=""
deb_asc_name=""
deb_sha256_name=""

while IFS= read -r main_array_item; do
  is_valid=true

  deb_data="$(jq -r 'first(.assets[] | select(.name? and (.name | endswith("-amd64.deb")) and (.name | contains("-legacy-cpu-") | not)) | {name: .name, url: .browser_download_url})' <<< "$main_array_item")"
  deb_asc_data="$(jq -r 'first(.assets[] | select(.name? and (.name | endswith("-amd64.deb.asc")) and (.name | contains("-legacy-cpu-") | not)) | {name: .name, url: .browser_download_url})' <<< "$main_array_item")"
  deb_sha256_data="$(jq -r 'first(.assets[] | select(.name? and (.name | endswith("-amd64.deb.sha256")) and (.name | contains("-legacy-cpu-") | not)) | {name: .name, url: .browser_download_url})' <<< "$main_array_item")"

  deb_url="$(jq -r '.url' <<< "$deb_data")"
  deb_asc_url="$(jq -r '.url' <<< "$deb_asc_data")"
  deb_sha256_url="$(jq -r '.url' <<< "$deb_sha256_data")"

  deb_name="$(jq -r '.name' <<< "$deb_data")"
  deb_asc_name="$(jq -r '.name' <<< "$deb_asc_data")"
  deb_sha256_name="$(jq -r '.name' <<< "$deb_sha256_data")"

  [[ "$deb_name" == *"rc"* || "$deb_name" == *"bitcore"* ]] && is_valid=false
  [[ "$deb_asc_name" == *"rc"* || "$deb_asc_name" == *"bitcore"* ]] && is_valid=false
  [[ "$deb_sha256_name" == *"rc"* || "$deb_sha256_name" == *"bitcore"* ]] && is_valid=false

  if [ "$is_valid" = true ]; then
    break
  fi
done < <(jq -c '.[]' <<< "$json_data")

curl -sL "${deb_url}" --output "${deb_name}" || { val="$?"; echo "Error: was not able to download ${deb_name}."; exit $val; }
curl -sL "${deb_asc_url}" --output "${deb_asc_name}" || { val="$?"; echo "Error: was not able to download ${deb_asc_name}."; exit $val; }
curl -sL "${deb_sha256_url}" --output "${deb_sha256_name}" || { val="$?"; echo "Error: was not able to download ${deb_sha256_name}."; exit $val; }

echo "" && echo "=== Checksum verification===" && echo ""
if sha256sum -c "$deb_sha256_name"; then
  echo "Checksum verification passed."
else
  echo "Checksum verification failed."
  exit 1
fi

# Step 2
echo "" && echo "=== Extract debian package content" && echo ""
ZEN_CONTENT_FOLDER=${deb_name}-contents
dpkg -x "${deb_name}" "${ZEN_CONTENT_FOLDER}" || { retval="$?"; echo "Error: was not able to extract package ${deb_name} to directory ${ZEN_CONTENT_FOLDER}."; exit $retval; }

# Step 3
echo "" && echo "=== Export BITCOINCLI, BITCOIND and SIDECHAIN_SDK path as env vars, needed for python tests" && echo ""
BITCOINCLI="${CURRENT_DIR}/${ZEN_CONTENT_FOLDER}/usr/bin/zen-cli"
BITCOIND="${CURRENT_DIR}/${ZEN_CONTENT_FOLDER}/usr/bin/zend"
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

# Step 4
echo "" && echo "=== Fetch zen params" && echo ""
${CURRENT_DIR}/${ZEN_CONTENT_FOLDER}/usr/bin/zen-fetch-params || { retval="$?"; echo "Error: was not able to fetch zen params."; exit $retval; }

# Step 5
echo "" && echo "=== Building SideChain SDK ===" && echo ""
mvn clean install -Dmaven.test.skip=true || { retval="$?"; echo "Error: was not able to complete mvn clean install of Sidechain SDK."; exit $retval; }

# Step 6
echo "" && echo "=== Installing node 16.0.0 ===" && echo ""
source ~/.nvm/nvm.sh
nvm install v16.0.0 || { retval="$?"; echo "Error: was not able to nvm install node 16.0.0"; exit $retval; }

# Step 7
echo "" && echo "=== Installing yarn ===" && echo ""
npm install --global yarn || { retval="$?"; echo "Error: was not able to install yarn with npm install."; exit $retval; }

# Step 8
echo "" && echo "=== Installing Python dependencies ===" && echo ""
pip install --no-cache-dir --upgrade pip
pip install --no-cache-dir -r ./requirements.txt
cd qa/
pip install --no-cache-dir -r ./SidechainTestFramework/account/requirements.txt

# Step 9
echo "" && echo "=== Run tests ===" && echo ""
"${test_cmd}" "${test_args}"