package com.example.myapplication

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.example.myapplication.databinding.ActivityDriverHomeBinding
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import de.hdodenhof.circleimageview.CircleImageView


class DriverHomeActivity : AppCompatActivity() {

    //dialog properties
    private lateinit var tvText: TextView
    private lateinit var progressDialog: AlertDialog


    private lateinit var imgUri: Uri
    private lateinit var imgAvatar: CircleImageView

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityDriverHomeBinding

    private lateinit var storageReference: StorageReference
    private lateinit var currentUserRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageReference = FirebaseStorage.getInstance().reference
        binding = ActivityDriverHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //initialize progress bar for upload profile avatar to firebase storage
        initProgressBar()
        setSupportActionBar(binding.appBarDriverHome.toolbar)
        currentUserRef =
            FirebaseDatabase.getInstance().reference.child(Common.DRIVER_INFO_REFFERENCE + "/" + FirebaseAuth.getInstance().currentUser!!.uid)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_driver_home) as NavHostFragment
        val navController =
            navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.appBarDriverHome.toolbar.title = "driver Home"
        navView.setupWithNavController(navController)

        // set on navigation item listener
        navView.setNavigationItemSelectedListener {
            if (it.itemId == R.id.nav_sign_out) {
                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setTitle("Sign Out").setMessage("Do You Really Want to Sign Out")
                    .setPositiveButton("yes") { dialogInterface, _ ->
                        FirebaseDatabase.getInstance().getReference()
                            .child(Common.DRIVER_LOCATION_REFFERENCE)
                            .child(FirebaseAuth.getInstance().currentUser!!.uid)
                            .removeValue()
                        FirebaseAuth.getInstance().signOut()
                        intent = Intent(this@DriverHomeActivity, SplashScreenActivity::class.java)
                        startActivity(intent)
                        finish()
                    }.setNegativeButton("Cancel") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                        navView.setCheckedItem(R.id.nav_home);
                    }.setCancelable(false)
                val dialog = alertDialog.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(
                            ContextCompat.getColor(
                                this,
                                R.color.red
                            )
                        )
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(
                            ContextCompat.getColor(
                                this,
                                R.color.black
                            )
                        )
                }
                dialog.show()
            }
            true
        }
        val naveHeader = navView.getHeaderView(0)

        // set navigation profile information
        val txtRate = naveHeader.findViewById<TextView>(R.id.txt_star)
        val txtName = naveHeader.findViewById<TextView>(R.id.txt_name)
        val txtPhoneNumber = naveHeader.findViewById<TextView>(R.id.txt_phoneNumber)
        imgAvatar = naveHeader.findViewById(R.id.img_avatar)
        if (!Common.currentUser!!.avatar.isEmpty())
            Glide.with(this).load(Common.currentUser!!.avatar).into(imgAvatar)


        val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) {
            imgUri = it!!
            Log.wtf("amrrr",imgUri.toString())
            imgAvatar.setImageURI(it)
            showUploadDialog()
        }

        // avatar listener to change profile avatar
        imgAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }
        txtRate.setText(Common.currentUser!!.rate)
        txtName.setText(Common.buildWelcomeMessage())
        txtPhoneNumber.setText(Common.currentUser!!.phoneNumber)


    }


    private fun showUploadDialog() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Change Avatar").setMessage("Do You Want To Change Avatar")
            .setPositiveButton("Change") { dialogInterface, _ ->
                val avatarFolder =
                    storageReference.child("avatars/${FirebaseAuth.getInstance().currentUser!!.uid}")
                avatarFolder.putFile(imgUri).addOnProgressListener { listener ->
                    progressDialog.show()
                    tvText.text =
                        "Loading...${(100 * listener.bytesTransferred) / listener.totalByteCount}%"
                }.addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }.addOnSuccessListener {
                    avatarFolder.downloadUrl.addOnSuccessListener {
                        currentUserRef.child("avatar").setValue(it.toString())
                        progressDialog.dismiss()
                    }
                }
            }.setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }.setCancelable(false)

        val dialog = alertDialog.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.red
                    )
                )
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.black
                    )
                )
        }
        dialog.show()
    }

    //initialize Progressbar to show avatar image uploading progress to firebase storage
    private fun initProgressBar() {
        // Creating a Linear Layout
        val llPadding = 30
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.setPadding(llPadding, llPadding, llPadding, llPadding)
        ll.gravity = Gravity.CENTER
        var llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        ll.layoutParams = llParam

        // Creating a ProgressBar inside the layout
        val progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.setPadding(0, 0, llPadding, 0)
        progressBar.layoutParams = llParam
        llParam = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER

        // Creating a TextView inside the layout
        tvText = TextView(this)
        tvText.setTextColor(Color.parseColor("#000000"))
        tvText.textSize = 20f
        tvText.layoutParams = llParam
        ll.addView(progressBar)
        ll.addView(tvText)

        // Setting the AlertDialog Builder view
        // as the Linear layout created above
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setView(ll)

        // Displaying the dialog
        progressDialog = builder.create()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.driver_home, menu)
        return true
    }


    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_driver_home)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}