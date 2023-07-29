// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for working with DPDK repository.

import java.util.regex.Matcher

// Clone DPDK repository.
//
// Args:
//   map of arguments (see the second variant of this function for
//   descriptions)
//
// Return:
//   List of checkout details as per GitSCM module.
def checkout(Map args) {
    return teRun.generic_checkout(ctx: args.ctx, component: 'dpdk',
                                  url: args.url, revision: args.revision,
                                  target: args.target ?: 'dpdk',
                                  do_poll: args.do_poll == null ?
                                                    true : args.do_poll)
}

// Clone DPDK repository.
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
             String target = 'dpdk', Boolean do_poll = true) {

    return teDPDK.checkout(
                      ctx: ctx, url: url, revision: revision,
                      target: target, do_poll: do_poll)
}

// Build DPDK.
//
// Args:
//   target: directory with DPDK sources
//   prefix: installation prefix
def build(String target = 'dpdk', String prefix = '') {
    dir(target) {
        sh """
           "${env.WORKSPACE}"/te-jenkins/scripts/dpdk-build.sh ${prefix}
           """
    }
}
