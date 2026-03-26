package com.reagent.tepraprint

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import org.json.JSONObject

class PrintStatusActivity : Activity() {

    companion object {
        private const val TAG = "PrintStatusActivity"
        const val EXTRA_LABEL_JSON = "extra_label_json"
        private const val PREF_NAME = "tepra_settings"
        private const val PREF_PRINTER_ADDRESS = "printer_address"
        private const val REQ_BT_PERMISSION = 1001
        private const val DEFAULT_PRINTER_ADDRESS = "68:84:7E:64:E4:B7"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val labelJson = intent.getStringExtra(EXTRA_LABEL_JSON) ?: run {
            Log.e(TAG, "No label data in Intent")
            finish()
            return
        }

        val firstLabel = try {
            val jsonObj = JSONObject(labelJson)
            TepraLabel.fromJsonArray(jsonObj).firstOrNull()
                ?: TepraLabel.fromJson(jsonObj)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse label JSON", e)
            toast("印刷データの解析に失敗しました")
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), REQ_BT_PERMISSION)
                return
            }
        }

        startPrinting(firstLabel)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_BT_PERMISSION) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            val labelJson = intent.getStringExtra(EXTRA_LABEL_JSON) ?: return finish()
            val jsonObj = JSONObject(labelJson)
            val firstLabel = TepraLabel.fromJsonArray(jsonObj).firstOrNull()
                ?: TepraLabel.fromJson(jsonObj)
            startPrinting(firstLabel)
        } else {
            toast("Bluetooth接続権限が必要です")
            finish()
        }
    }

    private fun startPrinting(label: TepraLabel) {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            toast("Bluetoothを有効にしてください")
            finish()
            return
        }

        val savedAddress: String? = prefs.getString(PREF_PRINTER_ADDRESS, DEFAULT_PRINTER_ADDRESS)
        if (savedAddress != null) {
            executePrint(adapter, savedAddress, label)
        } else {
            selectPrinterThenPrint(adapter, label)
        }
    }

    private fun selectPrinterThenPrint(adapter: BluetoothAdapter, label: TepraLabel) {
        val paired = adapter.bondedDevices.toList()
        if (paired.isEmpty()) {
            toast("ペアリング済みのデバイスが見つかりません")
            finish()
            return
        }

        val candidates = paired.filter { device ->
            device.name?.contains("TEPRA", ignoreCase = true) == true ||
            device.name?.contains("SR5500", ignoreCase = true) == true
        }.ifEmpty { paired }

        if (candidates.size == 1) {
            prefs.edit().putString(PREF_PRINTER_ADDRESS, candidates[0].address).apply()
            executePrint(adapter, candidates[0].address, label)
            return
        }

        val displayNames = candidates.map { "${it.name}  (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("プリンターを選択")
            .setItems(displayNames) { _, idx ->
                val selected = candidates[idx]
                prefs.edit().putString(PREF_PRINTER_ADDRESS, selected.address).apply()
                executePrint(adapter, selected.address, label)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun executePrint(adapter: BluetoothAdapter, address: String, label: TepraLabel) {
        val labelJson = intent.getStringExtra(EXTRA_LABEL_JSON) ?: run {
            finish()
            return
        }
        val jsonObj = JSONObject(labelJson)
        val labels = TepraLabel.fromJsonArray(jsonObj).ifEmpty { listOf(label) }

        val printer = TepraPrinter(this, address)

        toast("印刷中... (${labels.size}枚)")

        Thread {
            try {
                for ((index, lbl) in labels.withIndex()) {
                    // 2枚目以降はプリンターの処理完了を待つ
                    if (index > 0) {
                        Log.d(TAG, "Waiting 3s before next label (${index + 1}/${labels.size})...")
                        Thread.sleep(3000)
                    }
                    val bitmap = TepraLabelRenderer.render(lbl)
                    printer.print(bitmap, lbl.tapeWidthMm)
                    Log.d(TAG, "Label ${index + 1}/${labels.size} printed")
                }
                handler.post {
                    toast("印刷完了 (${labels.size}枚)")
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Print failed: ${e.message}", e)
                handler.post {
                    toast("印刷に失敗しました: ${e.message}")
                    finish()
                }
            }
        }.start()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
