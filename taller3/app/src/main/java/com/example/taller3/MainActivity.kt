package com.example.taller3

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberImagePainter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class MainActivity : ComponentActivity() {
    private val cameraAppVm:CameraAppViewModel by viewModels()
    private lateinit var cameraController:LifecycleCameraController
    private val lanzadorPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[Manifest.permission.ACCESS_FINE_LOCATION] ?:
                false) or (it[Manifest.permission.ACCESS_COARSE_LOCATION] ?:
                false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                    cameraAppVm.onPermisoUbicacionOk()
                }
                (it[Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                    cameraAppVm.onPermisoCamaraOk()
                }
                else -> {
                }
            }
        }
    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}


fun generarNombreSegunFechaHastaSegundo(): String {
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
    return dateFormat.format(calendar.time)
}

fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),"${generarNombreSegunFechaHastaSegundo()}.jpg")
fun uri2imageBitmap(uri: Uri, contexto: Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()
fun tomarFotografia(
    cameraController: CameraController,
    archivo: File,
    contexto: Context,
    formRecepcionVm: FormRecepcionViewModel,
    imagenGuardadaOk: (uri: Uri) -> Unit
) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(outputFileOptions,
        ContextCompat.getMainExecutor(contexto), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also {
                    Log.v("tomarFotografia()::onImageSaved", "Foto guardada en $it")
                    formRecepcionVm.agregarFoto(it) // Agregar la foto a la lista
                    imagenGuardadaOk(it)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("tomarFotografia()", "Error: ${exception.message}")
            }
        })
}

class SinPermisoException(mensaje:String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk:(location:Location) -> Unit) {
    try {
        val servicio =
            LocationServices.getFusedLocationProviderClient(contexto)
        val tarea =
            servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"No tiene permisos para conseguir la ubicación")
    }
}




@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current
    val formRecepcionVm:FormRecepcionViewModel = viewModel()
    val cameraAppViewModel:CameraAppViewModel = viewModel()
    when(cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formRecepcionVm,
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formRecepcionVm.latitud.value = it.latitude
                            formRecepcionVm.longitud.value = it.longitude
                        }
                    }
cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        Pantalla.FOTO -> {
            PantallaFotoUI(formRecepcionVm, cameraAppViewModel, cameraController)
        }
    }
}

@Composable
fun PantallaFormUI(
    formRecepcionVm: FormRecepcionViewModel,
    tomarFotoOnClick: () -> Unit = {},
    actualizarUbicacionOnClick: () -> Unit = {}
) {
    val openFullscreen = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            label = { Text("Nombre del lugar visitado en tus vacaciones") },
            value = formRecepcionVm.receptor.value,
            onValueChange = { formRecepcionVm.receptor.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )
        Text("Fotografía de mis Vacaciones:")
        Button(onClick = {
            tomarFotoOnClick()
        }) {
            Text("Capturar Fotografía")
        }
        formRecepcionVm.fotosRecepcion.value?.forEach { fotoUri ->
            Box(
                modifier = Modifier.clickable {
                    openFullscreen.value = true
                }
            ) {
                Image(
                    painter = rememberImagePainter(
                        data = fotoUri
                    ),
                    contentDescription = "Imagen Vacaciones ${formRecepcionVm.receptor.value}",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = formRecepcionVm.receptor.value,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.White)
                        .padding(4.dp),
                    color = Color.Black
                )
            }
            if (openFullscreen.value) {
                FotoFullscreenUI(fotoUri) {
                    openFullscreen.value = false
                }
            }

            Text(
                "Ubicación: Latitud ${formRecepcionVm.latitud.value}, Longitud ${formRecepcionVm.longitud.value}"
            )
        }
        Button(onClick = {
            actualizarUbicacionOnClick()
        }) {
            Text("Actualizar Ubicación")
        }
        Spacer(Modifier.height(100.dp))
        MapaOsmUI(formRecepcionVm.latitud.value, formRecepcionVm.longitud.value)
    }
}

@Composable
fun PantallaFotoUI(
    formRecepcionVm: FormRecepcionViewModel,
    appViewModel: CameraAppViewModel,
    cameraController: CameraController
) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    Button(onClick = {
        tomarFotografia(
            cameraController,
            crearArchivoImagenPrivado(contexto),
            contexto,
            formRecepcionVm
        ) {
            appViewModel.cambiarPantallaForm()
        }
    }) {
        Text("Tomar foto")
    }
}

@Composable
fun FotoFullscreenUI(
    fotoUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = rememberImagePainter(
                data = fotoUri
            ),
            contentDescription = "Imagen en pantalla completa",
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    // Implementa lógica para cerrar la foto (volver atrás)
                    onBack()
                }
        )
    }
}

@Composable
fun MapaOsmUI(latitud:Double, longitud:Double) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            MapView(it).also {it.setTileSource(TileSourceFactory.MAPNIK)
                org.osmdroid.config.Configuration.getInstance().userAgentValue = contexto.packageName
            }
        }, update = {
            it.overlays.removeIf { true }
            it.invalidate()
            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)
            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}
