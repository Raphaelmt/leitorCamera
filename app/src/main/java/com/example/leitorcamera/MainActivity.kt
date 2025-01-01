package com.example.leitorcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var textToSpeech: TextToSpeech? = null
    private var lastPhotoPath: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)

        // Inicializar TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Solicitar permissão de câmera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(arrayOf(Manifest.permission.CAMERA))
        }

        // Detectar toque na tela para capturar a imagem
        previewView.setOnClickListener {
            takePhoto()
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
            }
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Erro ao iniciar a câmera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            cacheDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Erro ao salvar a foto: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lastPhotoPath = photoFile.absolutePath
                    Toast.makeText(applicationContext, "Foto salva: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                    // Iniciar o reconhecimento de texto
                    extractTextFromImage(lastPhotoPath!!)
                }
            }
        )
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Implementação do TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TextToSpeech", "Idioma não suportado")
            } else {
                // Falar a mensagem ao iniciar o aplicativo
                speak("Toque na tela para tirar uma foto.")
            }
        } else {
            Log.e("TextToSpeech", "Falha ao inicializar o TextToSpeech")
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Apagar todas as fotos do diretório ao fechar o aplicativo
        val files = cacheDir.listFiles()
        files?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jpg")) {
                file.delete()
            }
        }

        // Liberar recursos do TextToSpeech
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    private fun extractTextFromImage(imagePath: String) {
        // Carregar a imagem a partir do caminho
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val image = InputImage.fromBitmap(bitmap, 0)

        // Inicializar o reconhecedor de texto
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Processar a imagem
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                processExtractedText(visionText)
            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "Erro ao reconhecer texto: ${e.message}", e)
            }
    }

    private fun processExtractedText(visionText: Text) {
        val extractedText = visionText.text
        Log.d("MLKit", "Texto extraído: $extractedText")

        // Exibir o texto extraído
        //Toast.makeText(this, "Texto encontrado: $extractedText", Toast.LENGTH_LONG).show()
        speak(extractedText)
    }


}
