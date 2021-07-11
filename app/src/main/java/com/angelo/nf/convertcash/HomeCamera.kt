package com.angelo.nf.convertcash

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.android.synthetic.main.activity_home_camera.*
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat

class HomeCamera : AppCompatActivity() {
    private lateinit var localModel: LocalModel
    private lateinit var imageCapture: ImageCapture
    private lateinit var customImageLabelerOptions: CustomImageLabelerOptions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_camera)
        iniciarCamera()
        phc_btn_billete.setOnClickListener { view -> reconocimiento_billetes() }
        phc_btn_etiqueta.setOnClickListener { view -> reconocer_etiquetas() }
        localModel = LocalModel.Builder()
            .setAssetFilePath("model.tflite")
            .build()
        customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.0f)
            .setMaxResultCount(3)
            .build()
    }

    private fun iniciarCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetRotation(phc_contenedor_camera.display.rotation).build()

                .also { it.setSurfaceProvider(phc_contenedor_camera.surfaceProvider) }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()

                imageCapture = ImageCapture.Builder().build()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview)
            } catch (e: Exception) {
                Log.e("IniciarCamera", e.message.toString(), e)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    fun reconocer_etiquetas() {
        val context = this
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)
                    val image =
                        InputImage.fromBitmap(phc_contenedor_camera.bitmap!!, imageProxy.imageInfo.rotationDegrees)
                    val recognizer =
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val result = recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            // Task completed successfully

                            Toast.makeText(context, visionText.text, Toast.LENGTH_SHORT).show()
                            // ...
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()

                        }
                imageProxy.close()
                }


            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Toast.makeText(context, exception.toString(), Toast.LENGTH_SHORT).show()
            }
        })
    }
    fun reconocimiento_billetes() {
        val context = this
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)
                val image =
                    InputImage.fromBitmap(phc_contenedor_camera.bitmap!!, imageProxy.imageInfo.rotationDegrees)

                val labeler = ImageLabeling.getClient(customImageLabelerOptions)

                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        for (label in labels) {
                            val text = label.text
                            val confidence = label.confidence
                            val index = label.index
                            Toast.makeText(context, "$text $confidence", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                    }

                imageProxy.close()
            }


            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Toast.makeText(context, exception.toString(), Toast.LENGTH_SHORT).show()
            }
        })
    }
}