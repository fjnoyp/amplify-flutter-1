/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.amplify.amplify_datastore.types.model

import com.amplifyframework.core.model.Model
import com.amplifyframework.core.model.ModelAssociation
import com.amplifyframework.core.model.ModelSchema
import com.amplifyframework.core.model.temporal.Temporal
import com.amplifyframework.datastore.appsync.SerializedModel
import java.lang.Exception


data class FlutterSerializedModel(val serializedModel: SerializedModel) {

    val associations: MutableMap<String, FlutterSerializedModel> = HashMap<String, FlutterSerializedModel>();

    // ignored fields
    private val id: String = serializedModel.id
    private val modelName: String = parseModelName(serializedModel.modelName) // ModelSchema -> SerializedModel should always have a name

    fun toMap(): Map<String, Any> {
        return mapOf(
                "id" to id,
                "serializedData" to parseSerializedDataMap(),
                "modelName" to modelName)
    }

    private fun parseModelName(modelName: String?) : String{
        return if(modelName.isNullOrEmpty()) ""
        else modelName!!
    }

    private fun parseSerializedDataMap(): Map<String, Any> {

        if(serializedModel.serializedData == null) throw Exception("FlutterSerializedModel - no serializedData")

        return serializedModel.serializedData.mapValues {
            when (val value: Any = it.value) {
                is Temporal.DateTime -> value.format()
                is Temporal.Date -> value.format()
                is Temporal.Time -> value.format()
                is Model -> FlutterSerializedModel(value as SerializedModel).toMap()
                is Temporal.Timestamp -> value.secondsSinceEpoch
                // TODO add for other complex objects that can be returned or be part of the codegen model
                // It seems we can ignore collection types for now as we aren't returning lists of Models in hasMany relationships
                else -> value
            }
        }.plus(associations.mapValues { it.value.parseSerializedDataMap() })
    }
}
