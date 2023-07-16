// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Generic helper functions.

// Create delegate map for a closure which is passed to pipeline
// templates.
//
// Return:
//   Map to use as a delegate
def create_delegate(env, params) {
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
