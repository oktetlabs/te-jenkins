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
    dlg.teCommon = teCommon

    dlg.env = env
    dlg.params = params

    // Having reference to this delegate itself makes it
    // simpler to use some API methods
    dlg.teCtx = dlg

    dlg.metas = [:]

    teRevData.init_ctx(dlg)

    return dlg
}
