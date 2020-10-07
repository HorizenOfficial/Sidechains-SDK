#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )/../.."
for file in "${ROOT_DIR}/examples/simpleapp/pom.xml" "${ROOT_DIR}/pom.xml" "${ROOT_DIR}/sdk/pom.xml" "${ROOT_DIR}/tools/sctool/pom.xml"; do
  CONTENT="$(xmllint --format --encode UTF-8 "${file}")"
  echo "${CONTENT}" > "${file}"
done
