package me.kuma.flirdemo

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.flir.thermalsdk.ErrorCode
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.log.ThermalLog
import timber.log.Timber

/**
 * Description here.
 *
 * Created on: 2021/9/27 11:40 上午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */
class App : Application() , DiscoveryEventListener {

  private val handler: Handler = Handler(Looper.getMainLooper())

  override fun onCreate() {
    super.onCreate()
    Timber.plant(Timber.DebugTree())

    //init thermal sdk
    val enableLoggingInDebug = if (BuildConfig.DEBUG) ThermalLog.LogLevel.DEBUG else ThermalLog.LogLevel.NONE
    ThermalSdkAndroid.init(applicationContext, enableLoggingInDebug)
    Timber.d("Thermal SDK Version: ${ThermalSdkAndroid.getVersion()}. Hash: ${ThermalSdkAndroid.getCommitHash()}")
    FlirOneManager.startDiscoveryDevice()
    FlirOneManager.addDiscoveryEventListener(this)
  }

  override fun onCameraFound(identity: Identity) {
    handler.post {
      Toast.makeText(applicationContext, "红外测温设备已接入", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCameraLost(identity: Identity?) {
    handler.post {
      Toast.makeText(applicationContext, "红外测温设备已断开", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onDiscoveryError(communicationInterface: CommunicationInterface, errorCode: ErrorCode) {
    //Not yet implemented
  }
}

typealias AsyncBlock = () -> Unit