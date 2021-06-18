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

package com.amazonaws.amplify.amplify_datastore

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.annotation.VisibleForTesting
import com.amazonaws.amplify.amplify_core.exception.ExceptionMessages
import com.amazonaws.amplify.amplify_core.exception.ExceptionUtil.Companion.createSerializedError
import com.amazonaws.amplify.amplify_core.exception.ExceptionUtil.Companion.createSerializedUnrecognizedError
import com.amazonaws.amplify.amplify_core.exception.ExceptionUtil.Companion.handleAddPluginException
import com.amazonaws.amplify.amplify_core.exception.ExceptionUtil.Companion.postExceptionToFlutterChannel
import com.amazonaws.amplify.amplify_datastore.types.model.FlutterModelSchema
import com.amazonaws.amplify.amplify_datastore.types.model.FlutterSerializedModel
import com.amazonaws.amplify.amplify_datastore.types.model.FlutterSubscriptionEvent
import com.amazonaws.amplify.amplify_datastore.types.query.QueryOptionsBuilder
import com.amazonaws.amplify.amplify_datastore.util.safeCastToList
import com.amazonaws.amplify.amplify_datastore.util.safeCastToMap
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.Consumer
import com.amplifyframework.core.async.Cancelable
import com.amplifyframework.core.model.Model
import com.amplifyframework.core.model.ModelAssociation
import com.amplifyframework.core.model.PrimaryKey
import com.amplifyframework.core.model.query.Page
import com.amplifyframework.core.model.query.QueryOptions
import com.amplifyframework.core.model.query.Where
import com.amplifyframework.core.model.query.predicate.QueryField
import com.amplifyframework.core.model.query.predicate.QueryPredicateGroup
import com.amplifyframework.core.model.query.predicate.QueryPredicates
import com.amplifyframework.datastore.AWSDataStorePlugin
import com.amplifyframework.datastore.DataStoreException
import com.amplifyframework.datastore.appsync.SerializedModel
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.Objects
import kotlin.collections.Map.Entry

/** AmplifyDataStorePlugin */
class AmplifyDataStorePlugin : FlutterPlugin, MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var eventchannel: EventChannel
    private lateinit var observeCancelable: Cancelable
    private val dataStoreObserveEventStreamHandler: DataStoreObserveEventStreamHandler

    lateinit var hubEventChannel: EventChannel
    private val dataStoreHubEventStreamHandler: DataStoreHubEventStreamHandler

    private val handler = Handler(Looper.getMainLooper())
    private val LOG = Amplify.Logging.forNamespace("amplify:flutter:datastore")
    val modelProvider = FlutterModelProvider.instance

    constructor() {
        dataStoreObserveEventStreamHandler = DataStoreObserveEventStreamHandler()
        dataStoreHubEventStreamHandler = DataStoreHubEventStreamHandler()
    }

    @VisibleForTesting
    constructor(eventHandler: DataStoreObserveEventStreamHandler,
            hubEventHandler: DataStoreHubEventStreamHandler) {
        dataStoreObserveEventStreamHandler = eventHandler
        dataStoreHubEventStreamHandler = hubEventHandler
    }

    override fun onAttachedToEngine(
            @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger,
                "com.amazonaws.amplify/datastore")
        channel.setMethodCallHandler(this)
        eventchannel = EventChannel(flutterPluginBinding.binaryMessenger,
                "com.amazonaws.amplify/datastore_observe_events")
        eventchannel.setStreamHandler(dataStoreObserveEventStreamHandler)

        hubEventChannel = EventChannel(flutterPluginBinding.binaryMessenger,
                "com.amazonaws.amplify/datastore_hub_events")
        hubEventChannel.setStreamHandler(dataStoreHubEventStreamHandler)
        LOG.info("Initiated DataStore plugin")
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        var data: Map<String, Any> = HashMap()
        try {
            if (call.arguments != null) {
                data = checkArguments(call.arguments) as HashMap<String, Any>
            }
        } catch (e: Exception) {
            handler.post {
                postExceptionToFlutterChannel(result, "DataStoreException",
                        createSerializedUnrecognizedError(e))
            }
            return
        }
        when (call.method) {
            "query" -> onQuery(result, data)
            "delete" -> onDelete(result, data)
            "save" -> onSave(result, data)
            "clear" -> onClear(result)
            "setupObserve" -> onSetupObserve(result)
            "configureModelProvider" -> onConfigureModelProvider(result, data)
            else -> result.notImplemented()
        }
    }

    private fun onConfigureModelProvider(flutterResult: Result, request: Map<String, Any>) {

        if (!request.containsKey("modelSchemas") || !request.containsKey(
                        "modelProviderVersion") || request["modelSchemas"] !is List<*>) {
            handler.post {
                postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                        createSerializedError(ExceptionMessages.missingExceptionMessage,
                                ExceptionMessages.missingRecoverySuggestion,
                                "Received invalid request from Dart, modelSchemas and/or modelProviderVersion" +
                                        " are not available. Request: " + request.toString()))
            }
            return
        }

        val modelSchemas: List<Map<String, Any>> = request["modelSchemas"].safeCastToList()!!

        val modelProvider = FlutterModelProvider.instance
        val flutterModelSchemaList =
                modelSchemas.map { modelSchema -> FlutterModelSchema(modelSchema) }
        flutterModelSchemaList.forEach { flutterModelSchema ->

            val nativeSchema = flutterModelSchema.convertToNativeModelSchema()
            modelProvider.addModelSchema(
                    flutterModelSchema.name,
                    nativeSchema
            )
        }

        modelProvider.setVersion(request["modelProviderVersion"] as String)

        try {
            Amplify.addPlugin(AWSDataStorePlugin(modelProvider))
        } catch (e: Exception) {
            handleAddPluginException("Datastore", e, flutterResult)
            return 
        }
        flutterResult.success(null)
    }

    @VisibleForTesting
    fun onQuery(flutterResult: Result, request: Map<String, Any>) {
        val modelName: String
        val queryOptions: QueryOptions
        try {
            modelName = request["modelName"] as String
            queryOptions = QueryOptionsBuilder.fromSerializedMap(request)
        } catch (e: Exception) {
            handler.post {
                postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                        createSerializedUnrecognizedError(e))
            }
            return
        }
        val plugin = Amplify.DataStore.getPlugin("awsDataStorePlugin") as AWSDataStorePlugin
        plugin.query(
                modelName,
                queryOptions,
                {
                    try {
                        val flutterSerializedModels = it.asSequence().toList().map { model -> FlutterSerializedModel(model as SerializedModel) }
                        queryNestedModels(
                                flutterSerializedModels,
                                { models ->
                                    val results = models.map { model -> model.toMap() }
                                    LOG.debug("Number of items received " + results.size)
                                    handler.post { flutterResult.success(results) }
                                },
                                { e ->
                                    LOG.error("Nested Query operation failed.", e)
                                    handler.post {
                                        postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                                                createSerializedError(e))
                                    }
                                }
                        )
                    } catch (e: Exception) {
                        handler.post {
                            postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                                    createSerializedUnrecognizedError(e))
                        }
                    }
                },
                { e ->
                    LOG.error("Query operation failed.", e)
                    handler.post {
                        postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                                createSerializedError(e))
                    }
                }
        )
    }

    // accesses association data for a given set of models, and then queries the associated nested models
    private fun queryNestedModels(
            flutterSerializedModels: List<FlutterSerializedModel>,
            onQueryResults: (flutterSerializedModels: List<FlutterSerializedModel>) -> Unit,
            onQueryFailure: (e: DataStoreException) -> Unit
    ) {
        // if there are no models to find nested data for, invoke callback w/ the original list and return
        if (flutterSerializedModels.isEmpty()) {
            onQueryResults(flutterSerializedModels)
            return
        }

        // grab associations from the first model
        val firstModel = flutterSerializedModels[0].serializedModel
        val associations = firstModel.modelSchema?.associations?.entries?.toMutableList()

        // if there are no associations, invoke callback w/ the original list and return
        if (associations == null || associations.isEmpty()) {
            onQueryResults(flutterSerializedModels)
            return
        }

        // transform association data into a hashmap with the nested model IDs, filter out relationships that are not BelongsTo or HasOne
        val associationsMap = associations
                .filter { association -> association.value.name == "BelongsTo" || association.value.name == "HasOne" }
                .associateBy(
                        { association -> association.key },
                        { association -> Pair(association.value, getAssociatedModelIds(flutterSerializedModels, association)) }
                )

        // query the nested models for a list of models with the given associations data
        queryAssociatedModels(flutterSerializedModels, associationsMap, onQueryResults, onQueryFailure )

    }

    // recursively queries the nested data for the given associations of a set of models
    private fun queryAssociatedModels(
            flutterSerializedModels: List<FlutterSerializedModel>,
            associationsMap: Map<String, Pair<ModelAssociation, List<String>>>,
            onQueryResults: (flutterSerializedModels: List<FlutterSerializedModel>) -> Unit,
            onQueryFailure: (e: DataStoreException) -> Unit
    ) {
        // if there are no associations left, invoke callback and return
        if (associationsMap.isEmpty()) {
            onQueryResults(flutterSerializedModels)
            return
        }

        val updatedAssociationsMap = associationsMap.toMutableMap()
        val associationKey = updatedAssociationsMap.keys.first()
        val associationPair = updatedAssociationsMap.remove(associationKey)!!

        val association = associationPair.first
        val associationIds = associationPair.second

        // if there are no association ids, move to next association
        if (associationIds.isEmpty()) {
            queryAssociatedModels(flutterSerializedModels, updatedAssociationsMap, onQueryResults, onQueryFailure)
            return
        }
        val nestedModelName = association.associatedType
        val queryOptions = whereIds(nestedModelName, associationIds)
        batchedQuery(
                nestedModelName,
                queryOptions,
                {
                    val nestedModelHashMap = it.associateBy({ nestedModel -> nestedModel.id }, { nestedModel -> nestedModel })
                    val nestedFlutterSerializedModels = mutableListOf<FlutterSerializedModel>()
                    for (flutterSerializedModel in flutterSerializedModels) {
                        val nestedSerializedModel = flutterSerializedModel.serializedModel.serializedData[associationKey] as SerializedModel
                        val modelId = nestedSerializedModel.serializedData["id"].toString()
                        val nestedModel = nestedModelHashMap[modelId]
                        if (nestedModel != null) {
                            val nestedFlutterSerializedModel = FlutterSerializedModel(nestedModel as SerializedModel)
                            nestedFlutterSerializedModels.add(nestedFlutterSerializedModel)
                            flutterSerializedModel.associations[associationKey] = nestedFlutterSerializedModel
                        } else {
                            // TODO: How to handle when the data is not found in the local DB (data not synced)
                            LOG.error("Nested model $nestedModelName with ID $modelId was not in DB")
                        }
                    }
                    // query nested models, then query the remaining associations of the current set of models
                    queryNestedModels(
                            nestedFlutterSerializedModels,
                            { queryAssociatedModels(flutterSerializedModels, updatedAssociationsMap, onQueryResults, onQueryFailure) },
                            onQueryFailure
                    )
                },
                {
                    onQueryFailure(it)
                }
        )
    }

    // performs a query for each set of QueryOptions and combines the results as if it were a single query
    private fun batchedQuery(
            modelName: String,
            queryOptionsList: List<QueryOptions>,
            onQueryResults: (result: List<Model>) -> Unit,
            onQueryFailure: (e: DataStoreException) -> Unit,
            previousResults: List<Model> = listOf()
    ) {
        val results = previousResults.toMutableList()
        if (queryOptionsList.isEmpty()) {
            onQueryResults(results)
            return
        }
        val mutableQueryOptionsList = queryOptionsList.toMutableList()
        val queryOptions = mutableQueryOptionsList.removeAt(0)
        val plugin = Amplify.DataStore.getPlugin("awsDataStorePlugin") as AWSDataStorePlugin
        plugin.query(
                modelName,
                queryOptions,
                {
                    results.addAll(it.asSequence())
                    batchedQuery(modelName, mutableQueryOptionsList, onQueryResults, onQueryFailure, results)
                },
                onQueryFailure
        )

    }

    // returns a list of associated model IDs for the given list of models and association
    private fun getAssociatedModelIds(
            flutterSerializedModels: List<FlutterSerializedModel>,
            association: MutableMap.MutableEntry<String, ModelAssociation>
    ): List<String> {
        val results: MutableList<String> = mutableListOf()
        val nestedModelKey = association.key
        for (flutterSerializedModel in flutterSerializedModels) {
            val nestedSerializedData = flutterSerializedModel.serializedModel.serializedData[nestedModelKey]
            // if nestedSerializedData is not a SerializedModel, the data does not exist and should be skipped
            // this is most likely the result of a non-required relationship
            if (nestedSerializedData is SerializedModel) {
                val nestedModelId = nestedSerializedData.serializedData["id"].toString()
                results.add(nestedModelId)
            }
        }
        return results
    }

    // creates a list of QueryOptions from the given model name and list of IDs. Based off of Where.id()
    private fun whereIds(modelName: String, modelIds: List<String>): List<QueryOptions> {
        val distinctModelIds = modelIds.toSet().toList()
        // chunk the data to account for SQLite's max query depth of 1000
        val chunkedModelsIds = distinctModelIds.chunked(995)
        return chunkedModelsIds.map { modelIdsChunk ->
            val idField = QueryField.field(modelName, PrimaryKey.fieldName())
            if (modelIdsChunk.size == 1) {
                Where.matches(idField.eq(modelIdsChunk[0]))
            } else {
                var match: QueryPredicateGroup = idField.eq(modelIdsChunk[0]).or(idField.eq(modelIdsChunk[1]))
                for (i in 2 until modelIdsChunk.size) {
                    match = match.or(idField.eq(modelIdsChunk[i]))
                }
                Where.matches(match)
            }
        }

    }

    @VisibleForTesting
    fun onDelete(flutterResult: Result, request: Map<String, Any>) {
        val modelName: String
        val serializedModelData: Map<String, Any>

        try {
            modelName = request["modelName"] as String
            serializedModelData =
                    deserializeNestedModels(request["serializedModel"].safeCastToMap()!!)
        } catch (e: Exception) {
            handler.post {
                postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                        createSerializedUnrecognizedError(e))
            }
            return
        }

        val plugin = Amplify.DataStore.getPlugin("awsDataStorePlugin") as AWSDataStorePlugin
        val schema = modelProvider.modelSchemas()[modelName];

        val instance = SerializedModel.builder()
                .serializedData(serializedModelData)
                .modelSchema(schema)
                .build()

        plugin.delete(
                instance,
                {
                    LOG.info("Deleted item: " + it.item().toString())
                    handler.post { flutterResult.success(null) }
                },
                {
                    LOG.error("Delete operation failed.", it)
                    if (it.localizedMessage == "Wanted to delete one row, but deleted 0 rows.") {
                        handler.post { flutterResult.success(null) }
                    } else {
                        handler.post {
                            postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                                    createSerializedError(it))
                        }
                    }
                }
        )
    }

    @VisibleForTesting
    fun onSave(flutterResult: Result, request: Map<String, Any>) {
        val modelName: String
        val serializedModelData: Map<String, Any>

        try {
            modelName = request["modelName"] as String
            serializedModelData =
                    deserializeNestedModels(request["serializedModel"].safeCastToMap()!!)
        } catch (e: Exception) {
            handler.post {
                postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                        createSerializedUnrecognizedError(e))
            }
            return
        }

        val plugin = Amplify.DataStore.getPlugin("awsDataStorePlugin") as AWSDataStorePlugin
        val schema = modelProvider.modelSchemas()[modelName];

        val serializedModel = SerializedModel.builder()
                .serializedData(serializedModelData)
                .modelSchema(schema)
                .build()

        val predicate = QueryPredicates.all()

        plugin.save(
                serializedModel,
                predicate,
                Consumer {
                    LOG.info("Saved item: " + it.item().toString())
                    handler.post { flutterResult.success(null) }
                },
                Consumer {
                    LOG.error("Save operation failed", it)
                    handler.post {
                        postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                                createSerializedError(it))
                    }
                }
        )
    }

    fun onClear(flutterResult: Result) {
        val plugin = Amplify.DataStore.getPlugin("awsDataStorePlugin") as AWSDataStorePlugin

        plugin.clear(
                {
                    LOG.info("Successfully cleared the store")
                    handler.post { flutterResult.success(null) }
                },
                {
                    LOG.error("Failed to clear store with error: ", it)
                    handler.post {
                        postExceptionToFlutterChannel(flutterResult, "DataStoreException",
                                createSerializedError(it))
                    }
                }
        )
    }

    fun onSetupObserve(result: Result) {
        val plugin = Amplify.DataStore.getPlugin("awsDataStorePlugin") as AWSDataStorePlugin

        plugin.observe(
                { cancelable ->
                    LOG.info("Established a new stream form flutter $cancelable")
                    observeCancelable = cancelable
                },
                { event ->
                    LOG.debug("Received event: $event")
                    if (event.item() is SerializedModel) {
                        dataStoreObserveEventStreamHandler.sendEvent(FlutterSubscriptionEvent(
                                serializedModel = event.item() as SerializedModel,
                                eventType = event.type().name.toLowerCase()).toMap())
                    }
                },
                { failure: DataStoreException ->
                    LOG.error("Received an error", failure)
                    dataStoreObserveEventStreamHandler.error("DataStoreException",
                            createSerializedError(failure))
                },
                { LOG.info("Observation complete.") }
        )
        result.success(true)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun checkArguments(@NonNull args: Any): Map<String, Any> {
        if (args !is Map<*, *>) {
            throw java.lang.Exception("Flutter method call arguments are not a map.")
        }
        return args.safeCastToMap()!!
    }

    @VisibleForTesting
    fun deserializeNestedModels(serializedModelData: Map<String, Any>): Map<String, Any> {
        return serializedModelData.mapValues {
            if (it.value is Map<*, *>) {
                SerializedModel.builder()
                        .serializedData(deserializeNestedModels(it.value as HashMap<String, Any>))
                        .modelSchema(null)
                        .build() as Any
            } else
                it.value
        }
    }
}
