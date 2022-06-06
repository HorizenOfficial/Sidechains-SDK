#!/bin/bash

set -euo pipefail

echo "======================================================================"
echo "|                           Building jars                            |"
echo "======================================================================"
echo
mvn clean package -DskipTests=true -B
echo "======================================================================"
echo "|                      Building jars completed                       |"
echo "======================================================================"
echo

echo "======================================================================"
echo "|                        Running build tests                         |"
echo "======================================================================"
echo
mvn test -B
echo "======================================================================"
echo "|                       Build tests completed                        |"
echo "======================================================================"
echo

if [ "$CONTAINER_PUBLISH" = "true" ]; then
  cd sdk
  echo "======================================================================"
  echo "|              Deploying sdk bundle to maven repository              |"
  echo "======================================================================"
  echo
  #mvn deploy -P sign,build-extras --settings ../ci/mvn_settings.xml -DskipTests=true -B
  echo "======================================================================"
  echo "|         Deploying sdk bundle to maven repository completed         |"
  echo "======================================================================"
  echo
fi
