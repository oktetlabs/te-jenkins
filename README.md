[SPDX-License-Identifier: Apache-2.0]::
[Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.]::

# Jenkins pipelines and shared library

The purpose of the code here is to simplify usage of Jenkins with
TE and test suites.

## Standalone pipelines

Some generic TE pipelines can be found in `pipelines/` folder.

### publish-logs
A pipeline for copying testing logs to the location where they
should be stored. Testing job should trigger it once tests are
finished.

### bublik-import
This pipeline defines a job for submitting testing logs to Bublik
web application. Testing job should trigger it once tests are
finished and logs are copied to the location where they can be
accessed via URL by Bublik (see publish-logs pipeline).

## Shared Jenkins library

The library defined in `./vars/` contains common Jenkins/Groovy code
which can be used to define pipelines for specific test suites.

### How to load the library

The most convenient way to use this library is by configuring it as
global library in Jenkins (Manage Jenkins -> Configure System ->
Global Pipeline Libraries).

Use `teLib` name for it. Then you will be able to load this library at
the beginning of your Jenkins files like

```
@Library('teLib') _

```

### Pipeline templates

Shared library contains a few pipeline templates in
`./vars/[template_name].groovy` files. A pipeline template can be
used to define a pipeline by calling `[template_name]` with a closure as
its only argument:

```
[template_name] {
    // setting some variables and hooks which are expected by
    // the template

    ts_name = 'my-ts'
    other_template_param = true

    someHook = {
        if (params.some_param) {
            something = true
            metas.SOMETHING = true
        }
    }

    someOtherHook = {
        if (something) {
            teEmail.email_message("Some message")
        }
    }

    <...>
}
```

All the code inside that closure has access to components of this
library (defined in `./vars/`) and to some common objects:
- `env`: environment variables
- `params`: pipeline parameters
- `metas`: meta data to be saved in `meta_data.json`. This is a
           dictionary where you can set parameters. If you set
           value to null, it will be obtained from environment
           or pipeline context (which is used as delegate for
           your closure) if it is present there when the JSON file
           is generated.
- `all_revs`: dictionary with repository URLs and revisions, should
              be manipulated with help of teRevData component
- `ts_stats`: after tests were run, tests statistics is stored here
- `TS_SRC`: path to test suite sources (after checkout)
- `teCtx`: pipeline context (may be passed to some API functions;
           all the variables mentioned before are its fields)

A map is used as a delegate for the closure (`teCtx`), so that any
undeclared variable set inside the closure is saved as a property of
that map and becomes available to all code from it.

#### TS run pipeline template

Helper for defining a pipeline for running tests can be found in
`./vars/teTsPipeline.groovy`. It is described in comments
there how to use it.

#### Scheduled testing pipeline template

Pipeline for running testing automatically according to a schedule
can be defined with help of `./vars/teScheduledRunPipeline.groovy`.
See comments there.

#### Documentation building pipeline template

Helper for defining test suite documentation builder pipeline can be found
in `./vars/teTsDocPipeline`.

### Helper libraries

Other groovy files in `./vars/` are helper library components. They are
available to pipeline template hooks, for example:
```
revs = teRevData.load_revs()
```

List of currently available components:
- **teEmail**: configuring email to be sent when pipeline finishes
- **teMeta**: generating `meta_data.json`
- **teRevData**: saving and loading information about revisions and repositories
- **teRun**: helper for working with te/ts/ts-rigs/ts-conf repositories

### Repositories

`teTsPipeline` and `teTsDocPipeline` templates assume presence of the following
repositories:

- te: Test Environment
- ts-rigs (optional): repository with information about test hosts
  and other site-specific data
- ts-conf (optional): repository with common configuration files and
  scripts which are shared between test suites
- test suite repository

Git URLs for these repositories may be either specified via job
parameters:
- `te_repo`
- `tsrigs_repo`
- `tsconf_repo`
- `ts_repo`

or, more conveniently, via environment variables set in Manage Jenkins
or in Jenkins files:

- `TE_GIT_URL`
- `TSRIGS_GIT_URL`
- `TSCONF_GIT_URL`
- `TS_GIT_URL`

Job parameters have higher priority than these variables and can be
used for testing different repositories.

If ts-rigs is present, pipeline templates will check whether
`jenkins/common.groovy` is available there. If it is, they
will load it and call `set_defs(ctx)` from it, letting it set some
common variables. This way some variables may be specified
in pipeline context and environment, for instance

- `ctx.TE_GIT_URL`: TE repository URL
- `ctx.TSCONF_GIT_URL`: ts-conf repository URL
- `env.TE_WORKSPACE_DIR`: where temporary directory for building TA should
                          be created on test hosts

Also `jenkins/defs/<test suite name>/defs.groovy` will be loaded if
it is present, and `set_defs(ctx)` will be called from it to set some
test-suite specific variables.

So it can be enough to set `TSRIGS_GIT_URL` in Jenkins and specify other
repository URLs in ts-rigs. In `set_defs(ctx)` you can use "${env.USER}"
for current username, in Manage Jenkins -> Configure System you can use
`__USER__` which will be automatically replaced with current username by
this library:

```
TSRIGS_GIT_URL = https://__USER__@git.oktetlabs.ru/git/oktetlabs/ts-rigs.git
```

## How to configure Jenkins for your test suite

### Required Jenkins plugins

- Pipeline: Stage Step
- Pipeline: SCM step
- Timestamper
- Email Extension
- Copy Artifact
- JUnit

### Configuring nodes and agents in Jenkins

The following hosts should be available as Jenkins agents.
May be some physical host can play a few roles simultaneously
from the following list; then it should have all associated
Jenkins labels. Often it is better to have different hosts
for these roles to balance load and to meet specific requirements
of the corresponding tasks (for example, building test suite may
require installing some set of packages; logs storage needs to
have a lot of free disk space).

1. A host where your test suite is built and run. Assign a label
   to that host so that you can pass it to `teTsPipeline` template.
2. A host where test suite documentation is built. A label
   assigned to this host should be passed to `teTsDocPipeline`
   template.
3. A host where publish-logs pipeline is run. It is the host where
   archive of logs of testing runs is stored. Label of this host
   should be passed to `publish-logs` pipeline as `logs_node`
   parameter. By default it will try to use `ts-logs` label.
4. A host where bublik-import pipeline is run. It should have
   label `bublik-import` in Jenkins. From that host Bublik
   web interface should be available.

### Creating pipeline files in your test suite using templates

1. It makes sense to create "update" pipeline as the first one -
   based on `teTsPipeline` template. It will only build test suite
   together with TE without running it (pass '--build-only' option
   in `optionsProvider` hook to achieve that), and save used revisions
   and repositories in artifacts. Then in other pipelines you
   can copy artifacts from last successful build of update pipeline
   and use them. It will minimize the number of cases when testing
   is not run because of broken build.
   In `triggersProvider` use `pollSCM()` so that update job will be
   rebuilt after detecting source code changes.
2. Create "run" pipeline based on `teTsPipeline` for running
   your tests.
3. If you need automatic building of documentation, also create
   a pipeline based on `teTsDocPipeline` template.
4. A pipeline for scheduling testing runs (based on
   `teScheduledRunPipeline`) can be added in ts-rigs (if you use it)
   or your test suite repository.
5. If you use ts-rigs, it can also be helpful to specify there
   generic and test suite specific environment variables as
   described above.

#### Configuring testing logs publishing

The template `teTsPipeline `can trigger `publish-logs` and `bublik-import`
(if Bublik is configured) to publish logs once testing is finished. For this
to work you need to set some variables in pipeline context in `ts-rigs` or in
your Jenkins file calling the pipeline template (in the closure body or in
`preStartHook` or `preRunHook`).

This is required for `publish-logs` job that copies testing logs to
a host where testing logs are permanently stored:

- `PUBLISH_LOGS_NODE` - label of the Jenkins node where logs should be
  permanently stored. This string will be passed to `publish-logs`
  job as its `logs_node` parameter. If this variable is not set or
  empty, logs will not be published.
- `TS_LOGS_SUBPATH` - path relative to `$HOME/private_html/` on the host
  where logs are stored. This is the base directory where logs should
  be saved. Subdirectories named after current date, tested configuration
  and Jenkins build number will be appended to this path to determine
  location for logs of the current testing (see `define_logs_paths()`
  in `teRun`); the resulting path is stored in `LOGS_PATH` context
  variable.
  If you do not like the default path construction, you can set directly
  `LOGS_PATH` variable in your pipeline (in closure body or `preStartHook`
  or `preRunHook`). Then it will be used as a path relative to
  `$HOME/private_html/`. Then you may not need to define `TS_LOGS_SUBPATH`.
  If neither `LOGS_PATH` nor `TS_LOGS_SUBPATH` is specified, logs
  will not be published or made available via Bublik.

The following variables are used for Bublik application currently:

- `TS_BUBLIK_URL` - URL of Bublik web application. If this is not set,
  `bublik-import` job will not be triggered and the rest of the variables
  here will be ignored.
- `TS_LOGS_URL_PREFIX` - URL prefix for web access to logs. Bublik needs
  it to import logs. It is assumed that `LOGS_PATH` should be appended
  to this prefix to get logs of the current testing after `publish-logs`
  job succeeded.
- `TS_LOG_LISTENER_NAME` - name of log listener defined in Logger
  configuration file. It is used by Bublik to obtain live logs during
  testing.
- `PROJECT` - project name, used by Bublik for sanity check that
  the logs belong to the project for which this Bublik instance
  was created.

### Configuring pipelines in Jenkins

1. If you do not already have them, add pipelines publish-logs and
   bublik-import (if you use Bublik web application) with exactly
   these names.
2. If you use ts-rigs, in `Manage Jenkins -> Configure System` set
   environment variable `TSRIGS_GIT_URL`.
3. Create a folder for your test suite in Jenkins.
4. Add pipelines for update and documentation build using pipeline
   files in your test suite. In update job set `downstream_jobs`
   string parameter to the name of your documentation job so that
   documentation is rebuilt automatically after successful update.
   In documentation job set `update_job` parameter to the name
   of your update job so that it gets repositories and revisions
   from there.
5. Add pipeline(s) based on "run" file in your test suite. This
   pipeline(s) will be triggered by pipeline for scheduled
   testing runs according to a schedule defined there.
   Set `update_job` parameter to the name of your update job.
6. Add pipeline for scheduled testing runs.

You may add multiple run jobs, for example, one for every configuration,
and mention them in a schedule stored in a pipeline for scheduled
runs. Or you can use the single run job for all configurations,
passing different `ts_cfg` values to it.
