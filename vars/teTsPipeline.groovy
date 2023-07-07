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
//   update_job: Job from which to get revisions of the last
//               successful build (sticky default)
//   ts_cfg: Tested configuration name (sticky default)
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
//   postRunHook: Closure is called after run.sh and before saving artifacts.
//                Useful for archiving your specific artifacts, cleanup.
//                Optional parameter.
//   postAlwaysHook: Closure is called at post stage. Usefull for E-mail etc.
//                   Optional parameter.
//   postUnsuccessfulHook: Closure is called at post stage. Usefull for E-mail etc.
//                   Optional parameter.
//   postCleanupHook: Closure is called at the end of post stage. Usefull for E-mail etc.
//                   Optional parameter.
//
// See "Pipeline templates" in jenkins/README.md for more information on
// how to use this template.

def call(Closure body) {
    def ctx = teCommon.create_delegate(env, params)
    def emailRecipientProviders = []

    // DELEGATE_FIRST means that the delegate is used firstly
    // to resolve properties. So that any property set in closure
    // body will be set in ctx.
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = ctx
    body()

    pipeline {

        agent { label ctx.label }

        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '100'))
            timestamps()
            copyArtifactPermission('*')
            checkoutToSubdirectory('te-jenkins')

            // Concurrent builds are enabled to make it possible to define
            // the single pipeline and use it for running testing on
            // multiple hosts simultaneously. Lock at run stage should
            // be used to prevent simultaneous access to the same resource.
        }

        stages {
            stage('Job prepare') {
                steps {
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

                            string(name: 'lock',
                                   defaultValue: params.lock,
                                   description: 'Lock to acquire to avoid concurrent execution on the same test rig/resource (sticky default)'),

                           string(name: 'downstream_jobs',
                                   defaultValue: params.downstream_jobs,
                                   description: 'List of jobs to trigger at the end of execution (comma-separated, sticky default)'),

                           string(name: 'update_job',
                                   defaultValue: params.update_job,
                                   description: 'Job to get revisions to use (sticky default)'),
                        ]

                        paramsList.addAll(teRun.get_repo_params(
                                                      params, ctx))

                        if (ctx.containsKey('specificParameters')) {
                            paramsList += ctx['specificParameters']
                        }

                        if (ctx.containsKey('triggersProvider')) {
                            triggersList +=
                                ctx.triggersProvider()
                        }

                        properties([
                            parameters(paramsList),
                            pipelineTriggers(triggersList),
                        ])
                    }

                    script {
                        // Do cleanup as the first action to ensure that
                        // previous run results are not saved as artifacts
                        teRun.cleanup()

                        teEmail.email_start()
                    }
                }
            }

            stage('Pre start') {
                steps {
                    script {
                        teEmail.email_stage('Pre start')

                        ctx.metas.TS_NAME = ctx.ts_name ?: ""
                        ctx.metas.CFG = params.ts_cfg ?: ""

                        ctx.metas.TCE = params.with_tce ? 'true' : 'false'

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
                        ctx.metas.CAMPAIGN_DATE = sh(
                              script: "date --date=\"${date_str}\" +%F",
                              returnStdout: true).trim()

                        ctx.metas.START_TIMESTAMP =
                                          teMeta.meta_timestamp_make()
                        ctx.metas.RUN_OK = "false"
                        ctx.metas.RUN_STATUS = "ERROR"

                        if (params.update_job) {
                            ctx.all_revs =
                                teRevData.try_load_revs_from_job(
                                              params.update_job)
                        }

                        if (ctx.containsKey('preStartHook')) {
                            ctx.preStartHook()
                        }

                        teRevData.export_values(env, ctx.all_revs, true)
                    }
                }
            }

            stage('Clone ts-rigs sources') {
                when { expression { env.TSRIGS_GIT_URL } }
                steps {
                    script {
                        teEmail.email_stage('Clone ts-rigs sources')
                        teRun.tsrigs_checkout(ctx)

                        teRun.tsrigs_load(ctx)
                    }
                }
            }

            stage('Clone TE and TS sources') {
                steps {
                    script {
                        teEmail.email_stage('Clone TE/TS sources')

                        teRun.te_checkout(ctx)
                        teRun.ts_checkout(ctx)
                    }
                }
            }

            stage('Clone ts-conf sources') {
                when { expression { ctx.tsconf } }
                steps {
                    script {
                        teEmail.email_stage('Clone ts-conf sources')
                        teRun.tsconf_checkout(ctx)
                    }
                }
            }

            stage('Run') {
                steps {
                    lock(params.lock ?: params.ts_cfg) {
                        script {

                            // Bublik needs PROJECT to be present in metadata
                            // for sanity check. Do it here because
                            // ctx.PROJECT may be defined in ts-rigs.
                            ctx.metas.PROJECT = ctx.PROJECT ?: ctx.ts_name ?: ""

                            teRun.define_logs_paths(ctx)

                            teEmail.email_stage('Run')

                            // Prepare initial meta_data.json for live logs
                            // listener.
                            if (ctx.TS_LOG_LISTENER_NAME) {
                                // Temporarily set status to running to
                                // have it in live import metadata
                                ctx.metas.RUN_STATUS = "RUNNING"
                                teMeta.meta_generate(ctx)
                                ctx.metas.RUN_STATUS = "ERROR"
                            }

                            if (ctx.containsKey('preRunHook')) {
                                ctx.preRunHook()
                            }

                            def opts = ctx.optionsProvider()
                            def cfg = params.ts_cfg ?: ""
                            if (params.with_tce) {
                                opts.add('--tce')
                            }
                            if (ctx.TS_LOG_LISTENER_NAME) {
                                opts.add('--logger-listener=' +
                                         ctx.TS_LOG_LISTENER_NAME)
                                opts.add('--logger-meta-file=' +
                                         teMeta.meta_data_fpath())
                            }
                            opts.add(params.ts_opts)
                            if (env.HTML_LOGS) {
                                opts.add("--trc-html-logs=${env.HTML_LOGS}html")
                            }

                            teRun.run(ctx, cfg, opts)
                            ctx.metas.RUN_OK = "true"
                            ctx.metas.RUN_STATUS = "DONE"

                            ctx.metas.FINISH_TIMESTAMP =
                                                teMeta.meta_timestamp_make()
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
                        teEmail.email_stage('Save statistics')

                        ctx.ts_stats = teRun.statistics()
                    }
                }
            }

        }

        post {
            always {
                script {
                    teEmail.email_all_revs(ctx.all_revs)

                    if (ctx.ts_stats != null) {
                        def total = ctx.ts_stats.totalCount
                        def failed = ctx.ts_stats.failCount
                        def skipped = ctx.ts_stats.skipCount
                        def passed = total - failed - skipped

                        if (total > 0) {
                            teEmail.email_set_trailer("${passed}+${failed}+" +
                                                    "${skipped}=${total}")
                        }
                    }

                    teRevData.save_revs(ctx.all_revs)
                    archiveArtifacts artifacts: 'all.rev'

                    if (ctx.metas.FINISH_TIMESTAMP == null) {
                        ctx.metas.FINISH_TIMESTAMP =
                                        teMeta.meta_timestamp_make()
                    }

                    teMeta.meta_generate(ctx)
                    archiveArtifacts artifacts: teMeta.meta_data_fname()

                    try {
                        // We want to try archive things for investigation
                        teRun.strip()
                        teRun.archive()
                    } catch(Exception e) {
                        // Ignore error
                    }

                    if (ctx.PUBLISH_LOGS_NODE && ctx.LOGS_PATH) {
                        teRun.publish_logs(ctx)

                        if (env.HTML_LOGS) {
                            teEmail.email_newline()
                            teEmail.email_message("Logs archive:")
                            teEmail.email_message("${env.HTML_LOGS}")
                        }
                    }

                    teEmail.email_newline()
                    teEmail.email_message("Parameters:")
                    params.sort().each {
                        name, value ->
                        teEmail.email_message("${name}=${value}")
                    }

                    if (ctx.containsKey('postAlwaysHook')) {
                        ctx.postAlwaysHook()
                    }
                }
            }
            success {
                script {
                    teEmail.email_post(true, emailRecipientProviders)

                    if (params.downstream_jobs) {
                        params.downstream_jobs.tokenize(',').each {
                            job_name ->
                            build(job: job_name, wait: false)
                        }
                    }
                }
            }
            unsuccessful {
                script {
                    teEmail.email_post(false, emailRecipientProviders)
                    if (ctx.containsKey('postUnsuccessfulHook')) {
                        ctx.postUnsuccessfulHook()
                    }
                }
            }
            cleanup {
                script {
                    if (ctx.containsKey('postCleanupHook')) {
                        ctx.postCleanupHook()
                    }
                }
            }
        }
    }
}
