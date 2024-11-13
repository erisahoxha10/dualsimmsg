package com.example.dual_sim_msg

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.ktor.application.Application
import io.ktor.server.engine.*
import io.ktor.server.netty.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_SMS_PERMISSION = 1
    private lateinit var server: NettyApplicationEngine


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestSmsPermission()

        startServer()


        // Find the button by its ID
        val sendButton: Button = findViewById(R.id.sendButton)
        val phoneNumberInput: EditText = findViewById(R.id.phoneNumber)
        val messageInput: EditText = findViewById(R.id.message)


        // Set up the click listener
        sendButton.setOnClickListener {
            // Action to perform on button click
            val phoneNumber = phoneNumberInput.text.toString()
            val message = messageInput.text.toString()

            sendSmsFromSpecificSim2(phoneNumber, message)
            Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()
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
            // Get the SubscriptionManager
            val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (subscriptionInfoList.isNotEmpty()) {
                // Assuming you want to use the first SIM
                val subscriptionId = subscriptionInfoList[1].subscriptionId
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
        server = embeddedServer(Netty, port = 8080, module = Application::module)
        server.start()
    }
}