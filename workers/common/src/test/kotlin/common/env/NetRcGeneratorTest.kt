/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common.env

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.createSecret
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.testSecretRef

class NetRcGeneratorTest : StringSpec({
        "The correct path in the user's home directory should be generated" {
            val mockBuilder = MockConfigFileBuilder()
            val definition = EnvironmentServiceDefinition(
                createInfrastructureService(
                    "https://repo.example.org",
                    createSecret("s1"),
                    createSecret("s2")
                )
            )

            NetRcGenerator().generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileName shouldBe ".netrc"
        }

        "generate the correct content of the .netrc file" {
            val secUser1 = createSecret("user1Secret")
            val secPass1 = createSecret("pass1Secret")
            val secUser2 = createSecret("user2Secret")
            val secPass2 = createSecret("pass2Secret")

            val service1 = createInfrastructureService("https://repo1.example.org", secUser1, secPass1)
            val service2 = createInfrastructureService("https://repo2.example.org", secUser2, secPass2)
            val service3 = createInfrastructureService("https://repo3.example.org", secUser2, secPass2)

            val mockBuilder = MockConfigFileBuilder()

            val expectedLines = listOf(
                "machine repo1.example.org login ${testSecretRef(secUser1)} password ${testSecretRef(secPass1)}",
                "machine repo2.example.org login ${testSecretRef(secUser2)} password ${testSecretRef(secPass2)}",
                "machine repo3.example.org login ${testSecretRef(secUser2)} password ${testSecretRef(secPass2)}"
            )

            val generator = NetRcGenerator()
            generator.generate(mockBuilder.builder, definitions(service1, service2, service3))

            mockBuilder.generatedLines() shouldContainExactly expectedLines
        }

        "handle multiple services referencing the same host" {
            val secUser1 = createSecret("user1Secret")
            val secPass1 = createSecret("pass1Secret")
            val secUser2 = createSecret("user2Secret")
            val secPass2 = createSecret("pass2Secret")
            val secUser3 = createSecret("user3Secret")
            val secPass3 = createSecret("pass3Secret")

            val service1 = createInfrastructureService("https://repo.example.org/r1", secUser1, secPass1)
            val service2 = createInfrastructureService("https://repo.example.org/r2", secUser2, secPass2)
            val service3 = createInfrastructureService("https://repo.example.org:443/r3", secUser3, secPass3)

            val mockBuilder = MockConfigFileBuilder()

            val expectedLines = listOf(
                "machine repo.example.org login ${testSecretRef(secUser1)} password ${testSecretRef(secPass1)}"
            )

            val generator = NetRcGenerator()
            generator.generate(mockBuilder.builder, definitions(service1, service2, service3))

            mockBuilder.generatedLines() shouldContainExactly expectedLines
        }

        "ignore services with an invalid URL" {
            val secUser1 = createSecret("user1Secret")
            val secPass1 = createSecret("pass1Secret")
            val secUser2 = createSecret("user2Secret")
            val secPass2 = createSecret("pass2Secret")

            val service1 = createInfrastructureService("https://repo.example.org", secUser1, secPass1)
            val service2 = createInfrastructureService("? invalid URL?!", secUser2, secPass2)

            val mockBuilder = MockConfigFileBuilder()

            val expectedLines = listOf(
                "machine repo.example.org login ${testSecretRef(secUser1)} password ${testSecretRef(secPass1)}"
            )

            val generator = NetRcGenerator()
            generator.generate(mockBuilder.builder, definitions(service1, service2))

            mockBuilder.generatedLines() shouldContainExactly expectedLines
        }
})

/**
 * Helper function to create a list with [EnvironmentServiceDefinition] objects from the passed in [services].
 */
private fun definitions(vararg services: InfrastructureService): List<EnvironmentServiceDefinition> =
    listOf(*services).map { EnvironmentServiceDefinition((it)) }
