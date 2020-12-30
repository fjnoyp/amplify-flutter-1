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

package com.amazonaws.amplify.amplify_api

import android.os.Handler
import android.os.Looper
import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.core.Amplify
import io.flutter.plugin.common.MethodChannel

class GraphQLApiModule {
    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private val LOG = Amplify.Logging.forNamespace("amplify:flutter:api")

        @JvmStatic
        fun query(flutterResult: MethodChannel.Result, request: Map<String, Any>) {
            var document: String
            var variables: Map<String, Any>
            var cancelToken: String

            try {
                document = FlutterApiRequestUtils.getGraphQLDocument(request)
                variables = FlutterApiRequestUtils.getVariables(request)
                cancelToken = FlutterApiRequestUtils.getCancelToken(request)
            } catch (e: Exception) {
                FlutterApiErrorUtils.postFlutterError(
                        flutterResult,
                        FlutterApiErrorMessage.AMPLIFY_REQUEST_MALFORMED.toString(),
                        e)
                return
            }
            var operation = Amplify.API.query(
                    SimpleGraphQLRequest<String>(
                            document,
                            variables,
                            String::class.java,
                            GsonVariablesSerializer()
                    ),
                    { response ->
                        if (!cancelToken.isNullOrEmpty()) OperationsManager.removeOperation(cancelToken)

                        var result: Map<String, Any> = mapOf(
                                "data" to response.data,
                                "errors" to response.errors.map { it.message }
                        )
                        LOG.debug("GraphQL query operation succeeded with response: $result")
                        handler.post { flutterResult.success(result) }
                    },
                    {
                        if (!cancelToken.isNullOrEmpty()) OperationsManager.removeOperation(cancelToken)

                        LOG.error("GraphQL query operation failed", it)
                        FlutterApiError.postFlutterError(
                                flutterResult,
                                FlutterApiErrorMessage.AMPLIFY_API_QUERY_FAILED.toString(),
                                it)
                    }
            )
            if (operation != null) {
                OperationsManager.addOperation(cancelToken, operation)
            }
        }

        @JvmStatic
        fun mutate(flutterResult: MethodChannel.Result, request: Map<String, Any>) {
            var document: String
            var variables: Map<String, Any>
            var cancelToken: String

            try {
<<<<<<< HEAD:packages/amplify_api/android/src/main/kotlin/com/amazonaws/amplify/amplify_api/GraphQLApiModule.kt
                document = FlutterApiRequestUtils.getGraphQLDocument(request)
                variables = FlutterApiRequestUtils.getVariables(request)
                cancelToken = FlutterApiRequestUtils.getCancelToken(request)
=======
                document = FlutterApiRequest.getGraphQLDocument(request)
                variables = FlutterApiRequest.getVariables(request)
>>>>>>> a39e41a08179f0cec0f2a2589f0e88ea6732734c:packages/amplify_api/android/src/main/kotlin/com/amazonaws/amplify/amplify_api/FlutterGraphQLApi.kt
            } catch (e: Exception) {
                FlutterApiError.postFlutterError(
                        flutterResult,
                        FlutterApiErrorMessage.AMPLIFY_REQUEST_MALFORMED.toString(),
                        e)
                return
            }
            var operation = Amplify.API.mutate(
                    SimpleGraphQLRequest<String>(
                            document,
                            variables,
                            String::class.java,
                            GsonVariablesSerializer()
                    ),
                    { response ->
                        if (!cancelToken.isNullOrEmpty()) OperationsManager.removeOperation(cancelToken)

                        var result: Map<String, Any> = mapOf(
                                "data" to response.data,
                                "errors" to response.errors.map { it.message }
                        )
                        LOG.debug("GraphQL mutate operation succeeded with response : $result")
                        handler.post { flutterResult.success(result) }
                    },
                    {
                        if (!cancelToken.isNullOrEmpty()) OperationsManager.removeOperation(cancelToken)

                        LOG.error("GraphQL mutate operation failed", it)
                        FlutterApiError.postFlutterError(
                                flutterResult,
                                FlutterApiErrorMessage.AMPLIFY_API_MUTATE_FAILED.toString(),
                                it)
                    }
            )
            if (operation != null) {
                OperationsManager.addOperation(cancelToken, operation)
            }
        }
    }
}