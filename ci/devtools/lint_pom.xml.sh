#!/bin/bash

base_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )/../..";
pom_xml_locations=("${base_dir}" "${base_dir}/sdk" "${base_dir}/examples/utxo/simpleapp" "${base_dir}/tools/sctool")

# Conditional for evm branch
if [ -d "${base_dir}/libevm" ]; then
  pom_xml_locations+=("${base_dir}/evm" "${base_dir}/libevm" "${base_dir}/examples/account/evmapp")
else
  pom_xml_locations+=("${base_dir}/tools/dbtool")
fi

for location in "${pom_xml_locations[@]}"; do
  CONTENT="$(xmllint --format --encode UTF-8 "${location}"/pom.xml)"
  echo "${CONTENT}" > "${location}/pom.xml"
done

SETTINGS_CONTENT="$(xmllint --format --encode UTF-8 ci/mvn_settings.xml)"
echo "${SETTINGS_CONTENT}" > ci/mvn_settings.xml
