package com.example.myapplication

import com.example.myapplication.Model.DriverInfoModel
import java.lang.StringBuilder

object Common {
    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome ").append(currentUser!!.firstName).append(" ")
            .append(currentUser!!.lastName).toString()
    }

    val canceledRequestRef = "Requests/Canceled"
    val TokenRef: String = "Tokens"
    val pendingRequestRef = "Requests/Pending"
    val confirmedRequestRef = "Requests/Confirmed"
    val inTripRequestRef = "Requests/InTrip"
    val finishedRequestRef = "Requests/Finished"
    var currentUser: DriverInfoModel? = null
    val DRIVER_LOCATION_REFFERENCE: String = "DriverLocation"
    val DRIVER_INFO_REFFERENCE: String = "DriverInfo"
    lateinit var driverID : String
}