// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2023 OKTET Labs Ltd. All rights reserved.
//
// Helper for saving and loading information about revisions and
// repositories.
//
// It allows to collect all information about revisions and repositories
// in a single map, save it to a file in JSON format which can be
// archived as an artifact. Then another job can copy that artifact,
// load JSON file and export values from it in environment.

// Load revisions map from a file.
//
// Args:
//   fpath: Path to the JSON file
//
// Return:
//   Loaded map (empty if the file is absent)
def load_revs(String fpath = "all.rev") {
    if (fileExists(fpath)) {
        return readJSON(file: fpath)
    } else {
        return [:]
    }
}

// Try to get map with revisions from an artifact of the last successful
// build of a given job.
//
// Args:
//   job: name of the job
//   fpath: file to get from artifacts
//
// Return:
//   Loaded map (empty if the file or job is absent)
def try_load_revs_from_job(String job = "update",
                           String fpath = "all.rev") {
    try {
        copyArtifacts(projectName: job,
                      filter: fpath, selector: lastSuccessful())
    } catch (Exception e) {
        return [:]
    }

    return load_revs(fpath)
}

// Store named value in a map.
//
// Args:
//   revs: map
//   component: component name (for example, "te")
//   name: value name (for example, "TE_REV")
//   value: value to store
def store_value(Map revs, String component, String name, String value) {
    if (!revs[component]) {
        revs[component] = [:]
    }

    revs[component][name] = value
}

// Export named values to environment.
//
// Args:
//   env: Environment object
//   revs: map with values
//   force: if true, existing environment variables should be
//          overwritten even if they have nonempty values
//   component: component name (if null, all components are iterated)
//   name: value name (if null, all values will be exported)
def export_values(env, Map revs, boolean force = false,
                  String component = null, String name = null) {
    revs.each {
        comp_name, comp_vars ->
        if (!component || component == comp_name) {
            comp_vars.each {
                var_name, var_value ->

                if (!name || name == var_name) {
                    if (force) {
                        env[var_name] = var_value
                    } else {
                        env[var_name] = env[var_name] ?:
                                        var_value
                    }
                }
            }
        }
    }
}

// Iterate all values in revisions map.
//
// Args:
//   revs: map to iterate
//   it: closure taking three arguments - component name, variable name and
//       value
def iterate_values(Map revs, Closure it) {
    revs.each {
        comp_name, comp_vars ->
        comp_vars.each {
            var_name, var_value -> it(comp_name, var_name, var_value)
        }
    }
}

// Save map with revisions to a JSON file.
//
// Args:
//   revs: map
//   fpath: file path
def save_revs(Map revs, String fpath = "all.rev") {
    writeJSON(file: fpath, json: revs, pretty: 4)
}
