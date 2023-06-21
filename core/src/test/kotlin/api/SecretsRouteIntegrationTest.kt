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

package org.ossreviewtoolkit.server.core.api

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretStorage
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting

private const val SECRET = "unguessable-secret-value"
private const val ERROR_PATH = "thisWillThrow"

@Suppress("LargeClass")
class SecretsRouteIntegrationTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var secretRepository: SecretRepository

    var organizationId = -1L
    var productId = -1L
    var repositoryId = -1L

    beforeEach {
        secretRepository = dbExtension.fixtures.secretRepository

        organizationId = dbExtension.fixtures.organization.id
        productId = dbExtension.fixtures.product.id
        repositoryId = dbExtension.fixtures.repository.id
    }

    "GET /organizations/{organizationId}/secrets" should {
        "return all secrets for this organization" {
            ortServerTestApplication(dbExtension.db, noDbConfig) {
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_1",
                    "New secret 1",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )
                val secret2 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_2",
                    "New secret 2",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/$organizationId/secrets") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            secretsTestApplication(dbExtension.db) {
                dbExtension.fixtures.organizationRepository.create(name = "name1", description = "description1")
                secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_3",
                    "New secret 3",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_4",
                    "New secret 4",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/$organizationId/secrets?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }
    }

    "GET /organizations/{organizationId}/secrets/{secretId}" should {
        "return a single secret" {
            secretsTestApplication(dbExtension.db) {
                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, organizationId, null, null)

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/$organizationId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, description)
                }
            }
        }

        "respond with NotFound if no secret exists" {
            secretsTestApplication(dbExtension.db) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/$organizationId/secrets/999999") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    "POST /organizations/{organizationId}/secrets" should {
        "create a secret in the database" {
            secretsTestApplication(dbExtension.db) {
                val client = createJsonClient()

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    SECRET,
                    "The new org secret"
                )

                val response = client.post("/api/v1/organizations/$organizationId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Secret>() shouldBe Secret(
                        secret.name,
                        secret.description
                    )
                }

                secretRepository.getByOrganizationIdAndName(organizationId, name)?.mapToApi().shouldBe(
                    Secret(secret.name, secret.description)
                )

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${organizationId}_$name"))?.value shouldBe SECRET
            }
        }

        "respond with CONFLICT if the secret already exists" {
            secretsTestApplication(dbExtension.db) {
                val name = "New secret 7"
                val description = "description"

                val secret1 = CreateSecret(name, SECRET, description)
                val secret2 = secret1.copy(value = "someOtherValue")

                val client = createJsonClient()

                val response1 = client.post("/api/v1/organizations/$organizationId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret1)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = client.post("/api/v1/organizations/$organizationId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret2)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${organizationId}_$name"))?.value shouldBe SECRET
            }
        }
    }

    "PATCH /organizations/{organizationId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            secretsTestApplication(dbExtension.db) {
                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", organizationId, null, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = client.patch("/api/v1/organizations/$organizationId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, updatedDescription)
                }

                secretRepository.getByOrganizationIdAndName(
                    organizationId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, updatedDescription)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path)) should beNull()
            }
        }

        "update a secret's value" {
            secretsTestApplication(dbExtension.db) {
                val name = "name"
                val desc = "description"
                val path = "path"

                secretRepository.create(path, name, desc, organizationId, null, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), SECRET.asPresent())
                val response = client.patch("/api/v1/organizations/$organizationId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, desc)
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path))?.value shouldBe SECRET
            }
        }

        "handle failures of the SecretStorage" {
            secretsTestApplication(dbExtension.db) {
                val name = "name"
                val desc = "description"

                secretRepository.create(ERROR_PATH, name, desc, organizationId, null, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), SECRET.asPresent(), "newDesc".asPresent())
                val response = client.patch("/api/v1/organizations/$organizationId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByOrganizationIdAndName(
                    organizationId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }

    "DELETE /organizations/{organizationId}/secrets/{secretName}" should {
        "delete a secret" {
            secretsTestApplication(dbExtension.db) {
                val path = SecretsProviderFactoryForTesting.TOKEN_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", organizationId, null, null)

                val client = createJsonClient()

                val response = client.delete("/api/v1/organizations/$organizationId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForOrganization(organizationId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle a failure from the SecretStorage" {
            secretsTestApplication(dbExtension.db) {
                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(ERROR_PATH, name, desc, organizationId, null, null)

                val client = createJsonClient()

                val response = client.delete("/api/v1/organizations/$organizationId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByOrganizationIdAndName(
                    organizationId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }

    "GET /products/{productId}/secrets" should {
        "return all secrets for this product" {
            ortServerTestApplication(dbExtension.db, noDbConfig) {
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_1",
                    "New secret 1",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )
                val secret2 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_2",
                    "New secret 2",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            secretsTestApplication(dbExtension.db) {
                secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_3",
                    "New secret 3",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_4",
                    "New secret 4",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }
    }

    "GET /products/{productId}/secrets/{secretId}" should {
        "return a single secret" {
            secretsTestApplication(dbExtension.db) {
                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, null, productId, null)

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, description)
                }
            }
        }

        "respond with NotFound if no secret exists" {
            secretsTestApplication(dbExtension.db) {
                val client = createJsonClient()

                val response = client.get("/api/v1/products/$organizationId/secrets/999999") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    "POST /products/{productId}/secrets" should {
        "create a secret in the database" {
            secretsTestApplication(dbExtension.db) {
                val client = createJsonClient()

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    SECRET,
                    "The new prod secret"
                )

                val response = client.post("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Secret>() shouldBe Secret(
                        secret.name,
                        secret.description
                    )
                }

                secretRepository.getByProductIdAndName(productId, name)?.mapToApi().shouldBe(
                    Secret(secret.name, secret.description)
                )

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("product_${productId}_$name"))?.value shouldBe SECRET
            }
        }

        "respond with CONFLICT if the secret already exists" {
            secretsTestApplication(dbExtension.db) {
                val name = "New secret 7"
                val description = "description"

                val secret = CreateSecret(name, SECRET, description)

                val client = createJsonClient()

                val response1 = client.post("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = client.post("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }

    "PATCH /products/{productId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            secretsTestApplication(dbExtension.db) {
                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", null, productId, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = client.patch("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, updatedDescription)
                }

                secretRepository.getByProductIdAndName(
                    organizationId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, updatedDescription)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path)) should beNull()
            }
        }

        "update a secret's value" {
            secretsTestApplication(dbExtension.db) {
                val name = "name"
                val path = "path"
                val desc = "some description"

                secretRepository.create(path, name, desc, null, productId, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), SECRET.asPresent())
                val response = client.patch("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, desc)
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path))?.value shouldBe SECRET
            }
        }

        "handle a failure from the SecretsStorage" {
            secretsTestApplication(dbExtension.db) {
                val name = "name"
                val desc = "description"

                secretRepository.create(ERROR_PATH, name, desc, null, productId, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), "newVal".asPresent(), "newDesc".asPresent())
                val response = client.patch("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByProductIdAndName(
                    organizationId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }

    "DELETE /products/{productId}/secrets/{secretName}" should {
        "delete a secret" {
            secretsTestApplication(dbExtension.db) {
                val path = SecretsProviderFactoryForTesting.PASSWORD_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", null, productId, null)

                val client = createJsonClient()

                val response = client.delete("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForProduct(productId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle a failure from the SecretsStorage" {
            secretsTestApplication(dbExtension.db) {
                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(ERROR_PATH, name, desc, null, productId, null)

                val client = createJsonClient()

                val response = client.delete("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByProductIdAndName(
                    productId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets" should {
        "return all secrets for this repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig) {
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_1",
                    "New secret 1",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )
                val secret2 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_2",
                    "New secret 2",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            secretsTestApplication(dbExtension.db) {
                secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_3",
                    "New secret 3",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_4",
                    "New secret 4",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets/{secretId}" should {
        "return a single secret" {
            secretsTestApplication(dbExtension.db) {
                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, null, null, repositoryId)

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, description)
                }
            }
        }

        "respond with NotFound if no secret exists" {
            secretsTestApplication(dbExtension.db) {
                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets/999999") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    "POST /repositories/{repositoryId}/secrets" should {
        "create a secret in the database" {
            secretsTestApplication(dbExtension.db) {
                val client = createJsonClient()

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    SECRET,
                    "The new repo secret"
                )

                val response = client.post("/api/v1/repositories/$repositoryId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Secret>() shouldBe Secret(
                        secret.name,
                        secret.description
                    )
                }

                secretRepository.getByRepositoryIdAndName(repositoryId, name)?.mapToApi().shouldBe(
                    Secret(secret.name, secret.description)
                )

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("repository_${repositoryId}_$name"))?.value shouldBe SECRET
            }
        }

        "respond with CONFLICT if the secret already exists" {
            secretsTestApplication(dbExtension.db) {
                val name = "New secret 7"
                val description = "description"

                val secret = CreateSecret(name, SECRET, description)

                val client = createJsonClient()

                val response1 = client.post("/api/v1/repositories/$repositoryId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = client.post("/api/v1/repositories/$repositoryId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }

    "PATCH /repositories/{repositoryId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            secretsTestApplication(dbExtension.db) {
                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", null, null, repositoryId)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = client.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, updatedDescription)
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, updatedDescription)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path)) should beNull()
            }
        }

        "handle a failure from the SecretsStorage" {
            secretsTestApplication(dbExtension.db) {
                val name = "name"
                val desc = "description"

                secretRepository.create(ERROR_PATH, name, desc, null, null, repositoryId)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), "newVal".asPresent(), "newDesc".asPresent())
                val response = client.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }

        "update a secret's value" {
            secretsTestApplication(dbExtension.db) {
                val name = "name"
                val path = "path"
                val desc = "some description"

                secretRepository.create(path, name, desc, null, null, repositoryId)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), SECRET.asPresent())
                val response = client.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, desc)
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path))?.value shouldBe SECRET
            }
        }
    }

    "DELETE /repositories/{repositoryId}/secrets/{secretName}" should {
        "delete a secret" {
            secretsTestApplication(dbExtension.db) {
                val path = SecretsProviderFactoryForTesting.SERVICE_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", null, null, repositoryId)

                val client = createJsonClient()

                val response = client.delete("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForRepository(repositoryId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle failures from the SecretStorage" {
            secretsTestApplication(dbExtension.db) {
                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(ERROR_PATH, name, desc, null, null, repositoryId)

                val client = createJsonClient()

                val response = client.delete("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }
})

/**
 * Helper function to create a test application running [block] which is configured to use the test secrets provider
 * implementation.
 */
fun secretsTestApplication(db: Database, block: suspend ApplicationTestBuilder.() -> Unit) {
    val config = mapOf(
        "${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to SecretsProviderFactoryForTesting.NAME,
        "${SecretStorage.CONFIG_PREFIX}.${SecretsProviderFactoryForTesting.ERROR_PATH_PROPERTY}" to ERROR_PATH
    )
    ortServerTestApplication(db, noDbConfig, config, block)
}
