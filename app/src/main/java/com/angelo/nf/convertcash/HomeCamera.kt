package com.angelo.nf.convertcash

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import com.makeramen.roundedimageview.RoundedImageView
import kotlinx.android.synthetic.main.activity_home_camera.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class HomeCamera : AppCompatActivity() {
    //Variables de firebase
    private lateinit var mAuth: FirebaseAuth
    private  lateinit var db: FirebaseFirestore
    //Variables de TFLite
    private lateinit var localModel: LocalModel
    private lateinit var customImageLabelerOptions: CustomImageLabelerOptions

    //Variables de la ventana emergente al reconocer un billete
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var bottomSheetView: View

    //Variables de los elementos de la ventana emergente
    private lateinit var imageCapture: ImageCapture
    private lateinit var roundedImageView: RoundedImageView
    private lateinit var textviewTest: TextView
    private lateinit var vf_et_usd: EditText
    private lateinit var vf_et_eur: EditText
    private lateinit var vf_et_cny: EditText

    //Variables de objetos necesarios para el tipo de cambio
    private lateinit var jsonTipoCambio: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_camera)
        iniciarCamera()
        phc_btn_billete.setOnClickListener { view -> reconocimientoBilletes() }
        phc_btn_etiqueta.setOnClickListener { view -> leerEtiquetas() }
        //Carga el modelo personalizado de tflite
        localModel = LocalModel.Builder()
            .setAssetFilePath("model.tflite")
            .build()
        //Carga el modelo y configura las opcion de la IA
        customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
            //Minimo de porcentaje para hacer valida una etiqueta
            .setConfidenceThreshold(0.5f)
            //camtindad de etiquetas maxima del modelo
            .setMaxResultCount(4)
            .build()

        //Crea la view de la ventana emergente
        bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        bottomSheetView = LayoutInflater.from(applicationContext)
            .inflate(R.layout.layout_ventana_flotante, findViewById(R.id.ventana_flotante))
        bottomSheetDialog.setContentView(bottomSheetView)

        //Configura los botones de la ventana emergente
        bottomSheetView.findViewById<Button>(R.id.vf_btn_cerrar)
            .setOnClickListener { view -> bottomSheetDialog.dismiss() }
        //Variables de los items de la ventana emergente
        roundedImageView = bottomSheetView.findViewById(R.id.vf_iv_foto)
        textviewTest = bottomSheetView.findViewById(R.id.testing_text_view)
        vf_et_usd = bottomSheetView.findViewById(R.id.vf_et_usd)
        vf_et_eur = bottomSheetView.findViewById(R.id.vf_et_eur)
        vf_et_cny = bottomSheetView.findViewById(R.id.vf_et_cny)

        //Variables de firebase
        mAuth = FirebaseAuth.getInstance()
        mAuth.signInAnonymously();
        db = FirebaseFirestore.getInstance()

        getJsonFromUrl("https://adminhotelparkview.000webhostapp.com/APITipoCambio/")

    }

    private fun iniciarCamera() {
        //Crea una instancia de la camara
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
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

    private fun leerEtiquetas() {
        val context = this

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)

                //Variable de bitmap al tomar la foto
                val bitmap = phc_contenedor_camera.bitmap!!

                //Set bitmap a la view flotante
                roundedImageView.setImageBitmap(bitmap)

                //crea el tipo de imagen InputImage, requerido por la ia, desde un bitmap
                val image =
                    InputImage.fromBitmap(
                        bitmap,
                        imageProxy.imageInfo.rotationDegrees
                    )

                //Crea la ia de reconocimiento de texto
                val recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)


                //Procesa la imagen para obtener los textos
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // Task completed successfully
                        // VARIABLE DE TESTING
                        textviewTest.text = visionText.text
                        Toast.makeText(context, visionText.text, Toast.LENGTH_SHORT).show()
                        // ...
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()

                    }


                //Muestra la ventana emergente
                //billetes_tipo_cambio()
                bottomSheetDialog.show()
                //NECESARIO: Libera la camara para su proxima captura de imagen
                imageProxy.close()
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Toast.makeText(context, exception.toString(), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun reconocimientoBilletes() {
        val context = this

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)

                //obtiene el bitmap de la imagen
                val bitmapImage = phc_contenedor_camera.bitmap!!
                roundedImageView.setImageBitmap(bitmapImage)
                //Paso la imagen a firebase para su amacenamiento
                imagenAFirebase(bitmapImage)
                //crea el tipo de imagen InputImage, requerido por la ia, desde el bitmap obtenido anteriormente
                val image =
                    InputImage.fromBitmap(
                        bitmapImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                //Pasa a la ia los parametros del modelo y el modelo
                val labeler = ImageLabeling.getClient(customImageLabelerOptions)

                //VARIABLE DE TESTING
                textviewTest.text = ""

                //Pasa la imagen a la ia para que la analice
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        //VARIABLE DE TESTING
                        var temp = ""

                        for (label in labels) {
                            //Etiqueta obtenida de la imagen
                            val text = label.text

                            //Probabilidad de acierto
                            val confidence = label.confidence

                            //Porcentaje de acierto
                            val porcentaje = confidence * 100

                            //VARIABLE DE TESTING
                            temp += ("$porcentaje seguro que es un billete de $text\n")
                            when (label.text) {
                                "1mil" -> calcularTipoCambio(1000.0)
                                "5mil" -> calcularTipoCambio(5000.0)
                                "10mil" -> calcularTipoCambio(10000.0)
                                "20mil" -> calcularTipoCambio(20000.0)
                            }
                        }
                        //VARIABLE DE TESTING
                        textviewTest.text = temp

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

    fun getJsonFromUrl(url: String) {

        val cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap

        val network = BasicNetwork(HurlStack())

        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response: JSONObject ->
                jsonTipoCambio = response
            },
            { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show()
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    fun calcularTipoCambio(valor_en_colones: Double) {
        val total_dolares = valor_en_colones / jsonTipoCambio.getDouble("tipoCambio")
        //Toast.makeText(this, jsonTipoCambio.getDouble("tipoCambio").toString(), Toast.LENGTH_LONG).show()
        //Toast.makeText(this, valor_en_colones.toString(), Toast.LENGTH_LONG).show()
        vf_et_usd.setText(total_dolares.toString())
    }

    fun imagenAFirebase(imagen:Bitmap){

        var outputStream =  ByteArrayOutputStream();
        imagen.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        var base64IMG = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        db.collection("Imagenes").add(hashMapOf("Imagen" to base64IMG))

    }


}
