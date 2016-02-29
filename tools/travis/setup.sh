#!/bin/bash

# Setup environment for a build in Travis-CI.

SCRIPTDIR=$(cd $(dirname "$0") && pwd)
ROOTDIR="$SCRIPTDIR/../.."

: ${OPEN_WHISK_DB_USERNAME:=dummy}
: ${OPEN_WHISK_DB_PASSWORD:=dummy}

# Generate dummy cloudant env file.
if [ ! -f "$ROOTDIR/cloudant-local.env" ]; then
	echo OPEN_WHISK_DB_USERNAME=dummy > "$ROOTDIR/cloudant-local.env"
	echo OPEN_WHISK_DB_PASSWORD=dummy >> "$ROOTDIR/cloudant-local.env"
fi
cat "$ROOTDIR/cloudant-local.env" |head -1

# Install necessary tools.
(
    cd ./tools/ubuntu-setup
    ./misc.sh && ./pip.sh && ./ant.sh && ./scala.sh
)

# Setup docker
sudo -E bash -c 'echo '\''DOCKER_OPTS="-H tcp://0.0.0.0:4243 -H unix:///var/run/docker.sock --api-enable-cors --storage-driver=aufs"'\'' >> /etc/default/docker'
sudo gpasswd -a travis docker
sudo service docker restart



