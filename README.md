# vehicle-app-java-sdk

Vehicle App Java SDK

This project is in incubation status. Not all required functionality might be migrated yet.

> [!IMPORTANT]
> **AI Usage Notice**
>
> This repository partially contains AI-generated code using GitHub Copilot Business.
> This notice must remain attached to any reproduction of this repository.

## Overview

The Velocitas Vehicle App Java SDK provides functionality to ease the implementation of Automotive
Java Applications.

## Databroker Interaction

build.gradle.kts
```kotlin
dependencies {
    implementation("org.eclipse.velocitas:vehicle-app-java-sdk:<VERSION>")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:<VERSION>")
}
```

Using protocol kuksa.val.v1:

```kotlin
    val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55555)
        .usePlaintext()
        .build()
    val dataBrokerConnector = DataBrokerConnector(managedChannel)

    coroutineScope {
        launch {
            val dataBrokerConnection = dataBrokerConnector.connect()

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
                            // handle changes
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        // handle error
                    }
                },
            )
        }
    }
```

Using protocol kuksa.val.v2

```kotlin
    val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55556)
        .usePlaintext()
        .build()
    val dataBrokerConnector = DataBrokerConnectorV2(managedChannel)

    coroutineScope {
        launch {
            val dataBrokerConnection = dataBrokerConnector.connect()

            println("Using protocol kuksa.val.v2")
            println("Setting Vehicle.Speed in Databroker to 60")

            val signalId = SignalID.newBuilder().setPath("Vehicle.Speed").build()
            val speedValue = Types.Value.newBuilder().setFloat(60.0F).build()
            val datapoint = Types.Datapoint.newBuilder().setValue(speedValue).build()
            val publishValueRequest = PublishValueRequestV2(signalId, datapoint)

            dataBrokerConnection.publishValue(publishValueRequest)

            println("Reading Vehicle.Speed from Databroker")
            val fetchValueRequest = FetchValueRequestV2(signalId)
            val response = dataBrokerConnection.fetchValue(fetchValueRequest)
            println("FetchValueResponse: $response")

            println("Observe Vehicle.Speed")
            val signalPaths = listOf("Vehicle.Speed")
            val subscribeRequest = SubscribeRequestV2(signalPaths)
            val responseFlow = dataBrokerConnection.subscribe(subscribeRequest)
            responseFlow.collect { response ->
                val vehicleSpeedValue = response.entriesMap["Vehicle.Speed"]?.value?.float
                // handle changes
            }
        }
    }
```
## VSS Model Generation

Velocitas provides the vss-processor-plugin Gradle Plugin to generate Model files from a Vehicle Signal Specification.
Using the VSS Models allows a more convenient usage over using the VSS Paths. However as of now native support for
VSS Models only exist for kuksa.val.v1 protocol. Support for kuksa.val.v2 will follow later. For starters you can
retrieve an extensive default specification from the release page of the
[COVESA Vehicle Signal Specification GitHub repository](https://github.com/COVESA/vehicle_signal_specification/releases).

Currently VSS specification files in .yaml and .json format are supported by the vss-processor.

*app/build.gradle.kts*
```kotlin
plugins {
    id("org.eclipse.velocitas.vss-processor-plugin") version "<VERSION>"
}

// Optional - See plugin documentation. Files inside the "$rootDir/vss" folder are used automatically.
vssProcessor {
    searchPath = "$rootDir/vss"
}
```

Doing so will generate a complete tree of Kotlin models which can be used in combination with the SDK API. This way you can
work with safe types and the SDK takes care of all the model parsing for you. There is also a whole set of
convenience operators and extension methods to work with to manipulate the tree data. See the `VssNode` class documentation for this.
The generated code can be found in the folder: `<PROJECT>/build/generated/vss/kotlin`.

> [!IMPORTANT]
> Keep in mind to always synchronize a compatible (e.g. subset) VSS file between the client and the Databroker.



*Example .yaml VSS file*
```yaml
Vehicle.Speed:
  datatype: float
  description: Vehicle speed.
  type: sensor
  unit: km/h
  uuid: efe50798638d55fab18ab7d43cc490e9
```

*Example model*

```kotlin
data class VssSpeed @JvmOverloads constructor(
    override val `value`: Float = 0f,
) : VssNode<Float> {
    override val comment: String
        get() = ""

    override val description: String
        get() = "Vehicle speed."

    override val type: String
        get() = "sensor"

    override val uuid: String
        get() = "efe50798638d55fab18ab7d43cc490e9"

    override val vssPath: String
        get() = "Vehicle.Speed"

    override val children: Set<VssNode>
        get() = setOf()

    override val parentClass: KClass<*>
        get() = VssVehicle::class
}
```

*Using the Vehicle Model to interact with the Databroker*

> [!IMPORTANT]
> The Vehicle Model only supports the kuksa.val.v1 protocol

```kotlin
    val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55555)
        .usePlaintext()
        .build()
    val dataBrokerConnector = DataBrokerConnector(managedChannel)

    coroutineScope {
        launch {
            val dataBrokerConnection = dataBrokerConnector.connect()

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
                        // handle changes
                    }
                },
            )
        }
    }
```
