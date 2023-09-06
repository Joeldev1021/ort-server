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

package org.ossreviewtoolkit.server.workers.common.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.NestedRepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ResolvedConfigurationRepository
import org.ossreviewtoolkit.server.model.resolvedconfiguration.PackageCurationProviderConfig
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedPackageCurations
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.repository.IssueResolution
import org.ossreviewtoolkit.server.model.runs.repository.PackageConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCurationData
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.OrtTestData
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt

class OrtRunServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures
    lateinit var repositoryConfigRepository: RepositoryConfigurationRepository
    lateinit var resolvedConfigurationRepository: ResolvedConfigurationRepository

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
        repositoryConfigRepository = dbExtension.fixtures.repositoryConfigurationRepository
        resolvedConfigurationRepository = dbExtension.fixtures.resolvedConfigurationRepository
    }

    "getOrtRepositoryInformation" should {
        "return ORT repository object" {
            val service = OrtRunService(db, repositoryConfigRepository, resolvedConfigurationRepository)

            val vcsInfo = getVcsInfo("https://example.com/repo.git")
            val processedVcsInfo = getVcsInfo("https://example.com/repo-processed.git")
            val nestedVcsInfo1 = getVcsInfo("https://example.com/repo-nested-1.git")
            val nestedVcsInfo2 = getVcsInfo("https://example.com/repo-nested-2.git")

            val ortRun = createOrtRun(
                db,
                vcsInfo,
                processedVcsInfo,
                nestedVcsInfo1,
                nestedVcsInfo2,
                fixtures,
                repositoryConfigRepository
            )

            service.getOrtRepositoryInformation(ortRun) shouldBe Repository(
                vcsInfo.mapToOrt(),
                processedVcsInfo.mapToOrt(),
                mapOf("nested-1" to nestedVcsInfo1.mapToOrt(), "nested-2" to nestedVcsInfo2.mapToOrt()),
                OrtTestData.repository.config
            )
        }

        "throw exception if VCS information is not present in ORT run" {
            val service = OrtRunService(db, repositoryConfigRepository, resolvedConfigurationRepository)

            val ortRun = fixtures.ortRun

            val exception = shouldThrow<IllegalArgumentException> {
                service.getOrtRepositoryInformation(ortRun)
            }

            exception.message shouldBe "VCS information is missing from ORT run '1'."
        }
    }

    "getResolvedConfiguration" should {
        "return the resolved configuration" {
            val service = OrtRunService(db, repositoryConfigRepository, resolvedConfigurationRepository)

            val ortRun = fixtures.ortRun

            val id = Identifier("type", "namespace", "name", "version")

            val packageConfigurations = listOf(PackageConfiguration(id = id))
            resolvedConfigurationRepository.addPackageConfigurations(ortRun.id, packageConfigurations)

            val packageCurations = listOf(
                ResolvedPackageCurations(
                    provider = PackageCurationProviderConfig(name = "name"),
                    curations = listOf(PackageCuration(id = id, PackageCurationData()))
                )
            )
            resolvedConfigurationRepository.addPackageCurations(ortRun.id, packageCurations)

            val resolutions = Resolutions(
                issues = listOf(IssueResolution(message = "message", reason = "reason", comment = "comment"))
            )
            resolvedConfigurationRepository.addResolutions(ortRun.id, resolutions)

            service.getResolvedConfiguration(ortRun) shouldBe
                    ResolvedConfiguration(packageConfigurations, packageCurations, resolutions)
        }

        "return an empty resolved configuration if no resolved configuration was stored" {
            val service = OrtRunService(db, repositoryConfigRepository, resolvedConfigurationRepository)

            service.getResolvedConfiguration(fixtures.ortRun) shouldBe ResolvedConfiguration()
        }
    }
})

private fun createOrtRun(
    db: Database,
    vcsInfo: VcsInfo,
    processedVcsInfo: VcsInfo,
    nestedVcsInfo1: VcsInfo,
    nestedVcsInfo2: VcsInfo,
    fixtures: Fixtures,
    repositoryConfigurationRepository: RepositoryConfigurationRepository
) = db.blockingQuery {
    val vcs = VcsInfoDao.getOrPut(vcsInfo)
    val vcsProcessed = VcsInfoDao.getOrPut(processedVcsInfo)
    val vcsNested = mapOf(
        "nested-1" to VcsInfoDao.getOrPut(nestedVcsInfo1),
        "nested-2" to VcsInfoDao.getOrPut(nestedVcsInfo2)
    )

    val ortRunDao = OrtRunDao[fixtures.ortRun.id]
    ortRunDao.vcsId = vcs.id
    ortRunDao.vcsProcessedId = vcsProcessed.id
    vcsNested.forEach { nestedRepository ->
        NestedRepositoriesTable.insert {
            it[ortRunId] = fixtures.ortRun.id
            it[vcsId] = nestedRepository.value.id
            it[path] = nestedRepository.key
        }
    }

    val repositoryConfiguration = OrtTestData.repository.config.mapToModel(fixtures.ortRun.id)

    repositoryConfigurationRepository.create(
        ortRunId = ortRunDao.id.value,
        analyzerConfig = repositoryConfiguration.analyzerConfig,
        excludes = repositoryConfiguration.excludes,
        resolutions = repositoryConfiguration.resolutions,
        curations = repositoryConfiguration.curations,
        packageConfigurations = repositoryConfiguration.packageConfigurations,
        licenseChoices = repositoryConfiguration.licenseChoices
    )

    ortRunDao.mapToModel()
}

private fun getVcsInfo(url: String) = VcsInfo(RepositoryType.GIT, url, "revision", "path")
