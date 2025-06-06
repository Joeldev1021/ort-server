/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model

/**
 * A product represents a group of [repositories][Repository] that belong together.
 */
data class Product(
    /**
     * The unique identifier of the product.
     */
    val id: Long,

    /**
     * The unique identifier of the [Organization] this product belongs to.
     */
    val organizationId: Long,

    /**
     * The name of the product. Must be unique within an [Organization].
     */
    val name: String,

    /**
     * The description of the product.
     */
    val description: String? = null
)
