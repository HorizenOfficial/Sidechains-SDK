FROM ubuntu:bionic

MAINTAINER infrastructure@zensystem.io

SHELL ["/bin/bash", "-c"]

COPY entrypoint.sh entrypoint_setup_gpg.sh /usr/local/bin/

# Get Ubuntu packages
RUN set -eux && export GOSU_VERSION=1.12 && export DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl dirmngr gnupg2 \
    && dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')" \
    && curl -L "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch" > /usr/local/bin/gosu \
    && curl -L "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch.asc" >  /usr/local/bin/gosu.asc \
    && export GNUPGHOME="$(mktemp -d)" \
    && ( gpg2 --batch --keyserver hkps://keys.openpgp.org --keyserver-options timeout=15 --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 || \
    gpg2 --batch --keyserver keyserver.ubuntu.com --keyserver-options timeout=15 --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 || \
    gpg2 --batch --keyserver hkp://p80.pool.sks-keyservers.net:80 --keyserver-options timeout=15 --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 || \
    gpg2 --batch --keyserver hkp://ipv4.pool.sks-keyservers.net --keyserver-options timeout=15 --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 || \
    gpg2 --batch --keyserver hkp://pgp.mit.edu:80 --keyserver-options timeout=15 --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 ) \
    && gpg2 --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu \
    && command -v gpgconf && ( gpgconf --kill all || true ) \
    && rm -rf "$GNUPGHOME" /usr/local/bin/gosu.asc \
    && chmod +x /usr/local/bin/gosu \
    && gosu --version \
    && gosu nobody true \
    && chmod +x /usr/local/bin/{entrypoint.sh,entrypoint_setup_gpg.sh} \
    && apt-get -y clean \
    && apt-get -y autoclean \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

