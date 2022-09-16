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

package org.ossreviewtoolkit.server.core.api

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.patch
import io.github.smiley4.ktorswaggerui.dsl.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.api.v1.CreateRepository
import org.ossreviewtoolkit.server.api.v1.UpdateProduct
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.api.v1.mapToModel
import org.ossreviewtoolkit.server.core.apiDocs.deleteProductById
import org.ossreviewtoolkit.server.core.apiDocs.getProductById
import org.ossreviewtoolkit.server.core.apiDocs.getRepositoriesByProductId
import org.ossreviewtoolkit.server.core.apiDocs.patchProductById
import org.ossreviewtoolkit.server.core.apiDocs.postRepository
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.services.ProductService

fun Route.products() = route("products/{productId}") {
    val productService by inject<ProductService>()

    get(getProductById) {
        val id = call.requireParameter("productId").toLong()

        val product = productService.getProduct(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch(patchProductById) {
        val id = call.requireParameter("productId").toLong()
        val updateProduct = call.receive<UpdateProduct>()

        val updatedProduct =
            productService.updateProduct(id, updateProduct.name, updateProduct.description)

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApi())
    }

    delete(deleteProductById) {
        val id = call.requireParameter("productId").toLong()

        productService.deleteProduct(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get(getRepositoriesByProductId) {
            val id = call.requireParameter("productId").toLong()

            call.respond(
                HttpStatusCode.OK,
                productService.listRepositoriesForProduct(id).map { it.mapToApi() }
            )
        }

        post(postRepository) {
            val id = call.requireParameter("productId").toLong()
            val createRepository = call.receive<CreateRepository>()

            call.respond(
                HttpStatusCode.Created,
                productService.createRepository(createRepository.type.mapToModel(), createRepository.url, id)
                    .mapToApi()
            )
        }
    }
}
