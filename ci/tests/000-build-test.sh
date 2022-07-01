#!/bin/bash

set -eo pipefail
retval=0

# Building jar package
echo "" && echo "=== Building jar ===" && echo ""

mvn clean package -DskipTests=true -B || retval="$?"

# Running mvn tests
echo "" && echo "=== Running maven build tests ===" && echo ""
mvn test -B || retval="$?"

exit "$retval"

