#!/bin/bash

set -euo pipefail
set +x

# Get the directory of the currently executing script and its parent dir
current_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )";
parent_dir="${current_dir%/*}"

# pom xml files
pom_xml_location="${parent_dir}"
sdk_xml_location="${parent_dir}/sdk"
dbtool_xml_location="${parent_dir}/tools/dbtool"
simpleapp_xml_location="${parent_dir}/examples/simpleapp"
sctool_xml_location="${parent_dir}/tools/sctool"
current_pom_version="$(xpath -q -e '/project/version/text()' "${pom_xml_location}"/pom.xml)"

# .sh file(s)
run_sc_file="${parent_dir}/ci/run_sc.sh"

# pythong file(s)
scutil_file="${parent_dir}/qa/SidechainTestFramework/scutil.py"
sc_test_framework_file="${parent_dir}/qa/SidechainTestFramework/sc_test_framework.py"

# .md files
mc_sc_workflow_file="${parent_dir}/examples/simpleapp/mc_sc_workflow_example.md"
simpleapp_readme_file="${parent_dir}/examples/simpleapp/README.md"
main_readme_file="${parent_dir}/README.md"

# Functions
function fn_die() {
  echo -e "${1}" >&2
  exit "${2:-1}"
}

function usage() {
  cat << BLOCK
  Usage: Provide OLD and NEW versions as the 1st and 2nd arguments respectively.
         It has to match the following format:
         DIGIT.DIGIT.DIGIT, DIGIT.DIGIT.DIGIT-SNAPSHOT or DIGIT.DIGIT.DIGIT-SNAPSHOTDIGIT

         For example:
         ./set_version.sh 5.5.5 5.5.5-SNAPSHOT
         ./set_version.sh 5.5.5 5.5.5-SNAPSHOT1
         ./set_version.sh 5.5.5-SNAPSHOT 5.5.5
BLOCK
  fn_die "Exiting ..."
}

# Checking for exact amount of arguments as the first step
if [[ $# -eq 2 ]]; then
    version_old="${1}"
    version_new="${2}"
else
    usage
fi

# Checking the format of the versions
if ! [[ "${version_old}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT[0-9]*)?$ ]]; then
  usage
fi

if ! [[ "${version_new}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT[0-9]*)?$ ]]; then
  usage
fi

# Changing version numbers under pom.xml files
for file in "${pom_xml_location}" "${sdk_xml_location}" "${dbtool_xml_location}" "${simpleapp_xml_location}" "${sctool_xml_location}"; do
  # Checking if OLD version matches with the CURRENT version in pom file(s)
  current_pom_version="$(xpath -q -e '/project/version/text()' "${file}"/pom.xml)"
  if [ "${version_old}" != "${current_pom_version}" ]; then
    fn_die "Fix it! The OLD version does not match with CURRENT version under ${file}/pom.xml file\nCurrent version is: ${current_pom_version}"
  fi

  echo "" && echo "=== Modifying pom file under ${file} location ===" && echo ""

  cd "${file}" && mvn versions:set -DnewVersion="${version_new}"

  # shellcheck disable=SC1014
  if grep -cq '<artifactId>sidechains-sdk</artifactIds>' "${file}/pom.xml"; then
    mvn versions:use-dep-version -Dincludes=io.horizen:sidechains-sdk -DdepVersion="${version_new}" -DforceVersion=true
  fi
done

# Changing version under other file(s)
for file in "${scutil_file}" "${sc_test_framework_file}" "${mc_sc_workflow_file}" "${simpleapp_readme_file}" "${main_readme_file}" "${run_sc_file}"; do
  # Checking if OLD version matches with the CURRENT version in pom file(s)
  if [ "${file}" != "${run_sc_file}" ]; then
    if ! grep -cq "sidechains.*${version_old}" "${file}"; then
      current_jar_version="$(grep -Eoh 'sidechains-sdk.*[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT[0-9]*)?' "${file}" | cut -d '-' -f4- | head -n1)"
      fn_die "Fix it! The OLD version does not match with CURRENT version under ${file} file\nCurrent version is: ${current_jar_version}"
    fi
  elif [ "${file}" = "${run_sc_file}" ];then
    if ! grep -cq "SIMPLE_APP_VERSION:-${version_old}" "${file}"; then
      current_jar_version="$(grep -Eioh 'SIMPLE_APP_VERSION:-[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT[0-9]*)?' "${file}" | cut -d '-' -f2-)"
      fn_die "Fix it! The OLD version does not match with CURRENT version under ${file} file\nCurrent version is: ${current_jar_version}"
    fi
  fi

  echo "" && echo "=== Modifying ${file} file ===" && echo ""
  sed -i "s/${version_old}/${version_new}/g" "${file}"
done

echo "" && echo "=== DONE ===" && echo ""
echo -e "OLD version: ${version_old}\nNEW version: ${version_new}"

exit 0

