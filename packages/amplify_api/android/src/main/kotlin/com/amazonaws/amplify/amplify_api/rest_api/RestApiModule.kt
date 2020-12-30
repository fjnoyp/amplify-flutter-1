package com.amazonaws.amplify.amplify_api.rest_api

import android.os.Handler
import android.os.Looper
import com.amazonaws.amplify.amplify_api.FlutterApiErrorMessage
import com.amazonaws.amplify.amplify_api.FlutterApiErrorUtils
import com.amazonaws.amplify.amplify_api.OperationsManager
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.rest.RestOperation
import com.amplifyframework.api.rest.RestOptions
import com.amplifyframework.api.rest.RestResponse
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.Consumer
import io.flutter.plugin.common.MethodChannel.Result
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction4

typealias RestAPIFunction3 = KFunction3<
        @ParameterName(name = "restOptions") RestOptions,
        @ParameterName(name = "restConsumer") Consumer<RestResponse>,
        @ParameterName(name = "exceptionConsumer") Consumer<ApiException>,
        RestOperation?>

typealias RestAPIFunction4 = KFunction4<
        @ParameterName(name = "apiName") String,
        @ParameterName(name = "restOptions") RestOptions,
        @ParameterName(name = "restConsumer") Consumer<RestResponse>,
        @ParameterName(name = "exceptionConsumer") Consumer<ApiException>,
        RestOperation?>

class RestApiModule {
    companion object {
        private val LOG = Amplify.Logging.forNamespace("amplify:flutter:api")

        private fun restFunctionHelper(
                methodName: String,
                flutterResult: Result,
                request: Map<String, *>,
                function3: RestAPIFunction3,
                function4: RestAPIFunction4
        ) {
            if (!FlutterRestInputs.isValid(flutterResult, request)) return

            val inputs = FlutterRestInputs(request)
            val cancelToken = inputs.getCancelToken()
            val apiName: String? = inputs.getApiPath()
            val options: RestOptions = FlutterRestInputs(request).getRestOptions()

            try {

                if (apiName == null) {
                    var operation: RestOperation? = function3(options,
                            Consumer { result -> prepareRestResponseResult(flutterResult, result, methodName, cancelToken) },
                            Consumer { error ->
                                prepareError(
                                        flutterResult,
                                        FlutterApiErrorMessage.stringToAPIRestError(methodName).toString(),
                                        cancelToken,
                                        error)
                            }
                    )
                    OperationsManager.addOperation(cancelToken, operation!!)
                } else {
                    var operation: RestOperation? = function4(
                            apiName,
                            options,
                            Consumer { result -> prepareRestResponseResult(flutterResult, result, methodName, cancelToken) },
                            Consumer { error ->
                                prepareError(
                                        flutterResult,
                                        FlutterApiErrorMessage.stringToAPIRestError(methodName).toString(),
                                        cancelToken,
                                        error)
                            }
                    )
                    OperationsManager.addOperation(cancelToken, operation!!)
                }

            } catch (e: Exception) {
                prepareError(
                        flutterResult,
                        FlutterApiErrorMessage.stringToAPIRestError(methodName).toString(),
                        cancelToken,
                        e)
            }
        }

        fun prepareError(flutterResult: Result, msg: String, cancelToken: String, error: Exception) {
            if (!cancelToken.isNullOrEmpty()) OperationsManager.removeOperation(cancelToken)
            FlutterApiErrorUtils.postFlutterError(flutterResult, msg, error)
        }

        private fun prepareRestResponseResult(flutterResult: Result, result: RestResponse, methodName: String, cancelToken: String = "") {

            var restResponse = FlutterRestResponse(result)

            if (!cancelToken.isNullOrEmpty()) OperationsManager.removeOperation(cancelToken)

            // if code is not 200 then throw an exception
            if (!result.code.isSuccessful) {
                FlutterApiErrorUtils.handleAPIError(
                        flutterResult,
                        FlutterApiErrorMessage.stringToAPIRestError(methodName).toString(),
                        "The HTTP response status code is [" + result.code.toString().substring(16, 19) + "].",
                        """
                    The metadata associated with the response is contained in the HTTPURLResponse.
                    For more information on HTTP status codes, take a look at
                    https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
                    """
                )
                return
            } else {
                Handler(Looper.getMainLooper()).post {
                    flutterResult.success(restResponse.toValueMap())
                }
            }
        }

        fun onGet(flutterResult: Result, arguments: Map<String, *>) {
            restFunctionHelper("get", flutterResult, arguments, this::get, this::get)
        }

        fun onPost(flutterResult: Result, arguments: Map<String, *>) {
            restFunctionHelper("post", flutterResult, arguments, this::post, this::post)
        }

        fun onPut(flutterResult: Result, arguments: Map<String, *>) {
            restFunctionHelper("put", flutterResult, arguments, this::put, this::put)
        }

        fun onDelete(flutterResult: Result, arguments: Map<String, *>) {
            restFunctionHelper("delete", flutterResult, arguments, this::delete, this::delete)
        }

        fun onHead(flutterResult: Result, arguments: Map<String, *>) {
            restFunctionHelper("head", flutterResult, arguments, this::head, this::head)
        }

        fun onPatch(flutterResult: Result, arguments: Map<String, *>) {
            restFunctionHelper("patch", flutterResult, arguments, this::patch, this::patch)
        }


        /*
        GET
        */
        private fun get(
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.get(restOptions, restConsumer, exceptionConsumer)
        }

        private fun get(
                apiName: String,
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.get(apiName, restOptions, restConsumer, exceptionConsumer)
        }

        /*
        POST
         */
        private fun post(
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.post(restOptions, restConsumer, exceptionConsumer)
        }

        private fun post(
                apiName: String,
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.post(apiName, restOptions, restConsumer, exceptionConsumer)
        }

        /*
        PUT
         */
        private fun put(
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.put(restOptions, restConsumer, exceptionConsumer)
        }

        private fun put(
                apiName: String,
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.put(apiName, restOptions, restConsumer, exceptionConsumer)
        }

        /*
        DELETE
         */
        private fun delete(
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.delete(restOptions, restConsumer, exceptionConsumer)
        }

        private fun delete(
                apiName: String,
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.delete(apiName, restOptions, restConsumer, exceptionConsumer)
        }

        /*
        HEAD
        */
        private fun head(
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.head(restOptions, restConsumer, exceptionConsumer)
        }

        private fun head(
                apiName: String,
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.head(apiName, restOptions, restConsumer, exceptionConsumer)
        }

        /*
        PATCH
        */
        private fun patch(
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.patch(restOptions, restConsumer, exceptionConsumer)
        }

        private fun patch(
                apiName: String,
                restOptions: RestOptions,
                restConsumer: Consumer<RestResponse>,
                exceptionConsumer: Consumer<ApiException>): RestOperation? {
            return Amplify.API.patch(apiName, restOptions, restConsumer, exceptionConsumer)
        }
    }
}