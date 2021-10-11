package me.kuma.flirdemo

import com.flir.thermalsdk.ErrorCode
import com.flir.thermalsdk.live.Camera
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.ConnectParameters
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.live.discovery.DiscoveryFactory
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener
import io.reactivex.disposables.Disposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import org.jetbrains.annotations.Nullable
import timber.log.Timber
import java.io.IOException

/**
 * Description here.
 *
 * Created on: 2021/6/11 1:30 下午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */
object FlirOneManager : DiscoveryEventListener, ConnectionStatusListener {

  sealed class Status {

    object Discovering : Status()
    object Connecting : Status()
    object Connected : Status()
    object Disconnect : Status()
    object Streaming : Status()

    class ConnectError(val errorMessage: String) : Status()
  }

  private var lastIdentity: Identity? = null
  private var connectedCamera: Camera? = null

  private val listeners: MutableList<DiscoveryEventListener> = mutableListOf()

  private var asyncDisposable: Disposable
  private val status = BehaviorProcessor.create<Status>()
  private val asyncProcessor = PublishProcessor.create<AsyncBlock>()

  init {
    asyncDisposable = asyncProcessor.subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
      .subscribe({
        it.invoke()
      }, {
        Timber.e(it)
      })
  }

  fun destroy() {
    if (!asyncDisposable.isDisposed) asyncDisposable.dispose()
  }

  fun startDiscoveryDevice() {
    async {
      val discoveryFactory = DiscoveryFactory.getInstance()
      val isDiscovering = discoveryFactory.isDiscovering(CommunicationInterface.USB)

      if (!isDiscovering) {
        Timber.d("Start discovery device")
        discoveryFactory.scan(this, CommunicationInterface.USB)
        status.onNext(Status.Discovering)
      } else {
        Timber.d("Scanning device")
      }
    }
  }

  fun stopDiscoveryDevice() {
    async {
      DiscoveryFactory.getInstance().stop()
      Timber.d("Stop scan device")
    }
  }

  fun addDiscoveryEventListener(listener: DiscoveryEventListener) {
    async {
      listeners.add(listener)
      if (lastIdentity != null) {
        if (listeners.size > 1) listeners.last().onCameraFound(lastIdentity)
      }
    }
  }

  fun removeDiscoveryEventListener(listener: DiscoveryEventListener) {
    async {
      listeners.remove(listener)
    }
  }

  fun connectCamera(
    identity: Identity,
    connectParameters: ConnectParameters,
    block: (camera: Camera?) -> Unit
  ) {
    status.onNext(Status.Connecting)
    async {
      try {
        val camera = Camera()
        camera.connect(identity, this, connectParameters)
        connectedCamera = camera

        status.onNext(Status.Connected)

        val temperatureRangeControl = camera.remoteControl?.temperatureRange
        if (temperatureRangeControl != null) {
          //Just FLIR One Pro Mobile SDK
          val normalTemperatureRangeIndex = 0 /* -20.0C - 120.0C */
          val highTemperatureRangeIndex = 1 /* 0.0C - 400.0C */
          temperatureRangeControl.selectedIndex().set(highTemperatureRangeIndex, {
            Timber.d("Select range 'high: 0.0C - 400.0C'")

            temperatureRangeControl.selectedIndex().get({
              Timber.d("Current range index $it")
            }, {
              Timber.e("Get temperature range failed: $it")
            })
          }, {
            Timber.e("Set temperature range failed: $it")
          })
        }

        block(connectedCamera!!)
      } catch (e: IOException) {
        Timber.e(e)

        connectedCamera = null
        lastIdentity = null

        status.onNext(Status.ConnectError("连接失败，请断开设备并重新连接"))

        block(null)
      }
    }
  }

  fun disconnectCamera() {
    async {
      if (connectedCamera != null) {
        connectedCamera!!.disconnect()
        connectedCamera!!.close()
        connectedCamera = null

        lastIdentity = null

        status.onNext(Status.Disconnect)
      }
    }
  }

  fun startStream(camera: Camera, listener: ThermalImageStreamListener) {
    async {
      camera.subscribeStream(listener)
      status.onNext(Status.Streaming)
    }
  }

  fun stopStream(camera: Camera, listener: ThermalImageStreamListener) {
    async {
      camera.unsubscribeStream(listener)
      if (Status.Streaming == status.value) status.onNext(Status.Connected)
    }
  }

  fun status(): BehaviorProcessor<Status> = status

  fun getCurrentTemperatureRange() {
    if (Status.Connected == status.value || Status.Streaming == status.value) {
      val temperatureRangeControl = connectedCamera?.remoteControl?.temperatureRange
      temperatureRangeControl?.selectedIndex()?.get({ rangeIndex ->
        val rangeStr: String = when (rangeIndex) {
          0 -> "-20.0C - 120.0C"
          1 -> "0.0C - 400.0C"
          else -> "Unknown"
        }
        Timber.d("Current temperature range index $rangeIndex ($rangeStr)")
      }, {
        Timber.e("Get temperature range failed: $it")
      })
    }
  }

  override fun onCameraFound(identity: Identity) {
    async {
      Timber.d("onCameraFound. Type: ${identity.cameraType.name}. Id: ${identity.communicationInterface}. Communication: ${identity.communicationInterface.name}")
      lastIdentity = identity
      listeners.last().onCameraFound(identity)
    }
  }

  override fun onCameraLost(identity: Identity) {
    async {
      super.onCameraLost(identity)
      Timber.d("onCameraLost().")
      Timber.d("Type: ${identity.cameraType.name}. Id: ${identity.communicationInterface}. Communication: ${identity.communicationInterface.name}")
      for (listener in listeners) listener.onCameraLost(identity)
    }
  }

  override fun onDisconnected(errorCode: @Nullable ErrorCode?) {
    async {
      Timber.d("onDisconnected(${errorCode ?: ""})")

      if (connectedCamera != null && connectedCamera!!.isConnected) connectedCamera!!.disconnect()

      connectedCamera?.close()
      connectedCamera = null

      lastIdentity = null

      if (errorCode != null) {
        when (errorCode.code) {
          5 -> {
            status.onNext(Status.ConnectError("USB暂不可用，请断开设备并重新连接"))
          }
        }
      } else {
        status.onNext(Status.Disconnect)
      }
    }
  }

  override fun onDiscoveryError(
    communicationInterface: CommunicationInterface,
    errorCode: ErrorCode
  ) {
    async {
      Timber.d("onDiscoveryError($communicationInterface, $errorCode)")
      for (listener in listeners) listener.onDiscoveryError(communicationInterface, errorCode)
    }
  }

//  fun getAllImageMode(): List<ImageMode> = imageModeList

//  fun getAllImageModeNameArray(): Array<String> {
//    val allImageMode = getAllImageMode()
//    return Array(allImageMode.size) {
//      return@Array allImageMode[it].name
//    }
//  }

  private fun async(block: () -> Unit) {
    asyncProcessor.onNext(block)
  }
}