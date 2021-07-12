package com.angelo.nf.convertcash

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_inicio.*


class Inicio : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio)
        //Verifica los permisos al inicio de la aplicacion
        if(!allPermissionsGranted()){
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        //Boton de siguiente de la pantalla inicial
        pi_btn_1.setOnClickListener{ onClickSiguiente() }

    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {

            } else {
                Toast.makeText(this,
                    R.string.error_permisos,
                    Toast.LENGTH_SHORT).show()

            }
        }
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        //Comprueba el array de permisos
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun onClickSiguiente(){
        //Al darle siguiente comprueba los permisos
        if(allPermissionsGranted()){
            //Si los permisos son correctos ejecuta la nueva activity
            val i = Intent(this, HomeCamera::class.java)
            startActivity(i)
            //Cierra la activity anterior
            finish()
        }else{
            //Si no tiene los permisos, vuelve a solicitarlos
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }


}