package com.markvtls.cameraApp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.markvtls.cameraApp.databinding.FragmentCameraBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors



typealias FaceListener = (face: Face) -> Unit

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding
    private var graphicOverlay: GraphicOverlay? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null

    private val orientationEventListener by lazy {
        object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation < 0) {
                    return
                }

                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (imageAnalyzer != null && imageCapture != null) {
                    imageAnalyzer!!.targetRotation = rotation
                    imageCapture?.targetRotation = rotation
                }

            }
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCameraBinding.inflate(inflater,container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        graphicOverlay = binding?.graphics
        requestPermissions(REQUEST_CODE_PERMISSIONS)
        binding?.bottomAppBar?.setOnMenuItemClickListener {
            when(it.itemId) {
                R.id.shot -> {
                    takePhoto()
                    true
                }
                else -> false
            }

        }


    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        _binding = null
    }
    private fun startCamera() {

        val cameraProvider = ProcessCameraProvider.getInstance(requireContext())
        orientationEventListener.enable()
        cameraProvider.addListener(
            {
                val camera = cameraProvider.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding?.preview?.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer(graphicOverlay!!) { face ->
                            binding?.graphics?.add(FaceOverlay(binding!!.graphics, face))
                            graphicOverlay?.clear()
                            graphicOverlay?.add(FaceOverlay(graphicOverlay!!,face))
                        })
                    }
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    camera.unbindAll()
                    camera.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer
                    )
                } catch (e: Exception) {
                    Log.e(TAG,"Camera Use Case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.ROOT)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FaceApp")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireActivity().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG,"Taking photo failed", exception)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Snackbar.make(requireView(),"Фото сохранено",Snackbar.LENGTH_SHORT).show()
                }

            }
        )
    }
    private fun permissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions(requestCode: Int) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (permissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
                Snackbar.make(requireView(),"Отсутствуют необходимые разрешения", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "FaceApp"
        private const val FILENAME_FORMAT = "dd-mm-yy--hh-mm-ss"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).apply { if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)}
                .toTypedArray()
    }

    private class FaceAnalyzer( private val graphicOverlay: GraphicOverlay, private val listener: FaceListener): ImageAnalysis.Analyzer {
        val realTimeOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(realTimeOptions)

        override fun analyze(imageProxy: ImageProxy) {




            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            when (rotationDegrees) {
                270 -> graphicOverlay.setImageSourceInfo(imageProxy.height, imageProxy.width, true)
                90 -> graphicOverlay.setImageSourceInfo(imageProxy.height, imageProxy.width, true)
                180 -> graphicOverlay.setImageSourceInfo(imageProxy.width, imageProxy.height, true)
                0 -> graphicOverlay.setImageSourceInfo(imageProxy.width, imageProxy.height, true)

            }

            @androidx.camera.core.ExperimentalGetImage
            val image = imageProxy.image
            if (image != null) {
                println(rotationDegrees)
                val frame = InputImage.fromMediaImage(image,rotationDegrees)
                detector.process(frame)
                    .addOnSuccessListener { faces ->
                        faces.forEach {
                            listener(it)
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }

            }
    }


    }
