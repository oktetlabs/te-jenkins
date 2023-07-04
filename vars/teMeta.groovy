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

// Meta generation. It saves to JSON file all contents of metas
// variable. You can set fields in that variable to add them to
// meta data.
//
// Args:
//     ctx: pipeline context containing metas
def meta_generate(ctx) {
    meta_data = [
        version: 1,
        metas: []
    ]

    ctx.metas.each { var_name, var_value ->
        if (var_value == null) {
            if (ctx.containsKey(var_name)) {
                var_value = ctx[var_name]
            } else {
                var_value = env[var_name]
            }
        }
        if (var_value == null) {
            return
        }

        meta_node = [
            name: var_name,
            value: var_value
        ]

        def meta_type = meta_name2type(var_name)
        if (meta_type != null) {
            meta_node.type = meta_type
        }

        meta_data.metas.add(meta_node)
    }

    writeJSON(file: meta_data_fpath(), json: meta_data, pretty: 4)
}

// Get meta node type by tail of node name
//
// Args:
//     node_name: Name of node, for example TE_REV, START_TIMESTAMP
// Return:
//     String with type of node or null
def meta_name2type(node_name) {
    if (node_name.endsWith('_TIMESTAMP')) {
        return 'timestamp'
    }
    if (node_name.endsWith('_COMMIT') || node_name.endsWith('_REV')) {
        return 'revision'
    }
    if (node_name.endsWith('_BRANCH')) {
        return 'branch'
    }

    return null
}

// Get current timestamp
//
// Return:
//    timestamp string
def meta_timestamp_make() {
    def timestamp = sh(script: 'date +%FT%T%:z', returnStdout: true).trim()
    return timestamp
}
