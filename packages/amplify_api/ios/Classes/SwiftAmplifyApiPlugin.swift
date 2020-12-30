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

import Flutter
import UIKit
import Amplify
import AmplifyPlugins

public class SwiftAmplifyApiPlugin: NSObject, FlutterPlugin {

    private let bridge: ApiBridge

    init(
        bridge: ApiBridge = ApiBridge()
    ){
        self.bridge = bridge
    }

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "com.amazonaws.amplify/api", binaryMessenger: registrar.messenger())
        let instance = SwiftAmplifyApiPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        do {
            try Amplify.add(plugin: AWSAPIPlugin())
            print("Successfully added API Plugin")
        } catch {
            print("Failed to add Amplify API Plugin \(error)")
        }
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        innerHandle(method: call.method, callArgs: call.arguments as Any, result: result)
    }

    // Create separate method to allow unit testing as we cannot mock "FlutterMethodCall"
    public func innerHandle(method: String, callArgs: Any, result: @escaping FlutterResult){

        do {
            if(method == "cancel"){
                let cancelToken = try FlutterApiRequestUtils.getCancelToken(args: callArgs)
                onCancel(flutterResult: result, cancelToken: cancelToken)
                return
            }
            
            let arguments = try FlutterApiRequestUtils.getMap(args: callArgs as Any)
            switch method {
                case "get": RestApiModule.onGet(flutterResult: result, arguments: arguments, bridge: bridge)
                case "post": RestApiModule.onPost(flutterResult: result, arguments: arguments, bridge: bridge)
                case "put": RestApiModule.onPut(flutterResult: result, arguments: arguments, bridge: bridge)
                case "delete": RestApiModule.onDelete(flutterResult: result, arguments: arguments, bridge: bridge)
                case "head": RestApiModule.onHead(flutterResult: result, arguments: arguments, bridge: bridge)
                case "patch": RestApiModule.onPatch(flutterResult: result, arguments: arguments, bridge: bridge)

                case "query":
                    GraphQLApiModule.query(flutterResult: result, request: arguments, bridge: bridge)
                case "mutate":
                    GraphQLApiModule.mutate(flutterResult: result, request: arguments, bridge: bridge)
                default:
                    result(FlutterMethodNotImplemented)
            }
        } catch let error {
            print("Failed to parse query arguments with \(error)")
            FlutterApiErrorUtils.handleAPIError(flutterResult: result, error: error as! APIError, msg: FlutterApiErrorMessage.MALFORMED.rawValue)
        }
    }
    
    public func onCancel(flutterResult: @escaping FlutterResult, cancelToken: String){
        if(OperationsManager.containsOperation(cancelToken: cancelToken)){
            OperationsManager.cancelOperation(cancelToken: cancelToken)
            flutterResult("Operation Canceled")
        }
        else{
            flutterResult(FlutterError(
                            code: "AmplifyRestAPI-CancelError",
                            message: "The RestOperation may have already completed or expired and cannot be canceled anymore",
                            details: "Operation does not exist"))
        }
    }

}
