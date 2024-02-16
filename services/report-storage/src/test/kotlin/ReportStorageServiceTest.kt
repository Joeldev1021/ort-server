/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.services

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.http.ContentType

import io.mockk.every
import io.mockk.mockk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import org.ossreviewtoolkit.server.storage.Key
import org.ossreviewtoolkit.server.storage.Storage
import org.ossreviewtoolkit.server.storage.StorageEntry

class ReportStorageServiceTest : WordSpec({
    "fetchReport" should {
        "return a ReportDownloadData object for an existing report" {
            val runId = 111L
            val fileName = "testReport.html"
            val reportData = "This is a report from the storage.".toByteArray()
            val contentType = "text/html"
            val key = Key("$runId|$fileName")

            val storage = mockk<Storage>()
            every { storage.containsKey(key) } returns true
            every { storage.read(key) } returns StorageEntry(ByteArrayInputStream(reportData), contentType)

            val service = ReportStorageService(storage)
            val downloadData = service.fetchReport(runId, fileName)

            downloadData.contentType shouldBe ContentType.Text.Html

            val stream = ByteArrayOutputStream()
            downloadData.loader(stream)
            stream.toByteArray() shouldBe reportData
        }

        "throw an exception if the requested report cannot be found" {
            val runId = 112L
            val fileName = "nonExistingReport.dat"

            val storage = mockk<Storage>()
            every { storage.containsKey(any()) } returns false

            val service = ReportStorageService(storage)
            val exception = shouldThrow<ReportNotFoundException> {
                service.fetchReport(runId, fileName)
            }

            exception.message shouldContain runId.toString()
            exception.message shouldContain fileName
        }

        "handle an undefined content type" {
            val runId = 88L
            val fileName = "testReportWithoutContentType.dat"
            val reportData = "This is a report from the storage.".toByteArray()
            val key = Key("$runId|$fileName")

            val storage = mockk<Storage>()
            every { storage.containsKey(key) } returns true
            every { storage.read(key) } returns StorageEntry(ByteArrayInputStream(reportData), null)

            val service = ReportStorageService(storage)
            val downloadData = service.fetchReport(runId, fileName)

            downloadData.contentType shouldBe ContentType.Application.OctetStream
        }
    }
})
