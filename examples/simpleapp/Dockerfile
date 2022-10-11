## Build stage

# Global ARGS

ARG FROM_IMAGE_BUILD=zencash/sc-ci-base:focal_jdk-11_latest

ARG FROM_IMAGE_RUN=eclipse-temurin:11-jre-focal

FROM $FROM_IMAGE_BUILD as simpleapp-builder

LABEL mantainer="infrastructure@zensystem.io"

# Scoped ARGS

ARG SDK_COMMITTISH=dockerize_simpleapp

SHELL ["/bin/bash", "-c"]

RUN set -euo pipefail && apt-get update \
    && export DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get -y --no-install-recommends install apt-utils \
    && apt-get -y --no-install-recommends dist-upgrade \
    && apt-get -y --no-install-recommends install cmake git gnupg1-curl maven\
    && git clone https://github.com/HorizenOfficial/Sidechains-SDK.git /Sidechains-SDK && cd /Sidechains-SDK \
    && git checkout $SDK_COMMITTISH && mvn install -DskipTests

# Simpleapp stage

FROM ${FROM_IMAGE_RUN} AS simpleapp

LABEL mantainer="infrastructure@zensystem.io"

# Scoped ARGS

ARG ARG_SDK_VERSION=0.5.0
ARG ARG_TINI_VERSION=v0.19.0
ARG ARG_GOSU_VERSION=1.14
ARG ARG_SC_JAR_NAME=sidechains-sdk-scbootstrappingtools
ARG ARG_SC_MAIN_CLASS=com.horizen.examples.simpleapp
ARG ARG_SC_CONF_PATH=/simpleapp/config/simpleapp_settings.conf

ENV SDK_VERSION=${ARG_SDK_VERSION} \
    SC_JAR_NAME=${ARG_SC_JAR_NAME} \
    SC_MAIN_CLASS=${ARG_SC_MAIN_CLASS} \
    SC_CONF_PATH=${ARG_SC_CONF_PATH} \
    GOSU_VERSION=${ARG_GOSU_VERSION} \
    TINI_VERSION=${ARG_TINI_VERSION}
SHELL ["/bin/bash", "-c"]

WORKDIR /simpleapp

COPY --from=simpleapp-builder /Sidechains-SDK/examples/simpleapp/target/*.jar /Sidechains-SDK/tools/sctool/target/sidechains-sdk-scbootstrappingtools-*.jar /simpleapp/
COPY --from=simpleapp-builder /Sidechains-SDK/examples/simpleapp/target/lib /simpleapp/lib
COPY --from=simpleapp-builder /Sidechains-SDK/tools/sctool/target/lib /simpleapp/tools/lib
COPY --from=simpleapp-builder /Sidechains-SDK/tools/sctool/target/*.jar /simpleapp/tools/
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
COPY --from=simpleapp-builder /Sidechains-SDK/examples/simpleapp/src/main/resources/simpleapp_settings.conf /simpleapp/config/simpleapp_settings.conf

RUN set -euo pipefail \
    && chmod +x /usr/local/bin/entrypoint.sh \
    && apt-get update \
    && export DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get -y --no-install-recommends install apt-utils \
    && apt-get -y --no-install-recommends dist-upgrade \
    && apt-get -y --no-install-recommends install cmake git gnupg1-curl \
    && savedAptMark="$(apt-mark showmanual)" \
    && if ! command -v gosu &> /dev/null; then \
      if ! command -v gpg2 &> /dev/null; then \
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends gnupg2 dirmngr; \
      fi \
      && dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')" \
      && curl -sSfL "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch" > /usr/local/bin/gosu \
      && curl -sSfL "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch.asc" > /usr/local/bin/gosu.asc \
      && export GNUPGHOME="$(mktemp -d)" \
      && ( gpg2 --batch --keyserver hkps://keys.openpgp.org --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 || \
       gpg2 --batch --keyserver hkps://keyserver.ubuntu.com:443 --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 || \
       gpg2 --batch --keyserver pgp.mit.edu --recv-key B42F6819007F00F88E364FD4036A9C25BF357DD4 ) \
      && gpg2 --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu \
      && ( gpgconf --kill dirmngr || true ) \
      && ( gpgconf --kill gpg-agent || true ) \
      && rm -rf "$GNUPGHOME" /usr/local/bin/gosu.asc \
      && unset GNUPGHOME \
      && chmod +x /usr/local/bin/gosu \
      && gosu --version; \
    fi \
    && if ! command -v tini &> /dev/null; then \
      if ! command -v gpg2 &> /dev/null; then \
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends gnupg2 dirmngr; \
      fi \
      && curl -sSfL "https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini" > /usr/local/bin/tini \
      && curl -sSfL "https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini.asc" > /usr/local/bin/tini.asc \
      && export GNUPGHOME="$(mktemp -d)" \
      && ( gpg2 --batch --keyserver hkps://keys.openpgp.org --recv-keys 595E85A6B1B4779EA4DAAEC70B588DFF0527A9B7 || \
       gpg2 --batch --keyserver hkps://keyserver.ubuntu.com:443 --recv-keys 595E85A6B1B4779EA4DAAEC70B588DFF0527A9B7 || \
       gpg2 --batch --keyserver pgp.mit.edu --recv-key 595E85A6B1B4779EA4DAAEC70B588DFF0527A9B7 ) \
      && gpg2 --batch --verify /usr/local/bin/tini.asc /usr/local/bin/tini \
      && ( gpgconf --kill dirmngr || true ) \
      && ( gpgconf --kill gpg-agent || true ) \
      && rm -rf "$GNUPGHOME" /usr/local/bin/tini.asc \
      && unset GNUPGHOME \
      && chmod +x /usr/local/bin/tini \
      && tini --version; \
    fi \
    && apt-mark auto '.*' &> /dev/null && [ -z "$savedAptMark" ] || apt-mark manual $savedAptMark &> /dev/null \
    && DEBIAN_FRONTEND=noninteractive apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false \
    && java -jar /simpleapp/"${SC_JAR_NAME}-${SDK_VERSION}.jar" help \
    && rm -rf /var/{lib/apt/lists/*,cache/apt/archives/*.deb} /tmp/*

VOLUME ["/sidechain/datadir/"]
VOLUME ["/sidechain/logs/"]

ENTRYPOINT ["/usr/local/bin/tini", "--", "/usr/local/bin/entrypoint.sh"]

# "java -cp '/sidechain/${ARG_SC_JAR_NAME}-${SDK_VERSION}.jar:/sidechain/lib/*' $SC_MAIN_CLASS $SC_CONF_PATH"
CMD ["/usr/bin/true"]