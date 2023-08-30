// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for email generation.
//
// Environment variables affecting this component:
// - TE_EMAIL_FROM_DEF. This is default email source address.
//   The value can contain __USER__ string which is replaced with
//   the name of current user.
// - TE_EMAIL_TO_<id> variables. Each variable can contain
//   one or more email addresses (separated by ';') to be
//   used as destinations for a given <id> (see
//   email_add_to_by_ids()).

// Set email sender.
//
// Args:
//   ctx: pipeline context
//   address: email address
def email_set_from(ctx, String address) {
    ctx.EMAIL_FROM = address
}

// Add email recipients.
//
// Args:
//   ctx: pipeline context
//   addresses: email addresses (should be separated
//              by ';')
def email_add_to(ctx, String addresses) {
    if (ctx.EMAIL_TO == null) {
        ctx.EMAIL_TO = [:]
    }

    addresses.tokenize(';').each {
        addr ->
        ctx.EMAIL_TO[addr] = true
    }
}

// Add email recipients by identifiers.
// For every identifier id, it is checked whether
// TE_EMAIL_TO_<id> is defined in environment. If
// it is defined, recipients from its value are added.
//
// Note: every <id> is turned into upper case, all '-' symbols
// are replaced with '_'. This simplifies usage of test suite
// names as identifiers here.
//
// Args:
//   ctx: pipeline context
//   ids: one or more string identifiers
def email_add_to_by_ids(ctx, String... ids) {
    ids.each { id ->
        def var_name = "TE_EMAIL_TO_${teCommon.str2id(id)}"
        if (env[var_name]) {
            email_add_to(ctx, env[var_name])
        }
    }
}

// Set prefix in email subject.
// By default it is "[CI <jobname>]".
//
// Args:
//   ctx: pipeline context
//   prefix: prefix string
def email_set_prefix(ctx, String prefix) {
    ctx.EMAIL_PREFIX = prefix
}

// Set trailer in email subject.
//
// Args:
//   ctx: pipeline context
//   trailer: trailer string
def email_set_trailer(ctx, String trailer) {
    ctx.EMAIL_TRAILER = trailer
}

// Reset email message: make it clean and print basic job info
//
// Args:
//   ctx: pipeline context
def email_start(ctx) {
    ctx.email_text = ""

    email_variable_message(ctx, 'WORKSPACE', env.WORKSPACE)
    email_variable_message(ctx, 'JOB_NAME', env.JOB_NAME)
    email_variable_message(ctx, 'NODE_NAME', env.NODE_NAME)
    email_newline(ctx)
    email_message(ctx, "Build URL: ${env.BUILD_URL}")
    email_message(ctx, "Timestamp: ${env.BUILD_TIMESTAMP}")
    email_newline(ctx)
}

// Add message to email
//
// Args:
//   ctx: pipeline context
//   message: message string
def email_message(ctx, String message) {
    ctx.email_text += message + "\n"
}

// Just wrapper for adding empty line
//
// Args:
//   ctx: pipeline context
def email_newline(ctx) {
    email_message(ctx, "")
}

// Add variable to email
//
// Args:
//   ctx: pipeline context
//   name: name of variable
//   value: value of variable
def email_variable_message(ctx, String name, String value) {
    email_message(ctx, "${name}=${value}")
}

// Add all revisions to email text. Revision are taken
// from the map constructed with help of teRevData.
//
// Args:
//   ctx: pipeline context
def email_all_revs(ctx) {
    def revs_only = []

    teRevData.iterate_values(ctx.all_revs, {
        component, variable, value ->
        if (variable ==~ /.*_REV$/) {
            revs_only.add("${variable}=${value}")
        }
    })

    email_newline(ctx)
    email_message(ctx, "Revisions:")
    revs_only.sort().each {
        value ->
        email_message(ctx, value)
    }
}

// Add stage to email
//
// Args:
//   ctx: pipeline context
//   stage: stage name
def email_stage(ctx, String stage) {
    email_message(ctx, "STAGE: ${stage}")
}

// Post email by status
//
// Args:
//   ctx: pipeline context
//   status: status of build
//   providers: recipient providers, i.e. requestor() or culprits() etc...
def email_post(ctx, status, providers = null) {
    def email_from
    def email_to = ""
    def prefix = ctx.EMAIL_PREFIX ?: "[CI ${env.JOB_NAME}]"
    def subject

    if (ctx.EMAIL_TO)
        email_to = ctx.EMAIL_TO.keySet().join(';')

    if (!email_to) {
        println "No destination email address is specified"
        return
    }

    if (!ctx.email_text) {
        email_start(ctx)
    }

    email_newline(ctx)
    if (status) {
        email_message(ctx, "OK")
    } else {
        email_message(ctx, "FAIL")
    }
    email_newline(ctx)

    subject = "${prefix} job ${currentBuild.displayName}: "
    subject += "${currentBuild.currentResult}"

    if (ctx.EMAIL_TRAILER) {
        subject += " ${ctx.EMAIL_TRAILER}"
    }

    email_from = ctx.EMAIL_FROM ?: env.TE_EMAIL_FROM_DEF
    if (email_from) {
        email_from = email_from.replaceAll(/__USER__/, "${env.USER}")
    }

    emailext (
        subject: "${subject}",
        to: email_to,
        from: email_from,
        recipientProviders: providers,
        attachLog: true,
        attachmentsPattern: '**/trc-brief.html',
        body: ctx.email_text
    )

    ctx.email_text = ""
}
