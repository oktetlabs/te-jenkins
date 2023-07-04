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
