package com.example.myapplication


import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.myapplication.Model.RequestInfoModel
import com.example.myapplication.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import de.hdodenhof.circleimageview.CircleImageView
import java.util.*


class HomeFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener {
    private var added = false
    private var riderKey: String? = null
    private lateinit var slidePanelTitle: TextView
    private lateinit var slidingPanelView: FrameLayout
    private lateinit var slidingPanelLayout: SlidingUpPanelLayout


    private var onlineSystemRegistered: Boolean = false
    private var _binding: FragmentHomeBinding? = null

    //Location Properties
    private lateinit var currentLatLng: LatLng
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentuserRef: DatabaseReference? = null
    private lateinit var driverLocationRef: DatabaseReference
    private lateinit var riderRequestListener: ValueEventListener

    // Online Reference and listener for persistence system
    private lateinit var onlineRef: DatabaseReference
    private val onlineValueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists() && currentuserRef != null) {
                //define onDisconnect methods when user becomes online
                //remove current user value when disconnect
                currentuserRef!!.onDisconnect().removeValue()
                Snackbar.make(requireView(), "you are online", Snackbar.LENGTH_LONG)
                    .show()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
        }

    }

    //Register for Location Permission Activity
    @SuppressLint("MissingPermission")
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { res ->
            // Handle Permission granted/rejected
            res.entries.forEach {
                if (it.value) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    fusedLocationProviderClient.lastLocation.addOnSuccessListener {
                        if (it != null) {
                            mMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(
                                        it.latitude,
                                        it.longitude
                                    ), 18f
                                )
                            )
                        } else return@addOnSuccessListener

                    }


                } else {
                    return@registerForActivityResult
                }
            }
        }


    //Google Map
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mapFragment = getChildFragmentManager()
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        //initialize sliding panel layout & sliding panel view
        slidingPanelLayout = binding.slidingLayout
        slidingPanelView = binding.slidingPanelView


        init()


        return root
    }

    @SuppressLint("MissingPermission")
    private fun init() {
        onlineRef =
            FirebaseDatabase.getInstance().getReference().child(".info/connected")


        //Location Initialize
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())
        locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.setInterval(5000).setFastestInterval(3000).setSmallestDisplacement(10f)
        locationCallback = object : LocationCallback() {

            override fun onLocationResult(locationResult: LocationResult) {
                currentLatLng = LatLng(
                    locationResult.lastLocation!!.latitude,
                    locationResult.lastLocation!!.longitude
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))

                // get city name based on Location provided from fusedLocationProvider
                val geoCoder = Geocoder(context, Locale.getDefault())
                val addressList: List<Address>
                val cityName: String
                try {
                    addressList = geoCoder.getFromLocation(
                        locationResult.lastLocation!!.latitude,
                        locationResult.lastLocation!!.longitude,
                        1
                    )
                    cityName = addressList.get(0).locality
                    driverLocationRef =
                        FirebaseDatabase.getInstance()
                            .getReference(Common.DRIVER_LOCATION_REFFERENCE + "/${cityName}")
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }


                currentuserRef =
                    driverLocationRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                if (!onlineSystemRegistered) {
                    registerOnlineSystem()
                    onlineSystemRegistered = true
                }
                //store current location in realtime database using GeoFire
                val geoFire = GeoFire(driverLocationRef)
                geoFire.setLocation(
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    GeoLocation(
                        locationResult.lastLocation!!.latitude,
                        locationResult.lastLocation!!.longitude
                    )
                ) { key: String, error: DatabaseError? ->
                    if (error != null) {
                        Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        addListenerForRiderRequests()


    }
    private fun showCancelDialog() {

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Cancel")
        builder.setMessage("the trip has been canceled by rider")
        //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

        builder.setPositiveButton("ok") { dialog, which ->
            dialog.dismiss()
        }

        builder.show()
    }
    private fun addListenerForRiderCancel() {
        val cancelListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.value == "rider canceled") {
                    cancelAll()
                    showCancelDialog()
                    FirebaseDatabase.getInstance()
                        .getReference(Common.canceledRequestRef)
                        .child(Common.driverID).child(riderKey!!)
                        .onDisconnect()
                        .setValue(null)
                    FirebaseDatabase.getInstance()
                        .getReference(Common.canceledRequestRef)
                        .child(Common.driverID).child(riderKey!!)
                        .setValue(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
            }
        }
        FirebaseDatabase.getInstance()
            .getReference(Common.canceledRequestRef)
            .child(Common.driverID).child(riderKey!!)
            .addValueEventListener(cancelListener)

    }

    private fun addListenerForRiderRequests() {
        //listener to get the rider request information
        riderRequestListener = object : ValueEventListener {
            private lateinit var requestInfo: RequestInfoModel
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    requestInfo = snapshot.getValue(RequestInfoModel::class.java)!!
                    riderKey = requestInfo.riderID
                    if (!added){
                        addListenerForRiderCancel()
                        added = true
                    }
                    FirebaseDatabase.getInstance().getReference(Common.canceledRequestRef)
                        .child(Common.driverID).child(riderKey!!).addListenerForSingleValueEvent(
                            object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (!snapshot.exists()){
                                        showRequestPanel(requestInfo)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                }
                            })
                }
            }


            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
            }

            private fun showRequestPanel(requestInfo: RequestInfoModel) {
                val requestLayout =
                    layoutInflater.inflate(R.layout.request_confirmation_layout, null)
                slidingPanelView.addView(requestLayout)
                slidingPanelLayout.anchorPoint = 1f
                slidingPanelLayout.panelState = SlidingUpPanelLayout.PanelState.ANCHORED
                slidingPanelLayout.panelHeight = 135

                slidePanelTitle = requestLayout.findViewById(R.id.title)
                val cancel_btn = requestLayout.findViewById<Button>(R.id.cancel_btn)
                val confirm_btn = requestLayout.findViewById<Button>(R.id.confirm_btn)
                val pickup_btn = requestLayout.findViewById<Button>(R.id.pickup_btn)
                val finish_btn = requestLayout.findViewById<Button>(R.id.finish_btn)
                val riderName = requestLayout.findViewById<TextView>(R.id.rider_name)
                val riderNumber = requestLayout.findViewById<TextView>(R.id.rider_number)
                val riderImage = requestLayout.findViewById<CircleImageView>(R.id.rider_image)

                riderName.text =
                    "Name : ${requestInfo.riderInfo!!.firstName} ${requestInfo.riderInfo!!.lastName}"
                riderNumber.text = "Mobile : ${requestInfo.riderInfo!!.phoneNumber}"
                if (!requestInfo.riderInfo!!.avatar.isEmpty())
                    Glide.with(requireContext()).load(requestInfo.riderInfo!!.avatar)
                        .into(riderImage)

                cancel_btn.setOnClickListener {
                    cancelAll()
                    FirebaseDatabase.getInstance()
                        .getReference(Common.canceledRequestRef).child(Common.driverID)
                        .child(riderKey!!).setValue("driver canceled")
                }

                confirm_btn.setOnClickListener {
                    FirebaseDatabase.getInstance()
                        .getReference(Common.confirmedRequestRef).child(Common.driverID)
                        .child(requestInfo.riderID!!)
                        .setValue("Confirmed")
                    FirebaseDatabase.getInstance()
                        .getReference(Common.pendingRequestRef).child(Common.driverID)
                        .removeValue()
                    slidePanelTitle.text = "Pickup The Rider Now"

                    confirm_btn.visibility = View.GONE
                    pickup_btn.visibility = View.VISIBLE





                    showRiderLocationMark(requestInfo)

                }

                pickup_btn.setOnClickListener {
                    pickup_btn.visibility = View.GONE
                    finish_btn.visibility = View.VISIBLE
                    FirebaseDatabase.getInstance()
                        .getReference(Common.confirmedRequestRef).child(Common.driverID)
                        .child(requestInfo.riderID!!)
                        .removeValue()
                    FirebaseDatabase.getInstance()
                        .getReference(Common.inTripRequestRef).child(Common.driverID)
                        .child(requestInfo.riderID!!)
                        .setValue("inTrip")
                    mMap.clear()
                    slidePanelTitle.text = "The Trip Has Started"
                }

                finish_btn.setOnClickListener {
                    FirebaseDatabase.getInstance()
                        .getReference(Common.finishedRequestRef).child(Common.driverID)
                        .child(requestInfo.riderID!!)
                        .setValue("Finished")
                    FirebaseDatabase.getInstance()
                        .getReference(Common.inTripRequestRef).child(Common.driverID)
                        .child(requestInfo.riderID!!)
                        .removeValue()
                    showCostDialog()
                    slidingPanelView.removeAllViews()
                    FirebaseDatabase.getInstance()
                        .getReference(Common.finishedRequestRef).child(Common.driverID)
                        .child(requestInfo.riderID!!)
                        .removeValue()


                }
            }

            private fun showCostDialog() {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Trip Cost")
                builder.setMessage("the trip has been finished and the cost is : 22$")
                //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

                builder.setPositiveButton("ok") { dialog, which ->
                    dialog.dismiss()
                    showNotification()
                }

                builder.show()

            }

            private fun showNotification() {
                //build Notfication and set properties
                val builder = NotificationCompat.Builder(requireContext(), "1")
                    .setSmallIcon(R.drawable.splash_screen)
                    .setContentTitle("Cost")
                    .setContentText("the trip has been finished and the cost is : 22$")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)

                createNotificationChannel()
                with(NotificationManagerCompat.from(requireContext())) {
                    // notificationId is a unique int for each notification that you must define
                    notify("1",1, builder.build())
                }
            }

            private fun createNotificationChannel() {
                // Create the NotificationChannel, but only on API 26+ because
                // the NotificationChannel class is new and not in the support library
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = getString(R.string.app_name)
                    val descriptionText = "description text for notification"
                    val importance = NotificationManager.IMPORTANCE_HIGH
                    val channel = NotificationChannel("1", name, importance).apply {
                        description = descriptionText
                    }
                    // Register the channel with the system
                    val notificationManager: NotificationManager =
                        activity!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(channel)
                }

            }

            private fun showRiderLocationMark(requestInfo: RequestInfoModel) {
                val position = LatLng(requestInfo.riderLat!!, requestInfo.riderLong!!)
                mMap.addMarker(
                    MarkerOptions().title(requestInfo.riderInfo!!.firstName).position(position)
                )
            }

        }

        FirebaseDatabase.getInstance()
            .getReference(Common.pendingRequestRef).child(Common.driverID)
            .addValueEventListener(riderRequestListener)

    }

    private fun cancelAll() {
        FirebaseDatabase.getInstance()
            .getReference(Common.pendingRequestRef).child(Common.driverID)
            .removeValue()
        FirebaseDatabase.getInstance()
            .getReference(Common.confirmedRequestRef).child(Common.driverID)
            .removeValue()
        FirebaseDatabase.getInstance()
            .getReference(Common.inTripRequestRef).child(Common.driverID)
            .removeValue()


        mMap.clear()
        slidingPanelView.removeAllViews()
    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }


    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getLocationPermission()
        val locationButton =
            (mapFragment.view?.findViewById<View>(Integer.parseInt("1"))?.parent as View).findViewById<View>(
                Integer.parseInt("2")
            )
        val rlp = locationButton.getLayoutParams() as RelativeLayout.LayoutParams
        rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        rlp.setMargins(0, 0, 30, 180)
        mMap.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(),
                R.raw.uber_maps_style
            )
        )

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback,
            Looper.myLooper()!!
        )

    }

    @SuppressLint("MissingPermission")
    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
            return
        } else {
            activityResultLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
        return true
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        onlineRef.removeEventListener(onlineValueEventListener)

        FirebaseDatabase.getInstance()
            .getReference(Common.pendingRequestRef + "/${Common.driverID}")
            .removeEventListener(riderRequestListener)


        _binding = null
        super.onDestroy()
    }


}