package me.kuma.flirdemo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler
import com.flir.thermalsdk.live.Identity
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import me.kuma.flirdemo.databinding.ActivityLiveBinding
import timber.log.Timber

/**
 * Description here.
 *
 * Created on: 2021/5/12 3:02 下午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */
class LiveActivity : AppCompatActivity() {

  private val binding: ActivityLiveBinding by inflate()
  private lateinit var presenter: LivePresenter

  private val disposables: CompositeDisposable = CompositeDisposable()
  private val usbPermissionHandler = UsbPermissionHandler()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_live)

    presenter = LivePresenter(this)
    observeData()

    binding.glLive.displayFPS(true)
    val previewWidth = QMUIDisplayHelper.getScreenWidth(this).toFloat()
    val previewHeight = (previewWidth / 0.75).toFloat()

    Timber.d("$previewWidth * $previewHeight")
    presenter.updatePreviewSize(previewWidth, previewHeight)
  }

  override fun onResume() {
    super.onResume()
    binding.glLive.onResume()

    val currentStatus = FlirOneManager.status().value
    Timber.d("Current Status: $currentStatus")
    when (currentStatus) {
      FlirOneManager.Status.Connected -> presenter.startStream()
      FlirOneManager.Status.Disconnect -> {/*startTipAnimation()*/}
      FlirOneManager.Status.Streaming -> {
//        binding.layoutConnectDeviceTip.postDelayed({
//          if (isVisible) stopTipAnimation()
//        }, 500)
      }
    }
  }

  override fun onPause() {
    super.onPause()
    binding.glLive.onPause()

    when (FlirOneManager.status().value) {
      FlirOneManager.Status.Streaming -> presenter.stopStream()
    }
  }

  override fun onDestroy() {
    presenter.destroy()

    disposables.clear()

    if (FlirOneManager.status().value == FlirOneManager.Status.Connected) {
      presenter.disconnectCamera()
    }

    super.onDestroy()
  }

  fun observeData() {
    disposables.add(
      presenter.frame()
        .observeOn(Schedulers.io())
        .subscribe({
          binding.glLive.offNewFrame(it)
        }, Throwable::printStackTrace)
    )

    disposables.add(
      presenter.temperature()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({
          binding.cl.setTemperature(it)
        }, Throwable::printStackTrace)
    )

    disposables.add(
      FlirOneManager.status()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({
          Timber.d("New Status: $it")
          when (it) {
            is FlirOneManager.Status.ConnectError -> {
              Toast.makeText(this, it.errorMessage, Toast.LENGTH_LONG).show()
              finish()
            }
            FlirOneManager.Status.Connected -> {
//              binding.layoutConnectingTip.visibility = View.GONE

//              binding.layoutContent.visibility = View.VISIBLE
//              binding.layoutBottomContent.visibility = View.VISIBLE
            }
            FlirOneManager.Status.Connecting -> {
//              if (binding.layoutConnectDeviceTip.visibility == View.VISIBLE) stopTipAnimation()

//              binding.layoutConnectingTip.visibility = View.VISIBLE
            }
            FlirOneManager.Status.Disconnect -> {
//              Timber.d("LiveFragment is currently visible to the user? $isVisible")
//              if (isVisible) {
                FlirOneManager.startDiscoveryDevice()
//                startTipAnimation()
//                binding.layoutContent.visibility = View.INVISIBLE
//                binding.layoutBottomContent.visibility = View.INVISIBLE
//              }
            }
            FlirOneManager.Status.Discovering -> {

            }
            FlirOneManager.Status.Streaming -> {

            }
          }
        }, Throwable::printStackTrace)
    )

    presenter.newCameraIdentity()
      .observe(this) {
        FlirOneManager.stopDiscoveryDevice()

//        binding.layoutConnectDeviceTip.post {
//          if (isVisible) stopTipAnimation()
//        }

        if (UsbPermissionHandler.isFlirOne(it)) {
          Timber.d("Found camera is type of 'FLIR One'")

          usbPermissionHandler.requestFlirOnePermisson(it, this, object : UsbPermissionHandler.UsbPermissionListener {
            override fun permissionGranted(identity: Identity) {
              Timber.d("USB permission Granted")
              presenter.connectCamera(identity) { camera ->
                if (camera != null) presenter.startStream(camera)
              }
            }

            override fun permissionDenied(identity: Identity) {
              Timber.e("USB permission was denied for identity: ${identity.deviceId}")
            }

            override fun error(errorType: UsbPermissionHandler.UsbPermissionListener.ErrorType?, identity: Identity?) {
              Timber.e("Error when asking for usb permission for FLIR ONE, error:$errorType identity:$identity")
            }
          })
        } else {
          presenter.connectCamera(it) { camera ->
            if (camera != null) presenter.startStream(camera)
          }
        }
      }

    presenter.captureRequestResult()
      .observe(this) {
//        it.setClass(this, ViewThermalImageActivity::class.java)
//        viewThermalImageLauncher.launch(it)
      }
  }
}