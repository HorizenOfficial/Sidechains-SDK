#!/bin/bash
set -euo pipefail

# check required vars are set
if [ -z "${CONTAINER_JAVA_VER}" ] || [ -z "${CONTAINER_SCALA_VER}" ] || [ -z "${CONTAINER_SCALA_DEB_SHA256SUM}" ]; then
  echo "CONTAINER_JAVA_VER, CONTAINER_SCALA_VER and CONTAINER_SCALA_DEB_SHA256SUM environment variables need to be set!"
  exit 1
fi

# Add local zenbuilder user
# Either use LOCAL_USER_ID:LOCAL_GRP_ID if set via environment
# or fallback to 9001:9001

USER_ID=${LOCAL_USER_ID:-2000}
GRP_ID=${LOCAL_GRP_ID:-2000}

getent group zenbuilder > /dev/null 2>&1 || groupadd -g $GRP_ID zenbuilder
id -u zenbuilder > /dev/null 2>&1 || useradd --shell /bin/bash -u $USER_ID -g $GRP_ID -o -c "" -m zenbuilder

LOCAL_UID=$(id -u zenbuilder)
LOCAL_GID=$(getent group zenbuilder | cut -d ":" -f 3)

if [ ! "$USER_ID" == "$LOCAL_UID" ] || [ ! "$GRP_ID" == "$LOCAL_GID" ]; then
    echo "Warning: User zenbuilder with differing UID $LOCAL_UID/GID $LOCAL_GID already exists, most likely this container was started before with a different UID/GID. Re-create it to change UID/GID."
fi

echo "Starting with UID/GID: $LOCAL_UID:$LOCAL_GID"

export HOME=/home/zenbuilder

# Get Java $CONTAINER_JAVA_VER
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y "$CONTAINER_JAVA_VER" maven
apt-get -y clean
apt-get -y autoclean
rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb

# Get Scala $CONTAINER_SCALA_VER
file_name="scala-${CONTAINER_SCALA_VER}.deb"
curl "https://scala-lang.org/files/archive/${file_name}" > "/tmp/${file_name}"
sha256sum -c <(echo "${CONTAINER_SCALA_DEB_SHA256SUM}  /tmp/${file_name}")
dpkg -i "/tmp/${file_name}"
rm "/tmp/${file_name}"

export JAVA_HOME="$(readlink -f "$(which javac)" | sed "s:/bin/javac::")"
gosu zenbuilder java -version
gosu zenbuilder scala -version

# Fix ownership recursively
chown -RH zenbuilder:zenbuilder /build

exec gosu zenbuilder /usr/local/bin//entrypoint_setup_gpg.sh "$@"

