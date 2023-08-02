// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for working with Open vSwitch repository.

// Clone Open vSwitch repository.
//
// Args:
//   map of arguments (see the second variant of this function for
//   descriptions)
//
// Return:
//   List of checkout details as per GitSCM module.
def checkout(Map args) {
    return teRun.generic_checkout(ctx: args.ctx, component: 'ovs',
                                  url: args.url, revision: args.revision,
                                  target: args.target ?: 'ovs',
                                  do_poll: args.do_poll == null ?
                                                    true : args.do_poll)
}

// Clone Open vSwitch repository.
//
// Args:
//   ctx: pipeline context
//   url: repository URL
//   revision: branch, tag or SHA-1
//   target: target directory
//   do_poll: if true, this checkout should be taken into account by
//            pollSCM() trigger
//
// Return:
//   List of checkout details as per GitSCM module.
def checkout(ctx, String url = null, String revision = null,
             String target = 'ovs', Boolean do_poll = true) {

    return teOVS.checkout(
                      ctx: ctx, url: url, revision: revision,
                      target: target, do_poll: do_poll)
}

// Build Open vSwitch.
//
// Args:
//   target: directory with Open vSwitch sources
//   dpdk_prefix: DPDK installation prefix
def build(String target = 'ovs', String dpdk_prefix = '') {
    dir(target) {
        def opts = ''
        if (dpdk_prefix) {
            env.PKG_CONFIG_PATH = dpdk_prefix + '/lib/pkgconfig'
            opts = '--with-dpdk=yes'
        }
        sh """
           "${WORKSPACE}"/te-jenkins/scripts/ovs-git-build.sh ${opts}
           """
    }
}
