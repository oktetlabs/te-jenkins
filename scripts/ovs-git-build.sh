#!/bin/bash

set -e

unset OPTS
case "$(uname -s)" in
    Linux)
        make=make ;;
    FreeBSD)
        make=gmake ;;
esac

test -n "$1" && OPTS="${OPTS} $@"

#
# Build
#
./boot.sh
./configure CFLAGS="-g -O2 -march=native" ${OPTS}
${make}
