package com.example.l1cert

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.l1cert.MainActivity
import com.example.l1cert.R
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.AuthUI.IdpConfig.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.util.*

class Login : AppCompatActivity() {
    //RC_SIGN in is the request code
    // you will assign for starting the new activity
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        FirebaseApp.initializeApp(baseContext);

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Si el usuario ya esta logueado pasar a la actividad principal
            val homeIntent = Intent(this, MainActivity::class.java)
            startActivity(homeIntent)
        } else {
            // Si no mostrar el FirebaseUI
            showFirebaseUI()
        }
    }

    fun showFirebaseUI() {
        val providers = Arrays.asList(
            EmailBuilder().build(),
            PhoneBuilder().build(),
            GoogleBuilder().build(),
            AnonymousBuilder().build()
        )
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false) //Desactiviar incio automatico
                .build(), RC_SIGN_IN
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                val homeIntent = Intent(this, MainActivity::class.java)
                startActivity(homeIntent)
            } else {
                finish()
            }
        }
    }

    companion object {
        private const val RC_SIGN_IN = 123
    }
}