package com.mark.android

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

/** Opens for about two seconds, takes one photo, hands it to the waiting tool, closes. */
class CameraActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val front = intent.getBooleanExtra("front", false)
        val exec = ContextCompat.getMainExecutor(this)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, capture)
                Handler(Looper.getMainLooper()).postDelayed({           // let exposure settle
                    capture.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val out = try {
                                val raw = image.toBitmap()
                                val rot = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                                var bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, rot, true)
                                val k = 1280f / maxOf(bmp.width, bmp.height)
                                if (k < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
                                val bo = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 85, bo)
                                Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
                            } catch (e: Exception) { null }
                            image.close(); Bus.photo?.complete(out); finish()
                        }
                        override fun onError(e: ImageCaptureException) { Bus.photo?.complete(null); finish() }
                    })
                }, 1200)
            } catch (e: Exception) { Bus.photo?.complete(null); finish() }
        }, exec)
    }
}
