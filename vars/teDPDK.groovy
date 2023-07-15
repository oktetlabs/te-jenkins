// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for working with DPDK repository.

import java.util.regex.Matcher

// Detect DPDK repository URL and branch name by name of the job.
// This function can set DPDK_GIT_URL and DPDK_DEF_BRANCH in
// the context.
//
// Args:
//   ctx: pipeline context
//   name: job name
def detect_url_branch(ctx, String name) {
    if (!ctx.DPDK_GIT_URL) {
        switch (name - ~"^.*/") {
            case 'dpdk':
                ctx.DPDK_GIT_URL = 'git://dpdk.org/dpdk'
                break
            case ~/^dpdk-stable-.*/:
                ctx.DPDK_GIT_URL = 'git://dpdk.org/dpdk-stable'
                break
            case 'dpdk-next-net':
                ctx.DPDK_GIT_URL = 'git://dpdk.org/next/dpdk-next-net'
                break
            case 'dpdk-next-virtio':
                ctx.DPDK_GIT_URL='git://dpdk.org/next/dpdk-next-virtio'
                break
        }
    }

    if (!ctx.DPDK_DEF_BRANCH) {
        switch (name - ~"^.*/") {
            case ~/^dpdk-stable-(.*)/:
                ctx.DPDK_DEF_BRANCH = Matcher.lastMatcher[1]
                break
            default:
                ctx.DPDK_DEF_BRANCH = 'main'
        }
    }
}

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
