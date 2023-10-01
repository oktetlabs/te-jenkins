// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for generating meta_data.json which describes the run
// and is used by Bublik.
//
// Requires:
//     Pipeline Utility Steps

// Get name of the file with metas
def meta_data_fname() {
    return 'meta_data.json'
}

// Get path to the file with metas
def meta_data_fpath() {
    return env.WORKSPACE + '/' + meta_data_fname()
}

// Get current timestamp
//
// Return:
//    timestamp string
def meta_timestamp_make() {
    def timestamp = sh(script: 'date +%FT%T%:z', returnStdout: true).trim()
    return timestamp
}
