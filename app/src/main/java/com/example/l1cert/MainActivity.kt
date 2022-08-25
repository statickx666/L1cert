package com.example.l1cert

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.gson.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    var pickedPhoto : Uri? = null
    var pickedBitMap : Bitmap? = null
    //var placeholderText : TextView? = null
    var Response : TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Response = findViewById(R.id.requestView)

        //Back button on label
        val display = supportActionBar
        display?.title = "Landmark Recognition"
        display?.setDisplayHomeAsUpEnabled(true)

        val logout = findViewById(R.id.LogOut) as Button
        logout.setOnClickListener{
            Toast.makeText(this@MainActivity, "Logged out", Toast.LENGTH_SHORT).show()
            logout()
        }
        val imgButton = findViewById(R.id.loadImg) as Button
        imgButton.setOnClickListener{
            pickedPhoto?.let { LoadImg(it) }

        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home ->{
                finish()
                logout()
                true
            }
            else ->super.onOptionsItemSelected(item)
        }
    }

    fun pickPhoto(view: View){ //view: View *Parameter*
        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) { // izin alınmadıysa
            ActivityCompat.requestPermissions(this,arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                1)
        } else {
            val galeriIntext = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galeriIntext,2)
            Response?.text = "Now press Process Image"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1) {
            if (grantResults.size > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val galeriIntext = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(galeriIntext,2)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 2 && resultCode == Activity.RESULT_OK && data != null) {
            pickedPhoto = data.data
            if (pickedPhoto != null) {
                if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(this.contentResolver,pickedPhoto!!)
                    pickedBitMap = ImageDecoder.decodeBitmap(source)
                    imageView.setImageBitmap(pickedBitMap)
                }
                else {
                    pickedBitMap = MediaStore.Images.Media.getBitmap(this.contentResolver,pickedPhoto)
                    imageView.setImageBitmap(pickedBitMap)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun logout() {
        val intent = Intent(applicationContext, Login::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK //Lanzar nuevamente el login y terminar el la ejecucion del activity anterior
        startActivity(intent)
        FirebaseAuth.getInstance().signOut()
        finish()
    }

    private fun LoadImg(uri: Uri){
        var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

        bitmap = scaleBitmapDown(bitmap, 640)

        // Convert bitmap to base64 encoded string
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
        val base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        // Create json request to cloud vision
        val request = JsonObject()
        // Add image to request
        val image = JsonObject()
        image.add("content", JsonPrimitive(base64encoded))
        request.add("image", image)
        //Add features to the request
        val feature = JsonObject()
        feature.add("maxResults", JsonPrimitive(5))
        feature.add("type", JsonPrimitive("LANDMARK_DETECTION"))
        val features = JsonArray()
        features.add(feature)
        request.add("features", features)

        //val request = jsonReq(base64encoded)
        var data = ""
        var position = ""

        annotateImage(request.toString())
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Failed req", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Success req", Toast.LENGTH_SHORT).show()
                    // ...
                    for (label in task.result!!.asJsonArray[0].asJsonObject["landmarkAnnotations"].asJsonArray) {
                        val labelObj = label.asJsonObject
                        val landmarkName = labelObj["description"]
                        val entityId = labelObj["mid"]
                        val score = labelObj["score"]
                        val bounds = labelObj["boundingPoly"]
                        // Multiple locations are possible, e.g., the location of the depicted
                        // landmark and the location the picture was taken.
                        for(loc in labelObj["locations"].asJsonArray) {
                            val latitude = loc.asJsonObject["latLng"].asJsonObject["latitude"]
                            val longitude = loc.asJsonObject["latLng"].asJsonObject["longitude"]
                            position += "Lat: " + latitude.toString() + "\n Long: " + longitude.toString() + "\n"
                        }
                        data += "Landmark: " + landmarkName.toString() + "\n Score: " + score.toString() + "\n" + position
                    }
                    Response?.text = data
                }
            }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension
        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth =
                (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight =
                (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

    private fun annotateImage(requestJson: String): Task<JsonElement> {
        lateinit var functions: FirebaseFunctions
        functions = Firebase.functions

        return functions
            .getHttpsCallable("annotateImage")
            .call(requestJson)
            .continueWith { task ->
                // This continuation runs on either success or failure, but if the task
                // has failed then result will throw an Exception which will be
                // propagated down.
                val result = task.result?.data
                JsonParser.parseString(Gson().toJson(result))
            }
        //Log.e(requestJson.toString(),"json parse")
    }

    private fun jsonReq(base64encoded: String){
        // Create json request to cloud vision
        val request = JsonObject()
        // Add image to request
        val image = JsonObject()
        image.add("content", JsonPrimitive(base64encoded))
        request.add("image", image)
        //Add features to the request
        val feature = JsonObject()
        feature.add("maxResults", JsonPrimitive(5))
        feature.add("type", JsonPrimitive("LANDMARK_DETECTION"))
        val features = JsonArray()
        features.add(feature)
        request.add("features", features)
    }
}