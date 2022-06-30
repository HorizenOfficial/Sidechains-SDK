#!/bin/bash

base_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )/../..";

for location in "${base_dir}" "${base_dir}/skd" "${base_dir}/tools/dbtool" "${base_dir}/tools/sctool" "${base_dir}/examples/simpleapp"; do
  CONTENT="$(xmllint --format --encode UTF-8 "${location}"/pom.xml)"
  echo "${CONTENT}" > "${location}/pom.xml"
done

SETTINGS_CONTENT="$(xmllint --format --encode UTF-8 ci/mvn_settings.xml)"
echo "${SETTINGS_CONTENT}" > ci/mvn_settings.xml
