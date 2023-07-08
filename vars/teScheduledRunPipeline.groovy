// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Pipeline template to run tests according to a schedule on
// various hosts.
//
// Required plugins:
//   - Pipeline: Stage Step
//   - Timestamper
//   - Parameterized Scheduler
//
// Pipeline is expected to have the following mandatory parameters:
//   job: which Jenkins job this pipeline should trigger
//
// It is assumed that the pipeline is triggered automatically
// according to a schedule where it is specified at which time
// which job should be run and with which parameters. All parameters
// except job are passed to the triggered job.
//
// Template caller should set the following properties in a closure
// passed to the template:
//   label: label of the Jenkins agent
//   schedule: parameterized schedule in the format accepted by
//             parameterizedCron()
//   specificParameters: list of test suite specific additional
//                       parameters which will be passed to
//                       the triggered job (optional)
//
// It is assumed that this template is used to define pipeline
// in ts-rigs where information about testing configurations and
// schedule of their usage in testing for specific test
// suites is stored. For example, it can be used like
//
// scheduledRunPipeline {
//     label = 'main'
//
//     schedule = '''
// H 03 * * * % job=host1_run
// '''
//
//     specificParameters = [
//       string(name: 'ts_opts', defaultValue: '',
//              description: 'Additional test suite options')
//     ]
// }
//
// See "Pipeline templates" in jenkins/README.md for more information on
// how to use this template.

def call(Closure body) {
    def job_params = []
    def ctx = teCommon.create_delegate(env, params)

    def get_job_param = {
        pname, pvalue ->

        switch (pvalue.getClass()) {
            case java.lang.String:
                return string(name: pname, value: pvalue)

            case java.lang.Boolean:
                return booleanParam(name: pname, value: pvalue)
        }
    }

    def get_job_params = {
        params ->

        params.each {
            name, value ->
            if (name != 'job') {
                job_params.add(get_job_param(name, value))
            }
        }
    }

    // DELEGATE_FIRST means that the delegate is used firstly
    // to resolve properties. So that any property set in closure
    // body will be set in ctx.
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = ctx
    body()

    pipeline {

        options {
            buildDiscarder(logRotator(daysToKeepStr:'15', numToKeepStr:'100'))
            timestamps()
        }

        agent {
            label ctx.label
        }

        stages {

            stage("Update schedule and parameters") {
                steps {
                    script {
                        def params_list = [
                            string(name: 'job', defaultValue: '',
                                   description: 'Job to execute'),
                        ]

                        if (ctx.specificParameters) {
                            params_list.addAll(
                                  ctx.specificParameters)
                        }

                        // pollSCM() trigger is added here so that
                        // schedule is updated automatically in Jenkins
                        // after changes in sources.
                        properties([parameters(params_list),
                                    pipelineTriggers([
                                        parameterizedCron(
                                                  ctx.schedule),
                                        pollSCM('H * * * *')])])
                    }
                }
            }

            stage("Trigger downstream job") {
                steps {
                    script {
                        // This pipeline can be run without job argument
                        // manually or when it is triggered by pollSCM().
                        // It allows to update the schedule after sources
                        // change.
                        if (params.job) {
                            get_job_params(params)

                            build job: params.job, wait: false,
                                       parameters: job_params
                        }
                    }
                }
            }
        }
    }
}
