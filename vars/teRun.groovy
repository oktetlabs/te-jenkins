// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for working with TE/TS/TS Conf/TS Rigs.

// Determine GIT URL for a given component (te/tsconf/tsrigs/ts),
// taking into account parameters and environment.
//
// Args:
//   ctx: pipeline context
//   component: component name
//
// Return:
//   URL
def get_url(ctx, component) {
    def url
    def var_name

    // For example, if component = 'te', we try the
    // following sources (stopping once we found
    // defined value):
    //
    // params.te_repo
    // TE_GIT_URL in context
    // TE_GIT_URL in environment

    var_name = "${component}_GIT_URL".toUpperCase()
    url = params["${component}_repo"] ?:
          ctx[var_name] ?: env[var_name]
    url = url.replaceAll(/__USER__/, "${env.USER}")

    return url
}

// Determine revision for a given component (te/tsconf/tsrigs/ts),
// taking into account parameters and environment.
//
// Args:
//   ctx: pipeline context
//   component: component name
//
// Return:
//   Revision
def get_rev(ctx, component) {
    def rev
    def var_name

    // For example, if component = 'te', we try the
    // following sources (stopping once we found
    // defined value):
    //
    // params.te_rev
    // TE_REV in context
    // TE_REV in environment
    // params.te_branch

    var_name = "${component}_rev".toUpperCase()
    rev = params["${component}_rev"] ?:
          ctx[var_name] ?: env[var_name] ?:
          params["${component}_branch"] ?: 'main'

    return rev
}

// Checkout TE
//
// Args:
//   ctx: pipeline context
//   revision: branch, tag or SHA-1 (if null, get_rev() determines
//             revision)
//   target: target directory
//
// Return:
//   List of checkout details as per GitSCM module.
def te_checkout(ctx, String revision = null,
                String target = 'te') {
    def scm_vars
    def url = get_url(ctx, "te")
    def rev = revision ?: get_rev(ctx, "te")

    if (!url) {
        error "TE repository URL is not defined"
    }

    dir(target) {
        scm_vars = git_checkout(url, rev)

        // TE_BASE is required for TE build.
        env.TE_BASE = pwd()
        ctx.metas.TE_REV = git_get_rev()
    }

    teRevData.store_value(ctx.all_revs, "te", "TE_GIT_URL", url)
    teRevData.store_value(ctx.all_revs, "te", "TE_REV", ctx.metas.TE_REV)

    return scm_vars
}

// Checkout Test Suite.
//
// Args:
//   ctx: pipeline context
//   url: source URL (if null, get_url() determines URL)
//   revision: branch, tag or SHA-1 (if null, get_rev()
//             determines revision)
//
// Return:
//   List of checkout details as per GitSCM module.
def ts_checkout(ctx, String url = null,
                String revision = null) {
    def scm_vars
    String rev = revision ?: get_rev(ctx, "ts")
    String repo = url ?: get_url(ctx, "ts")

    if (!repo) {
        error "Test Suite repository URL is not defined"
    }

    dir(ctx.ts_name) {
        scm_vars = git_checkout(repo, rev)

        ctx.TS_SRC = pwd()
        ctx.metas.TS_REV = git_get_rev()
    }

    teRevData.store_value(ctx.all_revs, "ts", "TS_GIT_URL", repo)
    teRevData.store_value(ctx.all_revs, "ts", "TS_REV", ctx.metas.TS_REV)

    return scm_vars
}

// Checkout TS Conf.
//
// Args:
//   ctx: pipeline context
//   revision: branch, tag or SHA-1 (if null, get_rev()
//             determines revision)
//   target: target directory
def tsconf_checkout(ctx, String revision = null,
                    String target = 'ts-conf') {
    def scm_vars
    def url = get_url(ctx, "tsconf")
    def rev = revision ?: get_rev(ctx, "tsconf")

    if (!url) {
        error "ts-conf repository URL is not defined"
    }

    dir(target) {
        scm_vars = git_checkout(url, rev)

        ctx.metas.TSCONF_REV = git_get_rev()
    }

    teRevData.store_value(ctx.all_revs, "tsconf", "TSCONF_GIT_URL", url)
    teRevData.store_value(ctx.all_revs, "tsconf", "TSCONF_REV",
                          ctx.metas.TSCONF_REV)

    return scm_vars
}

// Checkout TS rigs.
//
// Args:
//   ctx: pipeline context
//   revision: branch, tag or SHA-1 (if null, get_rev()
//             determines revision)
//   target: target directory
def tsrigs_checkout(ctx, String revision = null,
                    String target = 'ts-rigs') {
    def scm_vars
    def url = get_url(ctx, "tsrigs")
    def rev = revision ?: get_rev(ctx, "tsrigs")

    if (!url) {
        error "ts-rigs repository URL is not defined"
    }

    dir(target) {
        scm_vars = git_checkout(url, rev)

        ctx.TSRIGS_DIR = pwd()
        ctx.metas.TSRIGS_REV = git_get_rev()
    }

    teRevData.store_value(ctx.all_revs, "tsrigs", "TSRIGS_GIT_URL", url)
    teRevData.store_value(ctx.all_revs, "tsrigs", "TSRIGS_REV",
                          ctx.metas.TSRIGS_REV)

    return scm_vars
}

// Load site-specific Jenkins configuration from TS Rigs.
//
// Args:
//   ctx: pipeline context
def tsrigs_load(ctx) {
    def tsdefs_path
    def common_env_path
    def common_env

    // Export common environment variables
    common_env_path = "${ctx.TSRIGS_DIR}/jenkins/"
    common_env_path += "common.groovy"

    if (fileExists(common_env_path)) {
        common_env = load "${common_env_path}"
        common_env.set_defs(ctx)
    }

    // Getting TS-specific variables
    tsdefs_path = "${ctx.TSRIGS_DIR}/jenkins/defs/"
    tsdefs_path += "${ctx.ts_name}/defs.groovy"

    if (fileExists(tsdefs_path)) {
        def tsdefs = load tsdefs_path

        tsdefs.set_defs(ctx)

        if (ctx.TS_MAIL_FROM) {
            teEmail.email_set_from(ctx.TS_MAIL_FROM)
        }

        if (ctx.TS_MAIL_TO) {
            teEmail.email_add_to(ctx.TS_MAIL_TO)
        }
    }
}

// Run Test suite with specific options.
//
// Args:
//   ctx: pipeline context
//   cfg: Optional parameter to set --cfg
//   options: A groovy list with options for run.sh
def run(ctx, String cfg, options) {
    dir('run') {

        def start_options = []
        Boolean rm_te_logs = false
        String te_logs

        env.TE_BUILD = "${env.WORKSPACE}/build"

        if (cfg != "") {
            start_options.add("--cfg=${cfg}")
        }

        if (fileExists(env.TE_WORKSPACE_DIR)) {
            rm_te_logs = true
            env.TE_TMP = sh(
              script: "mktemp -d ${env.TE_WORKSPACE_DIR}/te_tmp.${cfg}.XXX",
              returnStdout: true).trim()
            te_logs = sh(
              script: "mktemp -d ${env.TE_WORKSPACE_DIR}/te_log.${cfg}.XXX",
              returnStdout: true).trim()
            start_options.add("--log-dir=${te_logs}")
        } else {
            te_logs = pwd()
        }

        env.TE_LOG_RAW="${te_logs}/log.raw"
        env.TE_LOG_BUNDLE="${te_logs}/raw_log_bundle.tpxz"

        def opts = start_options.join(' ') + ' ' + options.join(' ')
        def run_sh_err = null

        // JUnit log can be used to obtain testing statistics after
        // tests are run (see statistics() here).
        opts += " --log-junit=log.junit"
        // TRC statistics in text form is used by Bublik.
        opts += " --trc-txt=trc-stats.txt"
        try {
            sh "${ctx.TS_SRC}/scripts/run.sh ${opts}"
        } catch (err) {
            run_sh_err = err
        } finally {
            if (rm_te_logs) {
                if (fileExists(env.TE_LOG_BUNDLE)) {
                    sh "mv ${env.TE_LOG_BUNDLE} ."
                }
                sh "rm -rf '${te_logs}'"
            }

            if (run_sh_err != null)
                error('ERROR: run.sh failed with: ' + run_sh_err.toString())
        }
    }
}

// Check whether working directory is empty
def is_wdir_empty() {
    def content = sh(script: "ls", returnStdout: true).trim()

    return !content
}

// Save common artifacts for test suite
def archive() {
    dir('run') {
        if (!is_wdir_empty())
            archiveArtifacts artifacts: '**', excludes: 'log.txt'
    }
}

// Cleanup previous run files
def cleanup() {
    sh "rm -rf run"
}

// Strip run build to archive
def strip() {
    dir('run') {
        // Remove secondary log files (which may be generated from
        // raw log bundle).
        sh """
           rm -f log.raw
           rm -f log_plain.xml
           rm -f log_struct.xml
           rm -fr html
           rm -fr caps
           """
    }
}

// Get JUnit statistics about tests.
// This required JUnit Jenkins plugin.
//
// Return:
//    Test statistics object
def statistics() {
    def ts_stats = null

    dir('run') {
        if (fileExists('log.junit')) {
            ts_stats = junit testResults: 'log.junit', allowEmptyResults: true
        }
    }

    return ts_stats
}

// Just a wrapper for checkout from git.
// This requires "Pipeline: SCM step" plugin.
//
// Args:
//   url: URL for checkout
//   revision: branch, tag or SHA-1
//
// Return:
//   List of checkout details as per GitSCM module.
def git_checkout(String url, String revision) {
    def scm_params = [
        $class: 'GitSCM',
        userRemoteConfigs: [[url: url]],
        extensions: [
            [
                $class: 'CloneOption',
                depth: 100,
                noTags: false,
                reference: '',
                shallow: true
            ]
        ],
    ]

    if (revision) {
        scm_params.branches = [[name: revision]]
    }

    return checkout(scm: scm_params)
}

// Get git revision in the current directory.
def git_get_rev() {
    sh(script: "git show --format=%H --quiet", returnStdout: true).trim()
}

// Helper function to get nodes with a given label.
//
// Args:
//    label: label to search
def get_nodes(String label) {
    def nodes = jenkins.model.Jenkins.instance.nodes

    def filteredNodes = nodes.findAll { node ->
        node.labelString.contains("${label}")
    }
    return filteredNodes.collect { node ->
        node.name
    }
}

// Get specifications of repository-related parameters for
// some entity (for example, TE).
//
// Args:
//   params: pipeline parameters
//   entity: entity name
//   prefix: prefix for the related parameters
//   sticky_repo_params: if true, [prefix]_repo becomes sticky and
//                       [prefix]_branch parameter with sticky
//                       default is added.
//
// Return:
//   List of parameters specifications
def entity_params(params, entity, prefix, sticky_repo_params) {
    def specList = [
        string(name: "${prefix}_rev", defaultValue: '',
               description: "${entity} revision (overwrites branch)")
    ]

    if (sticky_repo_params) {
        specList.addAll([
            string(name: "${prefix}_branch",
                   defaultValue: params["${prefix}_branch"],
                   description: "${entity} branch (sticky default)"),
            string(name: "${prefix}_repo",
                   defaultValue: params["${prefix}_repo"],
                   description: "${entity} repository (sticky default)")
                   ])
    } else {
        specList.addAll([
            string(name: "${prefix}_repo",
                   description: "${entity} repository")
                   ])
    }

    return specList
}

// Get specifications of pipeline parameters for setting revisions,
// branches and repositories.
//
//  Args:
//    params: current pipeline parameters
//    tmplParams: pipeline template parameters
//
//  Return:
//    list of parameters specifications
def get_repo_params(params, tmplParams) {

    def paramsList = []

    paramsList.addAll(entity_params(params, "Test Environment", "te",
                                    tmplParams.sticky_repo_params))
    paramsList.addAll(entity_params(params, "Test Suite", "ts",
                                    tmplParams.sticky_repo_params))

    if (tmplParams.tsconf) {
        paramsList.addAll(entity_params(
                              params,
                              "Test suites shared configuration", "tsconf",
                              tmplParams.sticky_repo_params))
    }

    paramsList.addAll(entity_params(params, "Site-specific configuration",
                                    "tsrigs", tmplParams.sticky_repo_params))

    return paramsList
}

// Set LOGS_PATH (path to logs in logs storage) and
// HTML_LOGS (URL to logs in web interface) variables
// in pipeline context.
//
// Args:
//   ctx: pipeline context
def define_logs_paths(ctx) {
    if (!ctx.LOGS_PATH && !ctx.TS_LOGS_SUBPATH) {
        return
    }

    ctx.LOGS_PATH = ctx.LOGS_PATH ?:
                    ctx.TS_LOGS_SUBPATH + ctx.metas.CAMPAIGN_DATE +
                    '/' + params.ts_cfg + '-' + env.BUILD_NUMBER

    if (ctx.TS_LOGS_URL_PREFIX) {
        ctx.HTML_LOGS = ctx.TS_LOGS_URL_PREFIX +
                        ctx.LOGS_PATH + '/'
    } else {
        ctx.HTML_LOGS = ""
    }
}

// Check whether node with a given label is available.
// This helps to avoid indefinite hanging of Jenkins job.
// If label is not found, this function aborts the current
// pipeline with error message.
//
// Args:
//   label: node label to check
def check_label(label) {
    Boolean label_exists = false

    jenkins.model.Jenkins.instance.nodes.find {
        node ->

        if (node.labelString.contains(label)) {
            label_exists = true
            return true
        }

        return false
    }

    if (!label_exists) {
        error("Cannot find node with label ${label}")
    }
}

// Publish testing logs (copy them to specified location and
// export them to Bublik web application).
//
// LOGS_PATH variable should be set to the location
// where copy of the logs should be saved.
// HTML_LOGS variable should be set to URL of the logs
// to make export to Bublik possible.
// define_logs_paths() can be used to set these variables.
//
// Export to Bublik happens only when TS_BUBLIK_URL is set in
// test suite specific defines.
//
// Args:
//   ctx: pipeline context
def publish_logs(ctx) {
    if (ctx.PUBLISH_LOGS_NODE) {
        check_label(ctx.PUBLISH_LOGS_NODE)

        build(job: 'publish-logs', wait: true,
            parameters: [
                [ $class: 'StringParameterValue',
                  name: 'job_to_copy_from',
                  value: env.JOB_NAME ],
                [ $class: 'StringParameterValue',
                  name: 'publish_to',
                  value: ctx.LOGS_PATH ],
                [ $class: 'StringParameterValue',
                  name: 'logs_node',
                  value: ctx.PUBLISH_LOGS_NODE ],
                [ $class: 'StringParameterValue',
                  name: 'art_srv_id',
                  value: ctx.TS_ARTIFACTORY ],
                [ $class: 'StringParameterValue',
                  name: 'art_repo_name',
                  value: ctx.TS_ARTIFACTORY_REPO ]
            ])

        if (ctx.TS_BUBLIK_URL && ctx.HTML_LOGS) {
            check_label('bublik-import')
            build(job: 'bublik-import', wait: true,
                parameters: [
                    [ $class: 'StringParameterValue',
                      name: 'bublik_url',
                      value: ctx.TS_BUBLIK_URL ],
                    [ $class: 'StringParameterValue',
                      name: 'logs_url', value: ctx.HTML_LOGS ]
                ])
        }
    }
}
