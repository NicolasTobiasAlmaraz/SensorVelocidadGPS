/*
TP Android- Medidas I

App que configura al GPS para que continuamente envie datos cada X tiempo
Con estos calcula la velocidad a la que se mueve el celular.
Para esto configura el GPS con High Accuracy (de lo contrario no funciona bien)

Autor: Nicolás Almaraz
Revisores: Guido Glorioso, Axel Nahumm, Santiago Palozzo
*/

package com.example.sensorvelocidadgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.*

data class Ubicacion(val latitude: Double, val longitude: Double)

class MainActivity : AppCompatActivity() {

    //Tiempo muestreo
    private val sampleTime = 1000L

    //Adquisicion de datos
    private var i = 0                   //Iterador
    private var lat = 0.0               //Promedio de latitudes
    private var long = 0.0              //Promedio de longitudes
    private val CANTIDAD_MUESTRAS = 3    //Cantidad de muestras a promediar

    //Media movil velocidad
    private var listaDeValores = mutableListOf<Double>()
    private var MAX_SIZE = 5

    //Ubicación Anterior
    private var ultimaUbi = Ubicacion(0.0,0.0)

    //Widgets UI
    private lateinit var tvLocation: TextView
    private lateinit var tvVelocity: TextView
    private lateinit var btnPerm: Button

    //Flag de permisos
    private var permisosOk = false

    //Handler del GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    //Constructor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Sincronizo Widgets
        tvLocation = findViewById(R.id.tvLocation)
        tvVelocity = findViewById(R.id.tvVelocidad)
        btnPerm = findViewById(R.id.btnPerm)

        //Chequeo Permisos
        checkPermissions()

        //Inicializo GPS
        if(permisosOk) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            startLocationUpdates()
        }
        
        //Boton de testeo de ubicación
        btnPerm.setOnClickListener() {
            if(permisosOk) {
                Toast.makeText(this,"Todo en regla!",Toast.LENGTH_LONG).show()
            } else
                Toast.makeText(this,"No tenes permisos naboleti!",Toast.LENGTH_LONG).show()
        }
    }

    //Le dice al GPS que mande continuamente muestras (ya chequea permisos en onCreate)
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, sampleTime)
        // Solicita actualizaciones de ubicación
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }


    //Callback de cuando llegan muestras
    private val locationCallback = object : LocationCallback() {
        @SuppressLint("SetTextI18n")
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            if (permisosOk) {
                val location = locationResult.lastLocation ?: return
                val pos = Ubicacion(location.latitude, location.longitude)

                //Informo posicion
                tvLocation.text = "Lat: ${pos.latitude}, Long: ${pos.longitude}"

                //Calculo velocidad
                val velocity = procesarVelocidad(pos) ?: return

                //Informo velocidad
                tvVelocity.text = String.format("%.0f", velocity)
            }
        }
    }

    /*
    Toma una N cantidad de muestras de latitud y longitud para promediarlas
    Luego calcula cuanto se movio respecto de la ultima vez
     */
    private fun procesarVelocidad(pos:Ubicacion):Double? {
        lat+=pos.latitude
        long+=pos.longitude
        i++
        if(i==CANTIDAD_MUESTRAS) {
            lat/=CANTIDAD_MUESTRAS
            long/=CANTIDAD_MUESTRAS

            val radioTierra = 6371.0 // Radio de la Tierra en kilómetros
            val lat1 = Math.toRadians(lat)
            val lon1 = Math.toRadians(long)
            val lat2 = Math.toRadians(ultimaUbi.latitude)
            val lon2 = Math.toRadians(ultimaUbi.longitude)

            val dLat = lat2 - lat1
            val dLon = lon2 - lon1

            val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            val distance = radioTierra * c

            ultimaUbi = Ubicacion(lat,long)
            lat=0.0
            long=0.0
            i=0
            var velocidad = (distance)/(sampleTime) * 3.6 * 1000 * 1000

            //Si mide ruido directamente lo tomo como 0
            if(velocidad <= 4.0)
                velocidad = 0.0
            velocidad = calcularPromedioMediaMovil(velocidad)
            return velocidad

        }
        return null
    }

    private fun calcularPromedioMediaMovil(valorActual: Double):Double {
        listaDeValores.add(valorActual)

        if (listaDeValores.size > MAX_SIZE) {
            listaDeValores.removeAt(0) // Eliminar el valor más antiguo
        }

        if (listaDeValores.size == MAX_SIZE) {
            val suma = listaDeValores.sum()
            return suma / MAX_SIZE
        }
        return valorActual
    }

    //Chequea si tiene permisos
    private fun checkPermissions() {
        //Verifico si tengo permisos
        val permFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val permCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permFine!=PackageManager.PERMISSION_GRANTED||
            permCoarse!=PackageManager.PERMISSION_GRANTED) {
            //Permiso no aceptado -> Pido permiso
            requestLocationPermission()
        } else {
            //Funciona normalmente
            permisosOk = true
            Toast.makeText(this,"Permisos Ok",Toast.LENGTH_LONG).show()
        }
    }

    //Funcion que se encarga de ver si hay que pedir permisos o si estos estaban rechazados
    private fun requestLocationPermission() {
        //Me fijo los permisos que alguna vez fueron rechazados
        val permFine = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val permCoarse = ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permFine || permCoarse) {
            Toast.makeText(this,"Usted ha rechazado los permisos de ubiucacion anteriormente, debe activarlos manualmente",Toast.LENGTH_LONG).show()
        } else {
            //Nunca fueron rechazados, le tiro el popUp
            val permFine2 = Manifest.permission.ACCESS_FINE_LOCATION
            val permCoarse2 = Manifest.permission.ACCESS_COARSE_LOCATION
            ActivityCompat.requestPermissions(this, arrayOf(permFine2, permCoarse2), 777)
        }
    }

    //Analiza resultados del PopUp de permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 777) {
            if (grantResults.size == 2) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1]==PackageManager.PERMISSION_GRANTED) {
                    //Todos los permisos fueron aceptados
                    permisosOk = true
                    Toast.makeText(this,"Permisos Aceptados! Primera vez",Toast.LENGTH_LONG).show()
                }
            }
            else {
                Toast.makeText(this,"Permisos rechazados! Primera vez",Toast.LENGTH_LONG).show()
            }
        }
    }
}

//Sirve para Loopear la funcion del callback del GPS
@SuppressLint("MissingPermission")
private fun FusedLocationProviderClient.requestLocationUpdates(locationRequest: LocationRequest.Builder, locationCallback: LocationCallback, mainLooper: Looper?) {
    // Convierte el LocationRequest.Builder en un LocationRequest
    val request = locationRequest.build()

    // Solicita actualizaciones de ubicación
    requestLocationUpdates(request, locationCallback, mainLooper)
}
