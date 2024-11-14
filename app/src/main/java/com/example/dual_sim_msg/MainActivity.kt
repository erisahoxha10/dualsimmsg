package com.example.dual_sim_msg

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSION = 1
    private lateinit var server: NettyApplicationEngine
    private lateinit var subscriptionManager: SubscriptionManager
    private var SIM_CARD = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestSmsPermission()

        subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

        // get the number of sim cards on the phone
        // if there is just one sim card, just display the button "Start server"
        // otherwise give the user the option to select which sim card to use

        // Find the button by its ID
        val startServerButton: Button = findViewById(R.id.startServer)
        val statusTextView: TextView = findViewById(R.id.statusTextView)
        val multipleSimLayout: View = findViewById(R.id.multipleSimLayout)

        // now find the num of sim cards in the phone
        val simCardNum = getSimSlotCount()
        if (simCardNum == 2) {
            // if the number of sim is 2, then populate the dropdown
            // create
            var simCardsSelect: Spinner = findViewById(R.id.simCards)

            // Prepare data for the Spinner
            val items = listOf("Sim 1", "Sim 2")

            // Create an ArrayAdapter
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)

            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            // Apply the adapter to the Spinner
            simCardsSelect.adapter = adapter

            // then display the layout where the user can select which sim card to use
            multipleSimLayout.visibility = View.VISIBLE

            // Set a listener for item selection
            simCardsSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: android.view.View, position: Int, id: Long) {
                    val selectedItem = parent.getItemAtPosition(position).toString()
                    if(selectedItem.equals("Sim 2"))
                        SIM_CARD = 1
                    Toast.makeText(this@MainActivity, "Selected: $selectedItem", Toast.LENGTH_SHORT).show()
                    // if the card is selected then enable the button to start the server
                    startServerButton.setEnabled(true)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    TODO("Not yet implemented")
                }
            }
        } else {
            startServerButton.setEnabled(true)
        }



        // Set up the click listener
        startServerButton.setOnClickListener {
            // Action to perform on button click
            startServer()
            Toast.makeText(this, "Server started!", Toast.LENGTH_LONG).show()
            startServerButton.setEnabled(false)
            statusTextView.setText("Server is running on this device on port 8080")
        }

    }

    private fun requestSmsPermission() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION)
    }

    private fun sendSmsFromSim(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQUEST_PERMISSION)
        } else {

            // get the sim cards info
            val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (subscriptionInfoList.isNotEmpty()) {
                // Assuming you want to use the first SIM
                val subscriptionId = subscriptionInfoList[SIM_CARD].subscriptionId
                val smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

                try {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    SIM_CARD++
                    Toast.makeText(this, "SMS sent successfully from SIM $SIM_CARD", Toast.LENGTH_SHORT).show()
                    Log.d("SMS", "Message sent successfully")
                } catch (e: Exception) {
                    Log.e("SMS", "Error sending SMS: ${e.message}")
                }
            } else {
                Log.e("SMS", "No active SIMs found")
            }
        }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            var allGranted = true

            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    Toast.makeText(this, "Permission denied for ${permissions[i]}.", Toast.LENGTH_SHORT).show()
                }
            }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted! You can send SMS.", Toast.LENGTH_SHORT).show()
                // Call your method to access SIM cards or send SMS here
                getSimSlotCount() // or any other method that requires permissions
            } else {
                Toast.makeText(this, "Some permissions were denied! Cannot send SMS.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        server.stop(0, 0)
        super.onDestroy()
    }

    private fun getSimSlotCount(): Int {
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        val simSlotCount = telephonyManager?.phoneCount ?: 0 // Get the number of phone slots
        Toast.makeText(this, "Number of SIM card slots: $simSlotCount", Toast.LENGTH_SHORT).show()
        return simSlotCount
    }

    private fun startServer() {
        server = embeddedServer(Netty, port = 8080) {
            // Ktor application module
            install(ContentNegotiation) {
                gson()
            }

            routing {
                get("/sendSMS") {
                    // Extract query parameters
                    val number = call.request.queryParameters["number"] ?: "No number provided"
                    val msg = call.request.queryParameters["msg"] ?: "No message provided"

                    // Process the request with the extracted values
                    val responseMessage = sendSmsFromSim(number, msg)

                    // Respond with the processed message
                    call.respond(HttpStatusCode.OK, responseMessage)
                }
            }
        }
        server.start()
    }
}