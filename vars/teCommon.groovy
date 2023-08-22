// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Generic helper functions.

// Create TE pipeline context. It is used as a delegate map
// for a closure which is passed to pipeline templates,
// and is also passed to various functions of this
// library.
//
// Return:
//   Map to use as a pipeline context
def create_pipeline_ctx(env, params) {
    def dlg = [:]

    // Using DELEGATE_FIRST makes parts of this library not
    // available inside the closure unless we set references
    // to them in the delegate object.

    dlg.teRun = teRun
    dlg.teEmail = teEmail
    dlg.teMeta = teMeta
    dlg.teRevData = teRevData
    dlg.teDPDK = teDPDK
    dlg.teOVS = teOVS
    dlg.teCommon = this

    dlg.env = env
    dlg.params = params

    if (env.JOB_NAME) {
        // Having job name without full path can be better
        // for logging.
        dlg.JOB_LAST_NAME = env.JOB_NAME.replaceAll(/^.*\//, '')
    }

    // Having reference to this delegate itself makes it
    // simpler to use some API methods
    dlg.teCtx = dlg

    dlg.metas = [:]

    teRevData.init_ctx(dlg)

    return dlg
}

// Construct list of triggers from list of jobs.
//
// Args:
//   jobs: comma-separated list of job names
//
// Return:
//   List of triggers asking to build the current pipeline
//   when one of the jobs in the list succeeds.
def jobs_triggers(String jobs) {
    def triggers = []

    jobs.tokenize(',').each {
        job_name ->
        triggers += [
            upstream(upstreamProjects: job_name,
                     threshold: hudson.model.Result.SUCCESS) ]
    }

    return triggers
}

// Get all nodes having specific label.
//
// Args:
//   label: label to look for
//
// Return: list of node names
@NonCPS
def getNodes(String label) {
    def nodes = jenkins.model.Jenkins.instance.nodes

    def filteredNodes = nodes.findAll { node ->
        node.labelString.contains("${label}")
    }

    return filteredNodes.collect { node ->
        node.name
    }
}

// Check out sources of this library.
//
// Args:
//   do_poll: if true, this checkout should be taken into
//            account when polling SCM
def checkout_lib(Boolean do_poll = false) {
    dir('te-jenkins') {
        // changelog should not influence polling, but in fact
        // it does.
        checkout(scm: scm, poll: do_poll, changelog: do_poll)
    }
}

// Execute a given code on all nodes with a given label.
//
// Args:
//   label: label to look for
//   body: closure with code to execute
def do_on_nodes(String label, Closure body) {
    def actions = [:]
    def nodes = getNodes(label)

    for (p in nodes) {
        // Creating this variable helps to ensure
        // that each closure with node() references
        // different node name.
        def target_node = p
        actions[target_node] = {
            node(target_node) {
                body.call()
            }
        }
    }
    parallel actions
}

// Make a suitable identifier from a string by capitalizing
// it and replacing some characters with '_'.
//
// Args:
//   str: String to make identifier from
//
// Return:
//   identifier
String str2id(String str) {
    return str.toUpperCase().replaceAll(/[^A-Z_0-9]/, /_/)
}
