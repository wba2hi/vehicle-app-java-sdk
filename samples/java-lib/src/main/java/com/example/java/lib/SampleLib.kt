/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.java.lib

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import io.grpc.ManagedChannelBuilder
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnector
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssNodeListener
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssPathListener
import org.eclipse.kuksa.connectivity.databroker.v1.request.FetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.SubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.UpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeFetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeSubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeUpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeRequestV2
import org.eclipse.kuksa.proto.v1.KuksaValV1
import org.eclipse.kuksa.proto.v1.Types.Datapoint
import org.eclipse.kuksa.proto.v2.Types
import org.eclipse.kuksa.proto.v2.Types.SignalID
import org.eclipse.velocitas.vss.VssVehicle

class SampleLib {
    suspend fun main() {
        coroutineScope {
            launch {
                useVehicleModel()
                useKuksaValV1()
                useKuksaValV2()
            }
        }
    }

    @Suppress("MagicNumber")
    private suspend fun useVehicleModel() {
        val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55556)
            .usePlaintext()
            .build()
        val dataBrokerConnector = DataBrokerConnector(managedChannel)

        val dataBrokerConnection = dataBrokerConnector.connect().kuksaValV1

        println("Using protocol kuksa.val.v1 with VehicleModel")
        println("Setting Vehicle.Speed in Databroker to 100")
        val vssSpeedWithValue = VssVehicle.VssSpeed(100.0F)
        val updateRequest = VssNodeUpdateRequest(vssSpeedWithValue)
        dataBrokerConnection.update(updateRequest)

        println("Reading Vehicle.Speed from Databroker")
        val vssSpeed = VssVehicle.VssSpeed()
        val fetchRequest = VssNodeFetchRequest(vssSpeed)
        val response = dataBrokerConnection.fetch(fetchRequest)
        println("VssSpeed: $response")

        println("Observe Vehicle.Speed")
        val subscribeRequest = VssNodeSubscribeRequest(vssSpeed)
        dataBrokerConnection.subscribe(
            subscribeRequest,
            object : VssNodeListener<VssVehicle.VssSpeed> {
                override fun onError(throwable: Throwable) {
                    // handle error
                }

                override fun onNodeChanged(vssNode: VssVehicle.VssSpeed) {
                    // handle VssSpeed change
                    println("newSpeed(VehicleModel): $vssNode")
                }
            },
        )
    }

    @Suppress("MagicNumber")
    private suspend fun useKuksaValV1() {
        val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55556)
            .usePlaintext()
            .build()
        val dataBrokerConnector = DataBrokerConnector(managedChannel)

        val dataBrokerConnection = dataBrokerConnector.connect().kuksaValV1

        println("Using protocol kuksa.val.v1")
        println("Setting Vehicle.Speed in Databroker to 80")
        val vssPath = "Vehicle.Speed"

        val dataPoint = Datapoint.newBuilder().setFloat(80.0F).build()
        val updateRequest = UpdateRequest(vssPath, dataPoint)
        dataBrokerConnection.update(updateRequest)

        println("Reading Vehicle.Speed from Databroker")
        val fetchRequest = FetchRequest(vssPath)
        val response = dataBrokerConnection.fetch(fetchRequest)
        println("GetResponse: $response")

        println("Observe Vehicle.Speed")
        val subscribeRequest = SubscribeRequest("Vehicle.Speed")
        dataBrokerConnection.subscribe(
            subscribeRequest,
            object : VssPathListener {
                override fun onEntryChanged(entryUpdates: List<KuksaValV1.EntryUpdate>) {
                    entryUpdates.forEach { entryUpdate ->
                        val vehicleSpeedValue = entryUpdate.entry.value.float
                        // handle change
                        println("newSpeed(v1): $vehicleSpeedValue")
                    }
                }

                override fun onError(throwable: Throwable) {
                    // handle error
                }
            },
        )
    }

    @Suppress("MagicNumber")
    private suspend fun useKuksaValV2() {
        val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55556)
            .usePlaintext()
            .build()
        val dataBrokerConnector = DataBrokerConnector(managedChannel)

        val dataBrokerConnection = dataBrokerConnector.connect().kuksaValV2

        println("Using protocol kuksa.val.v2")
        println("Setting Vehicle.Speed in Databroker to 60")

        val signalId = SignalID.newBuilder().setPath("Vehicle.Speed").build()
        val speedValue = Types.Value.newBuilder().setFloat(60.0F).build()
        val datapoint = Types.Datapoint.newBuilder().setValue(speedValue).build()
        val publishValueRequest = PublishValueRequestV2(signalId, datapoint)

        dataBrokerConnection.publishValue(publishValueRequest)

        println("Reading Vehicle.Speed from Databroker")
        val fetchValueRequest = FetchValueRequestV2(signalId)
        val fetchResponse = dataBrokerConnection.fetchValue(fetchValueRequest)
        println("FetchValueResponse: $fetchResponse")

        println("Observe Vehicle.Speed")
        val signalPaths = listOf("Vehicle.Speed")
        val subscribeRequest = SubscribeRequestV2(signalPaths)
        val responseFlow = dataBrokerConnection.subscribe(subscribeRequest)
        responseFlow.collect { response ->
            val vehicleSpeedValue = response.entriesMap["Vehicle.Speed"]?.value?.float
            // handle change
            println("newSpeed(v2): $vehicleSpeedValue")
        }
    }
}
