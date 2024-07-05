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

package org.eclipse.apoapsis.ortserver.workers.common.context

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.config.ConfigFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

class WorkerContextFactoryTest : WordSpec({
    afterSpec {
        unmockkAll()
    }

    "ortRun" should {
        "return the OrtRun object" {
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()

            val context = helper.context()

            context.ortRun shouldBe run
        }

        "throw an exception if the run ID cannot be resolved" {
            val helper = ContextFactoryTestHelper()

            every { helper.ortRunRepository.get(any()) } returns null

            val context = helper.context()

            shouldThrow<IllegalArgumentException> {
                context.ortRun
            }
        }
    }

    "hierarchy" should {
        "return the hierarchy of the current repository" {
            val repositoryId = 20230607144801L
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()
            every { run.repositoryId } returns repositoryId

            val hierarchy = mockk<Hierarchy>()
            every { helper.repositoryRepository.getHierarchy(repositoryId) } returns hierarchy

            val context = helper.context()

            context.hierarchy shouldBe hierarchy
        }
    }

    "createTempDir" should {
        "return a temporary directory" {
            val helper = ContextFactoryTestHelper()
            helper.context().use { context ->
                val dir1 = context.createTempDir()
                val dir2 = context.createTempDir()

                dir1.isDirectory shouldBe true
                dir1 shouldNotBe dir2
            }
        }

        "remove the content of the temporary directory when the context is closed" {
            val helper = ContextFactoryTestHelper()
            val tempDir = helper.context().use { context ->
                val dir = context.createTempDir()

                dir.resolve("testFile.txt").writeText("This is a test file.")
                val subDir = dir.resolve("sub")
                subDir.mkdir() shouldBe true
                subDir.resolve("sub.txt").writeText("A test file in a sub folder.")

                dir
            }

            tempDir.exists() shouldBe false
        }
    }

    "resolveSecret" should {
        "resolve a secret" {
            val secret = createSecret(SecretsProviderFactoryForTesting.PASSWORD_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()

            context.resolveSecret(secret) shouldBe SecretsProviderFactoryForTesting.PASSWORD_SECRET.value
        }

        "cache the value of a secret that has been resolved" {
            val secret = createSecret(SecretsProviderFactoryForTesting.SERVICE_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()
            context.resolveSecret(secret)

            val secretsProvider = SecretsProviderFactoryForTesting.instance()
            secretsProvider.writeSecret(
                SecretsProviderFactoryForTesting.SERVICE_PATH,
                org.eclipse.apoapsis.ortserver.secrets.Secret("changedValue")
            )

            context.resolveSecret(secret) shouldBe SecretsProviderFactoryForTesting.SERVICE_SECRET.value
        }
    }

    "resolveSecrets" should {
        "resolve multiple secrets" {
            val secret1 = createSecret(SecretsProviderFactoryForTesting.PASSWORD_PATH.path)
            val secret2 = createSecret(SecretsProviderFactoryForTesting.SERVICE_PATH.path)
            val secret3 = createSecret(SecretsProviderFactoryForTesting.TOKEN_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()

            val secretValues = context.resolveSecrets(secret1, secret2, secret3)

            secretValues shouldContainExactly mapOf(
                secret1 to SecretsProviderFactoryForTesting.PASSWORD_SECRET.value,
                secret2 to SecretsProviderFactoryForTesting.SERVICE_SECRET.value,
                secret3 to SecretsProviderFactoryForTesting.TOKEN_SECRET.value
            )
        }

        "cache the resolved secrets" {
            val secret1 = createSecret(SecretsProviderFactoryForTesting.PASSWORD_PATH.path)
            val secret2 = createSecret(SecretsProviderFactoryForTesting.SERVICE_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()

            context.resolveSecrets(secret1, secret2)

            val secretsProvider = SecretsProviderFactoryForTesting.instance()
            secretsProvider.writeSecret(
                SecretsProviderFactoryForTesting.SERVICE_PATH,
                org.eclipse.apoapsis.ortserver.secrets.Secret("changedValue")
            )

            context.resolveSecret(secret1) shouldBe SecretsProviderFactoryForTesting.PASSWORD_SECRET.value
        }
    }

    "downloadConfigurationFile" should {
        "download a single configuration file" {
            val dir = tempdir()
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val file = context.downloadConfigurationFile(Path("config1.txt"), dir)

            file.name shouldBe "config1.txt"
            file.readText() shouldBe "Configuration1"
        }

        "allow renaming a configuration file" {
            val dir = tempdir()
            val targetName = "my-config.txt"
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val file = context.downloadConfigurationFile(Path("config1.txt"), dir, targetName)

            file.name shouldBe targetName
            file.readText() shouldBe "Configuration1"
        }

        "cache files that have already been downloaded" {
            val dir = tempdir()
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val file1 = context.downloadConfigurationFile(Path("config1.txt"), dir)
                val file2 = context.downloadConfigurationFile(Path("config1.txt"), dir)

                file1 shouldBe file2
        }

        "not cache downloaded files if they use different names" {
            val dir = tempdir()
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val file1 = context.downloadConfigurationFile(Path("config1.txt"), dir)
            val file2 = context.downloadConfigurationFile(Path("config1.txt"), dir, "otherConfig.txt")

            file1 shouldNotBe file2
        }

        "not cache downloaded files if they use different target directories" {
            val dir1 = tempdir()
            val dir2 = tempdir()
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val file1 = context.downloadConfigurationFile(Path("config1.txt"), dir1)
            val file2 = context.downloadConfigurationFile(Path("config1.txt"), dir2)

            file1 shouldNotBe file2
        }
    }

    "downloadConfigurationFiles" should {
        "download multiple configuration files" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val path1 = Path("config1.txt")
                val path2 = Path("config2.txt")
                val files = context.downloadConfigurationFiles(listOf(path1, path2), tempdir())

                files shouldHaveSize 2

                files[path1]?.readText() shouldBe "Configuration1"
                files[path2]?.readText() shouldBe "Configuration2"
            }
        }

        "cache files that have already been downloaded" {
            val dir = tempdir()
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val path = Path("config1.txt")
                val file1 = context.downloadConfigurationFile(path, dir)
                val files = context.downloadConfigurationFiles(listOf(path), dir)

                files[path] shouldBe file1
            }
        }
    }

    "downloadConfigurationDirectory" should {
        "download all files in a configuration directory" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val directoryPath = Path("dir")
                val files = context.downloadConfigurationDirectory(directoryPath, tempdir())

                files shouldHaveSize 2

                files[Path("dir/subConfig1.txt")]?.readText() shouldBe "subConfig1"
                files[Path("dir/subConfig2.txt")]?.readText() shouldBe "subConfig2"
            }
        }
    }

    "resolvePluginConfigSecrets" should {
        "return an empty Map for null input" {
            val helper = ContextFactoryTestHelper()

            val resolvedConfig = helper.context().resolvePluginConfigSecrets(null)

            resolvedConfig should beEmpty()
        }

        "return plugin configurations with resolved secrets" {
            val pluginConfig1 = PluginConfiguration(
                options = mapOf("plugin1Option1" to "v1", "plugin1Option2" to "v2"),
                secrets = mapOf("plugin1User" to "dbUser", "plugin1Password" to "dbPassword")
            )
            val pluginConfig2 = PluginConfiguration(
                options = mapOf("plugin2Option" to "v3"),
                secrets = mapOf(
                    "plugin2ServiceUser" to "serviceUser",
                    "plugin2ServicePassword" to "servicePassword",
                    "plugin2DBAccess" to "dbPassword"
                )
            )
            val config = mapOf("p1" to pluginConfig1, "p2" to pluginConfig2)

            val resolvedConfig1 = pluginConfig1.copy(
                secrets = mapOf("plugin1User" to "scott", "plugin1Password" to "tiger")
            )
            val resolvedConfig2 = pluginConfig2.copy(
                secrets = mapOf(
                    "plugin2ServiceUser" to "svcUser",
                    "plugin2ServicePassword" to "svcPass",
                    "plugin2DBAccess" to "tiger"
                )
            )
            val expectedConfig = mapOf("p1" to resolvedConfig1, "p2" to resolvedConfig2)

            val helper = ContextFactoryTestHelper()

            val resolvedConfig = helper.context().resolvePluginConfigSecrets(config)

            resolvedConfig shouldBe expectedConfig
        }
    }

    "the ORT configuration" should {
        "should be set up" {
            mockkObject(WorkerOrtConfig)
            val workerOrtConfigMock = mockk<WorkerOrtConfig> {
                every { setUpOrtEnvironment() } just runs
            }
            every { WorkerOrtConfig.create(config) } returns workerOrtConfigMock

            val helper = ContextFactoryTestHelper()
            helper.context()

            verify {
                workerOrtConfigMock.setUpOrtEnvironment()
            }
        }
    }
})

private const val RUN_ID = 20230607142948L

/** The path under which test configuration files are stored. */
private const val CONFIG_FILE_DIRECTORY = "src/test/resources/config"

/** A map with secrets to be returned by the test config manager. */
private val configSecrets = mapOf(
    "dbUser" to "scott",
    "dbPassword" to "tiger",
    "serviceUser" to "svcUser",
    "servicePassword" to "svcPass"
)

/** The configuration used by the test factory. */
private val config = createConfigManager()

/**
 * Return an initialized [Config] object that configures the test secret provider factory.
 */
private fun createConfigManager(): ConfigManager {
    val configManagerProperties = mapOf(
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to configSecrets,
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME
    )
    val properties = mapOf(
        SecretStorage.CONFIG_PREFIX to mapOf(SecretStorage.NAME_PROPERTY to SecretsProviderFactoryForTesting.NAME),
        ConfigManager.CONFIG_MANAGER_SECTION to configManagerProperties
    )
    return ConfigManager.create(ConfigFactory.parseMap(properties))
}

/**
 * Create a [Secret] with the given [path]. All other properties are irrelevant.
 */
private fun createSecret(path: String): Secret =
    Secret(0L, path, "irrelevant", null, null, null, null)

/**
 * A test helper class managing a [WorkerContextFactory] instance and its dependencies.
 */
private class ContextFactoryTestHelper(
    /** Mock for the [OrtRunRepository]. */
    val ortRunRepository: OrtRunRepository = mockk(),

    /** Mock for the [RepositoryRepository]. */
    val repositoryRepository: RepositoryRepository = mockk(),

    /** The factory to be tested. */
    val factory: WorkerContextFactory = WorkerContextFactory(config, ortRunRepository, repositoryRepository)
) {
    /**
     * Prepare the mock [OrtRunRepository] to be queried for the test run ID. Return a mock run that is also returned
     * by the repository.
     */
    fun expectRunRequest(): OrtRun {
        val run = mockk<OrtRun> {
            every { resolvedJobConfigContext } returns CONFIG_FILE_DIRECTORY
        }
        every { ortRunRepository.get(RUN_ID) } returns run

        return run
    }

    /**
     * Invoke the test factory to create a context for the test run ID.
     */
    fun context(): WorkerContext = factory.createContext(RUN_ID)
}
