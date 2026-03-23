package com.reagent.tepraprint

import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import org.json.JSONException
import org.json.JSONObject

class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG = "TepraMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data

        // Check if the app was launched via the tepra-print:// URL scheme
        if (action == Intent.ACTION_VIEW && data != null && data.scheme == "tepra-print") {
            Log.d(TAG, "Launched via URL scheme: $data")

            // Extract the 'data' query parameter containing the JSON payload
            val jsonParam = data.getQueryParameter("data")

            if (jsonParam != null) {
                Log.d(TAG, "Received JSON data: $jsonParam")
                parsePrintData(jsonParam)
            } else {
                Log.w(TAG, "URL received but 'data' parameter is missing: $data")
            }
        }
    }

    private fun parsePrintData(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            Log.d(TAG, "Parsed print data:")
            Log.d(TAG, "  - controlNumber : ${json.optString("controlNumber", "N/A")}")
            Log.d(TAG, "  - reagentName   : ${json.optString("reagentName", "N/A")}")
            Log.d(TAG, "  - lotNumber     : ${json.optString("lotNumber", "N/A")}")
            Log.d(TAG, "  - expiryDate    : ${json.optString("expiryDate", "N/A")}")
            Log.d(TAG, "  - quantity      : ${json.optString("quantity", "N/A")}")
            Log.d(TAG, "  - unit          : ${json.optString("unit", "N/A")}")

            // TODO: Pass parsed data to TEPRA SDK and execute Bluetooth printing
            // Example: TepraBluetoothPrinter.print(json)

        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse JSON data: ${e.message}")
        }
    }
}
