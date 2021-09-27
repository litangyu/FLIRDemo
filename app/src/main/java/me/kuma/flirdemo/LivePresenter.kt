package me.kuma.flirdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Build
import android.util.SparseArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.flir.thermalsdk.ErrorCode
import com.flir.thermalsdk.image.palettes.PaletteManager
import com.flir.thermalsdk.live.Camera
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.ConnectParameters
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Description here.
 *
 * Created on: 2021/9/27 1:11 下午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */
class LivePresenter(view: LiveActivity) : DiscoveryEventListener, ThermalImageStreamListener {

  private var isMeasureMode: Boolean = false
  private var inspectionPointId: String? = null
  private var inspectionTypeId: String? = null

  private var connectedCamera: Camera? = null

  private val frame = PublishProcessor.create<Bitmap>()
  private val temperature = PublishProcessor.create<String>()
  private val newCameraIdentity = MutableLiveData<Identity>()
  private val captureRequestResult = MutableLiveData<Intent>()

  @Volatile
  private var imageCaptureRequested = false
  private var imageCaptureSavePath: String? = null
  private var focusOffset: PointF? = null

  @Volatile
  private var paletteIndex: Int = 0

  @Volatile
  private var imageModeIndex: Int = 0

  @Volatile
  private var emissivityIndex: Int = 0

  @Volatile
  private var focusPointX: Int = 0

  @Volatile
  private var focusPointY: Int = 0

  private var previewWidth: Float = 0f
  private var previewHeight: Float = 0f

  private val bufferSparseArray: HashMap<Int,ByteBuffer> = HashMap()

  private val disposables: CompositeDisposable = CompositeDisposable()

  init {
    Timber.d("LiveViewModel.ViewModel init()")
    FlirOneManager.addDiscoveryEventListener(this)
    FlirOneManager.startDiscoveryDevice()
  }

  fun destroy() {
    disposables.clear()
    FlirOneManager.removeDiscoveryEventListener(this)
    bufferSparseArray.clear()
  }

  fun setData(isMeasureMode: Boolean, inspectionPointId: String?, inspectionTypeId: String?) {
    this.isMeasureMode = isMeasureMode
    this.inspectionPointId = inspectionPointId
    this.inspectionTypeId = inspectionTypeId
  }

  fun updatePreviewSize(width: Float, height: Float) {
    this.previewWidth = width
    this.previewHeight = height
  }

  fun connectCamera(identity: Identity, block: (camera: Camera?) -> Unit) {
    Timber.d("connectCamera(${identity})")
    FlirOneManager.connectCamera(identity, ConnectParameters(/*Time out 30 seconds */30000L)) {
      if (it != null) connectedCamera = it
      block(it)
    }
  }

  fun disconnectCamera() {
    Timber.d("disconnectCamera()")
    FlirOneManager.disconnectCamera()
  }

  fun startStream(camera: Camera? = null) {
    if (camera == null) {
      if (connectedCamera == null) {
        Timber.d("Have not available connected camera.")
      } else {
        Timber.d("startStream()")
        FlirOneManager.startStream(connectedCamera!!, this)
      }
    } else {
      Timber.d("startStream()")
      FlirOneManager.startStream(camera, this)
    }
  }

  fun stopStream() {
    if (connectedCamera != null) {
      Timber.d("stopStream()")
      FlirOneManager.stopStream(connectedCamera!!, this)
    } else {
      Timber.d("Have not available connected camera.")
    }
  }

  fun onCaptureImageClicked(focusOffset: PointF, savePath: String) {
    if (this.connectedCamera != null) {
      this.focusOffset = focusOffset
      this.imageCaptureRequested = true
      this.imageCaptureSavePath = savePath
    }
  }

  fun setPaletteIndex(index: Int) {
    this.paletteIndex = index
  }

  fun setImageModeIndex(index: Int) {
    this.imageModeIndex = index
  }

  fun setEmissivityIndex(index: Int) {
    this.emissivityIndex = index
  }

  fun onFocusPointCoordinateChange(x: Int, y: Int) {
    focusPointX = x
    focusPointY = y
  }

  fun frame(): Flowable<Bitmap> {
    return this.frame.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
  }

  fun temperature(): Flowable<String> {
    return this.temperature.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
  }

  fun newCameraIdentity(): LiveData<Identity> = newCameraIdentity

  fun captureRequestResult(): LiveData<Intent> = captureRequestResult

  override fun onCameraFound(identity: Identity) {
    Timber.d("onCameraFound($identity)")
    disposables.addAll(
      Observable.empty<Any>()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({
          //Not Implement
        }, Throwable::printStackTrace, {
          newCameraIdentity.value = identity
        })
    )
  }

  override fun onDiscoveryError(
    communicationInterface: CommunicationInterface,
    errorCode: ErrorCode
  ) {
    Timber.d("onDiscoveryError($communicationInterface, $errorCode)")
  }

  override fun onCameraLost(identity: Identity?) {
    Timber.d("onCameraLost(${identity ?: ""})")
  }

  override fun onImageReceived() {
    connectedCamera?.withImage {
      Timber.d("Frame available")

      if (this.imageCaptureRequested && this.imageCaptureSavePath != null) {
        Timber.d("Save thermal image, path: ${this.imageCaptureSavePath}")

        val file = File(this.imageCaptureSavePath!!)
        if (!file.exists()) file.createNewFile()

        it.saveAs(this.imageCaptureSavePath!!)

        val intent = Intent().apply {
          putExtra("capture_save_path", imageCaptureSavePath)
          putExtra("focus_offset", focusOffset)
          putExtra("inspection_point_id", inspectionPointId)
          putExtra("inspection_type_id", inspectionTypeId)
        }

        disposables.add(
          Observable.empty<Any>()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
              //Not Implement
            }, Throwable::printStackTrace, {
              captureRequestResult.value = intent
            })
        )

        this.imageCaptureRequested = false
        this.imageCaptureSavePath = null
        this.focusOffset = null

        return@withImage
      }

//      it.fusion?.setFusionMode(FlirOneManager.getAllImageMode()[imageModeIndex].value)
//        it.fusion?.setFusionMode(FusionMode.MSX)
      it.palette = PaletteManager.getDefaultPalettes()[paletteIndex]
//      it.imageParameters.emissivity =
//        EmissivityManager.getAllMaterialEmissivity()[emissivityIndex].value

      val javaImageBuffer = it.image
      val imageBufferWidth = javaImageBuffer.width
      val imageBufferHeight = javaImageBuffer.height
      val thermalImageWidth = it.width
      val thermalImageHeight = it.height

      val x = thermalImageWidth.toFloat() * (focusPointX.toFloat() / previewWidth)
      val y = thermalImageHeight.toFloat() * (focusPointY.toFloat() / previewHeight)
      if ((x >= 0 && x <= thermalImageWidth) && (y >= 0 && y <= thermalImageHeight)) {
        val focusPoint = com.flir.thermalsdk.image.Point(x.toInt(), y.toInt())
        val thermalValue = it.getValueAt(focusPoint).asCelsius()
        Timber.d("value: ${thermalValue.value} unit: ${thermalValue.unit} state: ${thermalValue.state}")
        temperature.onNext(thermalValue.toString())
      }

      Timber.d("JavaImageBuffer: $imageBufferWidth * $imageBufferHeight    ThermalImage: ${thermalImageWidth.toFloat()} * ${thermalImageHeight.toFloat()}    FocusPoint: ${focusPointX.toFloat()}, ${focusPointY.toFloat()}    Preview: $previewWidth, $previewHeight    Dest: $x, $y")

      val bitmapConfig = Bitmap.Config.ARGB_8888
      val fromBufferBitmap = if (Build.VERSION.SDK_INT >= 26) {
        Bitmap.createBitmap(imageBufferWidth, imageBufferHeight, bitmapConfig, true)
      } else {
        Bitmap.createBitmap(imageBufferWidth, imageBufferHeight, bitmapConfig)
      }

      val bufferSize = javaImageBuffer.pixelBuffer.size
      val cacheBuffer: ByteBuffer

      if (bufferSparseArray.contains(bufferSize)) {
        cacheBuffer = bufferSparseArray[bufferSize]!!
        cacheBuffer.clear()
      } else {
        val tempBuffer = ByteBuffer.allocate(bufferSize)
        bufferSparseArray[bufferSize] = tempBuffer
        cacheBuffer = tempBuffer
      }
      cacheBuffer.put(javaImageBuffer.pixelBuffer, 0, bufferSize)
      cacheBuffer.flip()

      fromBufferBitmap.copyPixelsFromBuffer(cacheBuffer)

      val matrix = Matrix()
      matrix.postScale(previewWidth / imageBufferWidth, previewHeight / imageBufferHeight)
      val destBitmap = Bitmap.createBitmap(
        fromBufferBitmap,
        0,
        0,
        imageBufferWidth,
        imageBufferHeight,
        matrix,
        false
      )

      frame.onNext(destBitmap)

      fromBufferBitmap.recycle()
    }
  }

}