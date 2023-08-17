/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.server.api.v1

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AnalyzerJob(
    /**
     * The unique identifier.
     */
    val id: Long,

    /**
     * The time the job was created.
     */
    val createdAt: Instant,

    /**
     * The time the job was started.
     */
    val startedAt: Instant? = null,

    /**
     * The time the job finished.
     */
    val finishedAt: Instant? = null,

    /**
     * The job configuration.
     */
    val configuration: AnalyzerJobConfiguration,

    /**
     * The job status.
     */
    val status: JobStatus
)
