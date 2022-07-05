package com.example.myapplication


import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide.init
import com.example.myapplication.Model.DriverInfoModel
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import java.util.concurrent.TimeUnit


class SplashScreenActivity : AppCompatActivity() {


    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference
    val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val user = firebaseAuth.currentUser
            if (user != null) {
                Toast.makeText(this, "Welcome : ${user.uid}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        init()
        delaySplash()
    }


    private fun delaySplash() {
        Completable.timer(2, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe { firebaseAuth.addAuthStateListener(listener) }
    }

    fun init() {
        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFFERENCE)
        providers = listOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = Firebase.auth
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                Common.driverID = FirebaseAuth.getInstance().currentUser!!.uid

                // get token and store it in realtime database
                FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        return@OnCompleteListener
                    }


                    // store token key in realtime database
                    FirebaseDatabase.getInstance().reference.child(Common.TokenRef)
                        .child(Common.driverID + "/Token")
                        .setValue(task.result)

                })
                checkUserFromFireBase()
            } else {
                showSignInLayout()
            }
        }

    }


    private fun checkUserFromFireBase() {
        driverInfoRef.child(Common.driverID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val model = snapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG)
                        .show()
                }

            })
    }

    private fun goToHomeActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        val intent = Intent(this, DriverHomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.dialogTheme)

        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val edt_first_name = itemView.findViewById<TextInputEditText>(R.id.edt_first_name)
        val edt_last_name = itemView.findViewById<TextInputEditText>(R.id.edt_last_name)
        val edt_phone_number = itemView.findViewById<TextInputEditText>(R.id.edit_phone_number)
        val btn_continue = itemView.findViewById<Button>(R.id.btn_register)

        edt_phone_number.setText(firebaseAuth.currentUser!!.phoneNumber)

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        btn_continue.setOnClickListener {
            val model = DriverInfoModel()
            model.firstName = edt_first_name.text.toString()
            model.lastName = edt_last_name.text.toString()
            model.phoneNumber = edt_phone_number.text.toString()
            model.rate = "0.0"

            driverInfoRef.child(Common.driverID).setValue(model)
                .addOnFailureListener { e ->
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
                .addOnSuccessListener {
                    Toast.makeText(this, "Register done", Toast.LENGTH_LONG).show()
                    goToHomeActivity(model)


                }
            dialog.dismiss()
        }


    }

    private fun showSignInLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in).setGoogleButtonId(R.id.btn_google_sign_in)
            .build()
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false).setAuthMethodPickerLayout(authMethodPickerLayout)
            .setTheme(R.style.loginTheme)
            .build()

        signInLauncher.launch(signInIntent)
    }


    override fun onDestroy() {
        firebaseAuth.removeAuthStateListener(listener)
        super.onDestroy()
    }


}