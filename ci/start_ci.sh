#!/bin/bash

set -eo pipefail

echo "execute the build script $(date)"

cd /build && ./ci/build_jar.sh

echo "done $(date)"
