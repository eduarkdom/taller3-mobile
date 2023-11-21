package com.example.taller3

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class FormRecepcionViewModel : ViewModel() {
    val receptor = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val fotosRecepcion = mutableStateOf<List<Uri>?>(null)

    fun agregarFoto(fotoUri: Uri) {
        val fotos = fotosRecepcion.value?.toMutableList() ?: mutableListOf()
        fotos.add(fotoUri)
        fotosRecepcion.value = fotos
    }
}

