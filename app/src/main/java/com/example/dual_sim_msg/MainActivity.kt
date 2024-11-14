package com.example.dual_sim_msg

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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

    private val REQUEST_SMS_PERMISSION = 1
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
        val simCardNum = getSimCardCount()
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

        ActivityCompat.requestPermissions(this, permissions, REQUEST_SMS_PERMISSION)
    }

    private fun sendSmsFromSpecificSim2(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQUEST_SMS_PERMISSION)
        } else {

            // get the sim cards info
            val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (subscriptionInfoList.isNotEmpty()) {
                // Assuming you want to use the first SIM
                val subscriptionId = subscriptionInfoList[SIM_CARD].subscriptionId
                val smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

                try {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    Toast.makeText(this, "SMS sent successfully from specific SIM", Toast.LENGTH_SHORT).show()
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
        if (requestCode == REQUEST_SMS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted! You can send SMS.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied! Cannot send SMS.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        server.stop(0, 0)
        super.onDestroy()
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
                    val responseMessage = sendSmsFromSpecificSim2(number, msg)

                    // Respond with the processed message
                    call.respond(HttpStatusCode.OK, responseMessage)
                }
            }
        }
        server.start()
    }

    private fun getSimCardCount(): Int {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                REQUEST_SMS_PERMISSION
            )
            return 0
        } else {
            val subscriptionInfoList = subscriptionManager?.activeSubscriptionInfoList
            val simCount = subscriptionInfoList?.size ?: 0
            Toast.makeText(this, "Number of SIM cards: $simCount", Toast.LENGTH_SHORT).show()
            return simCount
        }
    }
}