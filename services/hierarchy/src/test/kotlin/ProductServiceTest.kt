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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk

import org.eclipse.apoapsis.ortserver.dao.repositories.DaoProductRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.RepositoryType

import org.jetbrains.exposed.sql.Database

class ProductServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var productRepository: DaoProductRepository
    lateinit var repositoryRepository: DaoRepositoryRepository
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        productRepository = dbExtension.fixtures.productRepository
        repositoryRepository = dbExtension.fixtures.repositoryRepository
        fixtures = dbExtension.fixtures
    }

    "createRepository" should {
        "create Keycloak permissions" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { createRepositoryPermissions(any()) } just runs
                coEvery { createRepositoryRoles(any()) } just runs
            }

            val service = ProductService(db, productRepository, repositoryRepository, authorizationService)
            val repository =
                service.createRepository(RepositoryType.GIT, "https://example.com/repo.git", fixtures.product.id)

            coVerify(exactly = 1) {
                authorizationService.createRepositoryPermissions(repository.id)
                authorizationService.createRepositoryRoles(repository.id)
            }
        }
    }

    "deleteProduct" should {
        "delete Keycloak permissions" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { deleteProductPermissions(any()) } just runs
                coEvery { deleteProductRoles(any()) } just runs
            }

            val service = ProductService(db, productRepository, repositoryRepository, authorizationService)
            service.deleteProduct(fixtures.product.id)

            coVerify(exactly = 1) {
                authorizationService.deleteProductPermissions(fixtures.product.id)
                authorizationService.deleteProductRoles(fixtures.product.id)
            }
        }
    }

    "addUserToGroup" should {
        "throw an exception if the organization does not exist" {
            val service = ProductService(db, productRepository, repositoryRepository, mockk())

            shouldThrow<ResourceNotFoundException> {
                service.addUserToGroup("username", 1, "readers")
            }
        }

        "throw an exception if the group does not exist" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                ProductService(
                    db,
                    productRepository,
                    repositoryRepository,
                    authorizationService
                )
            ) {
                println(getProduct(1))
                coEvery { getProduct(any()) } returns Product(1, 1, "name")
            }

            shouldThrow<ResourceNotFoundException> {
                service.addUserToGroup("username", 1, "viewers")
            }
        }

        "generate the Keycloak group name" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                ProductService(
                    db,
                    productRepository,
                    repositoryRepository,
                    authorizationService
                )
            ) {
                coEvery { getProduct(any()) } returns Product(1, 1, "name")
            }

            service.addUserToGroup("username", 1, "readers")

            coVerify(exactly = 1) {
                authorizationService.addUserToGroup(
                    "username",
                    "PRODUCT_1_READERS"
                )
            }
        }
    }

    "removeUsersFromGroup" should {
        "throw an exception if the organization does not exist" {
            val service = ProductService(db, productRepository, repositoryRepository, mockk())

            shouldThrow<ResourceNotFoundException> {
                service.removeUserFromGroup("username", 1, "readers")
            }
        }

        "throw an exception if the group does not exist" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                ProductService(
                    db,
                    productRepository,
                    repositoryRepository,
                    authorizationService
                )
            ) {
                coEvery { getProduct(any()) } returns Product(1, 1, "name")
            }

            shouldThrow<ResourceNotFoundException> {
                service.removeUserFromGroup("username", 1, "viewers")
            }
        }

        "generate the Keycloak group name" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { removeUserFromGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                ProductService(
                    db,
                    productRepository,
                    repositoryRepository,
                    authorizationService
                )
            ) {
                coEvery { getProduct(any()) } returns Product(1, 1, "name")
            }

            service.removeUserFromGroup("username", 1, "readers")

            coVerify(exactly = 1) {
                authorizationService.removeUserFromGroup(
                    "username",
                    "PRODUCT_1_READERS"
                )
            }
        }
    }
})
