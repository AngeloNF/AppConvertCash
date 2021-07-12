package com.angelo.nf.convertcash

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import com.makeramen.roundedimageview.RoundedImageView
import kotlinx.android.synthetic.main.activity_home_camera.*
import kotlinx.android.synthetic.main.layout_ventana_flotante.*
import org.json.JSONObject


class HomeCamera : AppCompatActivity() {

    //Variables de TFLite
    private lateinit var localModel: LocalModel
    private lateinit var customImageLabelerOptions: CustomImageLabelerOptions

    //Variables de la ventana emergente al reconocer un billete
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var  bottomSheetView: View

    //Variables de los elementos de la ventana emergente
    private lateinit var imageCapture: ImageCapture
    private lateinit var roundedImageView: RoundedImageView
    private lateinit var textView_test: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_camera)
        iniciarCamera()
        phc_btn_billete.setOnClickListener { view -> reconocimiento_billetes() }
        phc_btn_etiqueta.setOnClickListener { view -> leer_etiquetas() }
        //Carga el modelo personalizado de tflite
        localModel = LocalModel.Builder()
            .setAssetFilePath("model.tflite")
            .build()
        //Carga el modelo y configura las opcion de la IA
        customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
                //Minimo de porcentaje para hacer valida una etiqueta
            .setConfidenceThreshold(0.0f)
                //camtindad de etiquetas maxima del modelo
            .setMaxResultCount(3)
            .build()

        //Crea la view de la ventana emergente
        bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        bottomSheetView = LayoutInflater.from(applicationContext).inflate(R.layout.layout_ventana_flotante, findViewById(R.id.ventana_flotante))

        //Configura los botones de la ventana emergente
        bottomSheetView.findViewById<Button>(R.id.vf_btn_cerrar).setOnClickListener { view -> bottomSheetDialog.dismiss() }
        bottomSheetView.findViewById<Button>(R.id.vf_btn_cerrar).setOnClickListener { view -> bottomSheetDialog.dismiss() }
        bottomSheetDialog.setContentView(bottomSheetView)
        //Variables de los items de la ventana emergente
        roundedImageView = bottomSheetView.findViewById(R.id.vf_iv_foto)
        textView_test = bottomSheetView.findViewById(R.id.testing_text_view)
    }

    private fun iniciarCamera() {
        //Crea una instancia de la camara
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            //Carga la preview de la camara
            val preview = Preview.Builder()
                .setTargetRotation(phc_contenedor_camera.display.rotation)
                .build()
                .also { it.setSurfaceProvider(phc_contenedor_camera.surfaceProvider) }

            //Carga la camara trasera por defecto
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                //Intenta liberar las camaras
                cameraProvider.unbindAll()
                //Crea el capturador de imagenes
                imageCapture = ImageCapture.Builder().build()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview)
            } catch (e: Exception) {
                Log.e("ErrorIniciarCamera", e.message.toString(), e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    fun leer_etiquetas() {
        val context = this

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)

                //crea el tipo de imagen InputImage, requerido por la ia, desde un bitmap
                val image =
                    InputImage.fromBitmap(
                        phc_contenedor_camera.bitmap!!,
                        imageProxy.imageInfo.rotationDegrees
                    )

                //Crea la ia de reconocimiento de texto
                val recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                //Procesa la imagen para obtener los textos
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // Task completed successfully

                        Toast.makeText(context, visionText.text, Toast.LENGTH_SHORT).show()
                        // ...
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()

                    }
                //NECESARIO: Libera la camara para su proxima captura de imagen
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

                //obtiene el bitmap de la imagen
                val bitmapImage = phc_contenedor_camera.bitmap!!
                roundedImageView.setImageBitmap(bitmapImage)

                //crea el tipo de imagen InputImage, requerido por la ia, desde el bitmap obtenido anteriormente
                val image =
                    InputImage.fromBitmap(
                        bitmapImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                //Pasa a la ia los parametros del modelo y el modelo
                val labeler = ImageLabeling.getClient(customImageLabelerOptions)

                //VARIABLE DE TESTING
                textView_test.text =""

                //Pasa la imagen a la ia para que la analice
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        //VARIABLE DE TESTING
                        var temp :String = ""

                        for (label in labels) {
                            //Etiqueta obtenida de la imagen
                            val text = label.text

                            //Probabilidad de acierto
                            val confidence = label.confidence

                            //index de la etiqueta
                            val index = label.index

                            //Porcentaje de acierto
                            val porcentaje = confidence * 100

                            //VARIABLE DE TESTING
                            temp += ("$porcentaje seguro que es un billete de $text\n")

                        }
                        //VARIABLE DE TESTING
                        textView_test.text = temp

                        //Muestra la ventana emergente
                        //billetes_tipo_cambio()
                        bottomSheetDialog.show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
                    }
                //NECESARIO: Libera la camara para su proxima captura de imagen
                imageProxy.close()
            }


            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Toast.makeText(context, exception.toString(), Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun billetes_tipo_cambio(){
        val requestQueue = Volley.newRequestQueue(this)
        val jsArrayRequest = JsonObjectRequest(
            Request.Method.GET,
            "https://tipodecambio.paginasweb.cr/api",
            "",
            { response ->
                Toast.makeText(this,response.toString(),Toast.LENGTH_LONG).show()
                response.let { jsonObject -> textView_test.text = jsonObject.toString(1) }
            },
            { error ->
                Log.d(
                    "REQUESTERROR",
                    "Error Respuesta en JSON: " + error.message
                )
            }
        )
        requestQueue.add(jsArrayRequest)
    }

}