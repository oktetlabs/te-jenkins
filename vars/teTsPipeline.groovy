// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Pipeline template for running tests.
//
// Required plugins:
//   - Pipeline: Stage Step
//   - Pipeline: SCM step
//   - Timestamper
//   - Email Extension
//   - Copy Artifact
//   - JUnit
//   - Lockable Resources
//
// Pipeline does:
//   1. Call preStartHook if needed (see "Available pipeline hooks")
//   2. Clone TE/TS/TS Conf/TS Rigs with revisions that you define
//   3. Call preRunHook if needed (see "Available pipeline hooks")
//   4. Start run.sh with options that you define (see optionProvider)
//   5. Call postRunHook if needed (see "Available pipeline hooks")
//   6. Save artifacts and post JUnit log
//   7. Trigger downstream jobs if needed
//   8. Generate meta_data.json in always
//   9. Call postAlwaysHook if needed (see "Available pipeline hooks")
//   10. Post email about status of build
//
// Default job parametes
// Note: tsconf_* parameters are present only if tsconf template parameter
// was set to true.
//
//   te_rev: TE revision, can be tag, branch or SHA-1
//   te_repo: TE repository (can also be set in TE_GIT_URL environment
//            variable in Jenkins; value of this parameter can
//            override the environment)
//   ts_rev: TS revision, can be tag, branch or SHA-1
//   ts_repo: test suite repository (can also be set in TS_GIT_URL
//            environment variable in Jenkins; value of this parameter
//            can override the environment)
//   tsconf_rev: TS Conf revision, can be tag, branch or SHA-1
//   tsconf_repo: ts-conf repository (can also be set in TSCONF_GIT_URL
//                environment variable in Jenkins; value of this parameter
//                can override the environment)
//   tsrigs_rev: TS rigs revision, can be tag, branch or SHA-1
//   tsrigs_repo: ts-rigs repository (can also be set in TSRIGS_GIT_URL
//                environment variable in Jenkins; value of this
//                parameter can override the environment)
//   ts_opts: Additional options for run.sh as string
//   with_tce: Boolean to toggle --tce option for run.sh
//   downstream_jobs: Comma-separated list of jobs to be triggered
//                    in case of success (sticky default)
//   get_revs_from: Jobs from which to get revisions of the last
//                  successful build (sticky default). It is assumed
//                  that revisions were saved in an artifact in `all.rev`
//                  file using teRevData API.
//   ts_cfg: Tested configuration name (sticky default)
//   restart_cfg: if true, test hosts should be restarted before
//                running tests. Note: this parameter is not handled
//                here because restarting is site-specific. It
//                can be handled in test suite or ts-rigs.
//   lock: Lock to acquire and hold while running. Empty by default
//         which means that ts_cfg should be used as a lock name.
//         (opaque string, sticky default)
//
// Additional parameters present if sticky_repo_params template parameter
// was set:
//   te_branch: TE branch to use (may be overridden by te_rev or TE_REV;
//              sticky default)
//   ts_branch: TS branch to use (may be overridden by ts_rev or TS_REV;
//              sticky default)
//   tsrigs_branch: ts-rigs branch (may be overridden by tsrigs_rev or
//                  TSRIGS_REV; sticky default)
//   tsconf_branch: ts-conf branch (may be overridden by tsconf_rev or
//                  TSCONF_REV; sticky default)
//
// Available parameters of pipeline template:
//   ts_name: Test suite name.
//   label: String. Execute the Pipeline on an agent available in the Jenkins
//          environment with the provided label. Required.
//   specificParameters: List of additional job parameters which can be
//                       used in test suite specific hooks. Optional.
//   optionsProvider: Closure that returns list of options for run.sh. Required.
//   triggersProvider: Closure that returns list of conditions when job starts
//                     automatically. Optional.
//   tsconf: if true, it is required to clone TS Conf repository (not all
//           test suites may need it)
//   sticky_repo_params: if true, sticky [te|ts|tsconf|tsrigs]_branch
//                       parameters are added and *_repo parameters
//                       become sticky too.
//   publish_logs: if true, logs should be published (copied to permanent
//                 storage, exported to Bublik)
//   concurrent_builds: if true, multiple instances of this pipeline
//                      can be run in parallel. This is useful when
//                      you have a few testing configurations and
//                      want to run tests on them simultaneously.
//                      Lock at run stage should prevent running
//                      two instances of the pipeline on the same
//                      configuration.
//  email_conditions: can be set to a list of conditions when email
//                    should be sent at the end. List can include
//                    the following strings:
//                    - "success": successful run
//                    - "fixed": successful run when the previous one
//                      was unsuccessful
//                    - "unsuccessful": unsuccessful run
//                    If this parameter was not set, email is always
//                    sent. If it is set to empty list, email is
//                    never sent.
//
// Available pipeline hooks (see "Pipeline does" for understanding when hook
// is called):
//   preStartHook: Closure is called before TE/TS/TS Conf/TS Rigs checkout.
//                 Useful for overriding the te/ts/tsconf/tsrigs revisions or
//                 checkout of your tools. Optional parameter.
//                 Pre-start hook must not be used for any kind of operations
//                 which require the resource to be locked (e.g. reboot hosts,
//                 install required SW, configure it etc).
//   preRunHook: Closure is called after TE/TS/TS Conf/TS rigs checkout and
//               before starting run.sh. Useful for preparing your specific
//               environment and tools before running test suite. Optional
//               parameter.
//   restartHook: Closure is called when restart_cfg parameter is set to
//                true.
//   postRunHook: Closure is called after run.sh and before saving artifacts.
//                Useful for archiving your specific artifacts, cleanup.
//                Optional parameter.
//   postAlwaysHook: Closure is called at post stage. Usefull for E-mail etc.
//                   Optional parameter.
//   postUnsuccessfulHook: Closure is called at post stage. Usefull for E-mail etc.
//                   Optional parameter.
//   postCleanupHook: Closure is called at the end of post stage. Usefull for E-mail etc.
//                   Optional parameter.
//   cfgLockHook: If defined, this hook is used to lock testing
//                configuration instead of lock() step when tests are built
//                and run. This hook should expect two parameters - pipeline
//                context and closure to execute under lock. It may be
//                defined in ts-rigs if it should handle site-specific
//                issues (set cfgLockHook field of pipeline context
//                to a closure to do it).
//                The hook is used only when lock pipeline parameter is
//                empty.
//
// See "Pipeline templates" in jenkins/README.md for more information on
// how to use this template.

def call(Closure body) {
    def ctx = teCommon.create_pipeline_ctx(env, params)
    def emailRecipientProviders = []
    def get_revs_from = params.get_revs_from
    def post_conds = []

    // DELEGATE_FIRST means that the delegate is used firstly
    // to resolve properties. So that any property set in closure
    // body will be set in ctx.
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = ctx
    body()

    pipeline {

        agent { label ctx.label }

        options {
            buildDiscarder(logRotator(numToKeepStr: '100', artifactNumToKeepStr: '100'))
            timestamps()
            copyArtifactPermission('*')
            checkoutToSubdirectory('te-jenkins')
        }

        stages {
            stage('Job prepare') {
                steps {
                    script {
                        if (ctx.ts_name) {
                            teEmail.email_add_to_by_ids(ctx, ctx.ts_name)
                        }

                        teEmail.email_start(ctx)
                    }

                    echo "Workspace: ${env.WORKSPACE}"
                    echo "Job: ${env.JOB_NAME}"
                    echo "Host: ${env.NODE_NAME}"

                    script {
                        currentBuild.displayName = "#${BUILD_NUMBER}"
                        if (params.ts_cfg) {
                            currentBuild.displayName += " (${ts_cfg})"
                        }
                    }

                    script {
                        def triggersList = []
                        def paramsList = [
                            string(name: 'ts_opts',
                                   defaultValue: '',
                                   description: 'Additional options for run.sh'),
                            booleanParam(name: 'with_tce',
                                         defaultValue: false,
                                         description: 'Add --tce options for run.sh'),

                            string(name: 'ts_cfg',
                                   defaultValue: params.ts_cfg,
                                   description: 'Name of configuration to be passed as --cfg option value (sticky default)'),

                            booleanParam(name: 'restart_cfg',
                                         defaultValue: false,
                                         description: 'If true, test hosts should be restarted'),

                            string(name: 'lock',
                                   defaultValue: params.lock,
                                   description: 'Lock to acquire to avoid concurrent execution on the same test rig/resource (sticky default)'),

                           string(name: 'downstream_jobs',
                                   defaultValue: params.downstream_jobs,
                                   description: 'List of jobs to trigger at the end of execution (comma-separated, sticky default)'),

                           string(name: 'get_revs_from',
                                  defaultValue: get_revs_from,
                                  description: 'Jobs providing revisions to use (sticky default; comma-separated list)'),
                        ]

                        def propsList = []

                        paramsList.addAll(teRun.get_repo_params(
                                                      params, ctx))

                        if (ctx.containsKey('specificParameters')) {
                            paramsList += ctx['specificParameters']
                        }

                        if (ctx.containsKey('triggersProvider')) {
                            triggersList +=
                                ctx.triggersProvider()
                        }

                        propsList = [ parameters(paramsList),
                                      pipelineTriggers(triggersList) ]
                        if (!ctx.concurrent_builds) {
                            propsList += [ disableConcurrentBuilds() ]
                        }

                        properties(propsList)
                    }

                    script {
                        // Do cleanup as the first action to ensure that
                        // previous run results are not saved as artifacts
                        teRun.cleanup()
                    }
                }
            }

            stage('Pre start') {
                steps {
                    script {
                        teEmail.email_stage(ctx, 'Pre start')

                        def date_str
                        if (env.CAMPAIGN_DATE_OFFSET) {
                            date_str = env.CAMPAIGN_DATE_OFFSET
                        } else {
                            // By default we add 6 hours so that testing
                            // sessions started before midnight have the
                            // same date as testing sessions started after
                            // midnight on the same evening.
                            date_str = "+6 hours"
                        }

                        env.TE_META_START_TS = teMeta.meta_timestamp_make()
                        env.TE_META_CAMPAIGN_DATE = sh(
                              script: "date --date=\"${date_str}\" +%F",
                              returnStdout: true).trim()

                        if (get_revs_from) {
                            ctx.revdata_try_load(get_revs_from)
                        }

                        if (ctx.containsKey('preStartHook')) {
                            ctx.preStartHook()
                        }
                    }
                }
            }

            stage('Clone ts-rigs sources') {
                when { expression { env.TSRIGS_GIT_URL } }
                steps {
                    script {
                        teEmail.email_stage(ctx, 'Clone ts-rigs sources')
                        teRun.tsrigs_checkout(ctx)

                        teRun.tsrigs_load(ctx)
                    }
                }
            }

            stage('Clone TE and TS sources') {
                steps {
                    script {
                        teEmail.email_stage(ctx, 'Clone TE/TS sources')

                        teRun.te_checkout(ctx)
                        teRun.ts_checkout(ctx)
                    }
                }
            }

            stage('Clone ts-conf sources') {
                when { expression { ctx.tsconf } }
                steps {
                    script {
                        teEmail.email_stage(ctx, 'Clone ts-conf sources')
                        teRun.tsconf_checkout(ctx)
                    }
                }
            }

            stage('Run') {
                steps {
                    script {
                        teRun.cfg_lock ctx, {

                            teRun.define_logs_paths(ctx)

                            teEmail.email_stage(ctx, 'Run')

                            if (params.restart_cfg) {
                                if (ctx.containsKey('restartHook')) {
                                    ctx.restartHook(ctx)
                                } else {
                                    error("restartHook() is not specified")
                                }
                            }

                            if (ctx.containsKey('preRunHook')) {
                                ctx.preRunHook()
                            }

                            def opts = ctx.optionsProvider()
                            def cfg = params.ts_cfg ?: ""
                            if (params.with_tce) {
                                opts.add('--tce')
                            }

                            env.TE_META_FILE = teMeta.meta_data_fpath()

                            if (ctx.TS_LOG_LISTENER_NAME) {
                                opts.add('--logger-listener=' +
                                         ctx.TS_LOG_LISTENER_NAME)
                            }
                            if (params.ts_opts) {
                                opts.add(params.ts_opts)
                            }
                            if (env.HTML_LOGS) {
                                opts.add("--trc-html-logs=${env.HTML_LOGS}html")
                            }

                            teRun.run(ctx, cfg, opts)

                            if (ctx.containsKey('postRunHook')) {
                                ctx.postRunHook()
                            }
                        }
                    }
                }
            }

            stage('Save statistics') {
                steps {
                    script {
                        teEmail.email_stage(ctx, 'Save statistics')

                        ctx.ts_stats = teRun.statistics()
                    }
                }
            }

        }

        post {
            always {
                script {
                    teEmail.email_all_revs(ctx)

                    if (ctx.ts_stats != null) {
                        def total = ctx.ts_stats.totalCount
                        def failed = ctx.ts_stats.failCount
                        def skipped = ctx.ts_stats.skipCount
                        def passed = total - failed - skipped

                        if (total > 0) {
                            teEmail.email_set_trailer(
                                        ctx, "${passed}+${failed}+" +
                                        "${skipped}=${total}")
                        }
                    }

                    ctx.revdata_archive()

                    archiveArtifacts artifacts: teMeta.meta_data_fname()

                    try {
                        // We want to try archive things for investigation
                        teRun.strip()
                        teRun.archive()
                    } catch(Exception e) {
                        // Ignore error
                    }

                    if (ctx.publish_logs && ctx.PUBLISH_LOGS_NODE &&
                        ctx.LOGS_PATH) {
                        teRun.publish_logs(ctx)

                        if (env.HTML_LOGS) {
                            teEmail.email_newline(ctx)
                            teEmail.email_message(ctx, "Logs archive:")
                            teEmail.email_message(ctx, "${env.HTML_LOGS}")
                        }
                    }

                    teEmail.email_newline(ctx)
                    teEmail.email_message(ctx, "Parameters:")
                    params.sort().each {
                        name, value ->
                        teEmail.email_message(ctx, "${name}=${value}")
                    }

                    if (ctx.containsKey('postAlwaysHook')) {
                        ctx.postAlwaysHook()
                    }
                }
            }
            success {
                script {
                    post_conds += [ 'success' ]

                    if (params.downstream_jobs) {
                        params.downstream_jobs.tokenize(',').each {
                            job_name ->
                            build(job: job_name, wait: false)
                        }
                    }
                }
            }
            fixed {
                script {
                    post_conds += [ 'fixed' ]
                    teEmail.email_set_trailer(ctx, 'fixed')
                }
            }
            unsuccessful {
                script {
                    post_conds += [ 'unsuccessful' ]

                    if (ctx.containsKey('postUnsuccessfulHook')) {
                        ctx.postUnsuccessfulHook()
                    }
                }
            }
            cleanup {
                script {
                    if (ctx.email_conditions == null ||
                        ctx.email_conditions.intersect(post_conds)) {
                        teEmail.email_post(ctx,
                                           post_conds.contains('success'),
                                           emailRecipientProviders)
                    }

                    if (ctx.containsKey('postCleanupHook')) {
                        ctx.postCleanupHook()
                    }
                }
            }
        }
    }
}
