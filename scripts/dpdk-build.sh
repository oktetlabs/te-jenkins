#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2022-2023 OKTET Labs Ltd. All rights reserved.
#
# Script used to build DPDK.

PREFIX="$1"

echo "Building dpdk on host $(hostname)"

set -e

# Load configuration and helper functions
MYDIR="$(cd "$(dirname "$(which "$0")")" ; pwd -P)"
source "${MYDIR}"/functions


function meson_build() {
    local build="$1" ; shift
    local meson_args=()

    test -z "${PREFIX}" || meson_args+=(--prefix "${PREFIX}" --libdir lib)

    meson "${meson_args[@]}" "${build}" || return 1
    ninja_build -C "${build}" || return 1
    if test -n "${PREFIX}" ; then
        ninja_build install -C "${build}" || return 1
    fi
    ln -snf "${build}" build
    return 0
}

meson_build meson_build
