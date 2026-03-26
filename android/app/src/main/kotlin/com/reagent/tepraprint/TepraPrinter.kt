package com.reagent.tepraprint

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import jp.co.kingjim.tepraprint.sdk.TepraPrint
import jp.co.kingjim.tepraprint.sdk.TepraPrintCallback
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverConnectionType
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverPrinter
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverPrinterCallback
import jp.co.kingjim.tepraprint.sdk.TepraPrintParameterKey
import jp.co.kingjim.tepraprint.sdk.TepraPrintPrintSpeed
import jp.co.kingjim.tepraprint.sdk.TepraPrintPrintingPhase
import jp.co.kingjim.tepraprint.sdk.TepraPrintStatusError
import jp.co.kingjim.tepraprint.sdk.TepraPrintTapeCut
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TEPRA SR5500P プリンターへの印刷を行うクラス。
 * King Jim 公式 TepraPrint SDK を使用。
 *
 * 流れ: Discover（検出＆ペアリング）→ ステータス取得 → 印刷
 */
class TepraPrinter(private val context: Context, private val address: String) {

    companion object {
        private const val TAG = "TepraPrinter"
        private val SPP_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val tepraPrint = TepraPrint(context)
    private var lastError: String? = null

    /**
     * SDK呼び出し前にraw Bluetoothソケットで接続→切断して
     * Bluetoothスタックを「温める」。
     * SDKは createRfcommSocketToServiceRecord のみを使うが、
     * 一部デバイスでは先にリフレクション接続を行わないとSPPが動かない。
     */
    private fun warmUpBluetooth(macAddress: String) {
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: return
            val device = adapter.getRemoteDevice(macAddress)

            Log.d(TAG, "Warming up Bluetooth connection to $macAddress...")

            var socket: BluetoothSocket? = null
            try {
                // まず標準UUID接続を試す
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                Log.d(TAG, "Warm-up: standard UUID connect succeeded")
            } catch (e: Exception) {
                Log.d(TAG, "Warm-up: standard UUID failed (${e.message}), trying reflection...")
                socket?.close()
                try {
                    @Suppress("DiscouragedPrivateApi")
                    socket = device.javaClass
                        .getMethod("createRfcommSocket", Int::class.java)
                        .invoke(device, 1) as BluetoothSocket
                    socket.connect()
                    Log.d(TAG, "Warm-up: reflection connect succeeded")
                } catch (e2: Exception) {
                    Log.w(TAG, "Warm-up: reflection connect also failed (${e2.message})")
                    socket?.close()
                    socket = null
                }
            }

            // 接続できたらすぐ切断（Bluetoothスタックが温まった状態になる）
            if (socket != null) {
                Thread.sleep(500)
                socket.close()
                Log.d(TAG, "Warm-up: connection closed, Bluetooth stack ready")
                Thread.sleep(1000) // SDKが接続する前に少し待つ
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up failed: ${e.message}")
        }
    }

    /**
     * SDKのプリンター検出機能でプリンターを見つけて情報を取得する。
     * これにより自動ペアリングも行われる。
     */
    @Throws(Exception::class)
    private fun discoverPrinter(): Map<String, String> {
        Log.d(TAG, "Starting printer discovery (Bluetooth)...")

        val discoverLatch = CountDownLatch(1)
        var foundPrinterInfo: Map<String, String>? = null

        // SR5500P をBluetooth Classicで検出
        val models = listOf("SR5500P", "SR-MK1", "SR-R2500P")
        val connType = EnumSet.of(TepraPrintDiscoverConnectionType.ConnectionTypeBluetooth)
        val discover = TepraPrintDiscoverPrinter(null, models, connType)

        discover.setCallback(object : TepraPrintDiscoverPrinterCallback {
            override fun onFindPrinter(
                discoverPrinter: TepraPrintDiscoverPrinter,
                printer: Map<String, String>
            ) {
                // 見つかったプリンターの全情報をログに出力
                Log.d(TAG, "=== Discovered Printer ===")
                for ((key, value) in printer) {
                    Log.d(TAG, "  $key = $value")
                }
                Log.d(TAG, "=========================")

                val name = printer[TepraPrintDiscoverPrinter.PRINTER_INFO_NAME] ?: ""

                // TEPRA/SR5500Pを含むプリンターを採用（最初に見つかったものを使用）
                if (foundPrinterInfo == null && (
                    name.contains("SR5500", ignoreCase = true) ||
                    name.contains("TEPRA", ignoreCase = true) ||
                    name.contains("SR-R", ignoreCase = true)
                )) {
                    Log.d(TAG, "Matched target printer: $name")
                    foundPrinterInfo = HashMap(printer)
                    discoverLatch.countDown()
                }
            }

            override fun onRemovePrinter(
                discoverPrinter: TepraPrintDiscoverPrinter,
                printer: Map<String, String>
            ) {
                Log.d(TAG, "Printer removed: ${printer[TepraPrintDiscoverPrinter.PRINTER_INFO_NAME]}")
            }
        })

        discover.startDiscover(context)

        // 最大20秒待機
        val found = discoverLatch.await(20, TimeUnit.SECONDS)
        discover.stopDiscover()

        if (!found || foundPrinterInfo == null) {
            throw Exception("プリンターが見つかりません。電源が入っているか確認してください。")
        }

        Log.d(TAG, "Discovery complete: ${foundPrinterInfo}")
        return foundPrinterInfo!!
    }

    /**
     * Bitmap をプリンターに印刷する。
     * この呼び出しはブロッキング（印刷完了またはエラーまで待機）。
     */
    @Throws(Exception::class)
    fun print(bitmap: Bitmap, tapeWidthMm: Int) {
        Log.d(TAG, "print() called: ${bitmap.width}×${bitmap.height} dots, tape=${tapeWidthMm}mm")

        // 1. プリンターを検出（自動ペアリング含む）
        val printerInfo = discoverPrinter()

        // 2. Bluetooth: host が空の場合、Serial Number(MACアドレス)をhostにコピー
        val mutableInfo = HashMap(printerInfo)
        val host = mutableInfo[TepraPrintDiscoverPrinter.PRINTER_INFO_HOST] ?: ""
        val serial = mutableInfo[TepraPrintDiscoverPrinter.PRINTER_INFO_SERIAL_NUMBER] ?: ""
        if (host.isBlank() && serial.isNotBlank()) {
            mutableInfo[TepraPrintDiscoverPrinter.PRINTER_INFO_HOST] = serial
            Log.d(TAG, "Fixed: set host=$serial (was empty)")
        }

        // 3. Bluetoothスタックを温める（SDK接続成功率を上げるため）
        warmUpBluetooth(serial.ifBlank { address })

        // 4. コールバック完了を待つための仕組み
        val latch = CountDownLatch(1)
        lastError = null

        // コールバックを設定
        tepraPrint.setCallback(object : TepraPrintCallback {
            override fun onChangePrintOperationPhase(tp: TepraPrint, phase: Int) {
                val phaseName = when (phase) {
                    TepraPrintPrintingPhase.Prepare -> "Prepare"
                    TepraPrintPrintingPhase.Processing -> "Processing"
                    TepraPrintPrintingPhase.WaitingForPrint -> "WaitingForPrint"
                    TepraPrintPrintingPhase.Complete -> "Complete"
                    else -> "Unknown($phase)"
                }
                Log.d(TAG, "Phase: $phaseName ($phase)")

                if (phase == TepraPrintPrintingPhase.Complete) {
                    Log.d(TAG, "Print operation completed")
                    latch.countDown()
                }
            }

            override fun onSuspendPrintOperation(tp: TepraPrint, phase: Int, error: Int) {
                Log.w(TAG, "Print suspended: phase=$phase, error=$error")
                lastError = "印刷が中断されました (phase=$phase, error=$error)"
                latch.countDown()
            }

            override fun onAbortPrintOperation(tp: TepraPrint, phase: Int, error: Int) {
                Log.e(TAG, "Print aborted: phase=$phase, error=$error")
                lastError = "印刷が中止されました (phase=$phase, error=$error)"
                latch.countDown()
            }

            override fun onChangeTapeFeedOperationPhase(tp: TepraPrint, phase: Int) {
                Log.d(TAG, "Tape feed phase: $phase")
            }

            override fun onAbortTapeFeedOperation(tp: TepraPrint, phase: Int, error: Int) {
                Log.e(TAG, "Tape feed aborted: phase=$phase, error=$error")
            }
        })

        // 3. 検出したプリンター情報を設定
        tepraPrint.setPrinterInformation(mutableInfo)
        Log.d(TAG, "Printer info set: host=${mutableInfo[TepraPrintDiscoverPrinter.PRINTER_INFO_HOST]}, type=${mutableInfo[TepraPrintDiscoverPrinter.PRINTER_INFO_TYPE]}")

        // 4. プリンターステータスを取得（失敗しても印刷を試みる）
        var tapeWidth = tapeWidthMmToSdkValue(tapeWidthMm)
        try {
            val tepraStatus = tepraPrint.fetchPrinterStatus()
            Log.d(TAG, "Printer status: $tepraStatus")

            if (tepraStatus != null && tepraStatus.isNotEmpty()) {
                val deviceError = tepraPrint.getDeviceErrorFromStatus(tepraStatus)
                Log.d(TAG, "Device error: $deviceError")

                if (deviceError == TepraPrintStatusError.NoError) {
                    val detectedWidth = tepraPrint.getTapeWidthFromStatus(tepraStatus)
                    if (detectedWidth > 0) {
                        tapeWidth = detectedWidth
                    }
                    Log.d(TAG, "Tape width from printer: $detectedWidth")
                } else {
                    Log.w(TAG, "Status error=$deviceError, will try printing anyway")
                }
            } else {
                Log.w(TAG, "Empty status, will try printing anyway")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPrinterStatus failed: ${e.message}, will try printing anyway")
        }

        // デバッグ: Bitmapをファイルに保存（印刷内容の確認用）
        saveBitmapForDebug(bitmap)

        // 5. 印刷パラメータを設定
        val printParameter = HashMap<String, Any>()
        printParameter[TepraPrintParameterKey.Copies] = 1
        printParameter[TepraPrintParameterKey.TapeCut] = TepraPrintTapeCut.EachLabel
        printParameter[TepraPrintParameterKey.HalfCut] = false
        printParameter[TepraPrintParameterKey.PrintSpeed] = TepraPrintPrintSpeed.PrintSpeedHigh
        printParameter[TepraPrintParameterKey.Density] = 0
        printParameter[TepraPrintParameterKey.TapeWidth] = tapeWidth
        printParameter[TepraPrintParameterKey.PriorityPrintSetting] = false
        printParameter[TepraPrintParameterKey.HalfCutContinuous] = false
        Log.d(TAG, "Print parameters set (tapeWidth=$tapeWidth), starting doPrint...")

        // 7. 印刷実行
        tepraPrint.doPrint(bitmap, printParameter)

        // 8. 完了を待機（最大60秒）
        val completed = latch.await(60, TimeUnit.SECONDS)
        if (!completed) {
            throw Exception("印刷がタイムアウトしました（60秒）")
        }
        if (lastError != null) {
            throw Exception(lastError)
        }

        Log.d(TAG, "print() completed successfully")
    }

    /**
     * テープ幅(mm) → SDK定数値に変換
     */
    private fun tapeWidthMmToSdkValue(widthMm: Int): Int = when (widthMm) {
        4  -> 1   // Normal_4mm
        6  -> 2   // Normal_6mm
        9  -> 3   // Normal_9mm
        12 -> 4   // Normal_12mm
        18 -> 5   // Normal_18mm
        24 -> 6   // Normal_24mm
        36 -> 7   // Normal_36mm
        50 -> 10  // Normal_50mm
        else -> 5 // デフォルト 18mm
    }

    /**
     * デバッグ用: Bitmapをファイルに保存して印刷内容を確認できるようにする。
     * 保存先: /sdcard/Android/data/com.reagent.tepraprint/files/debug_label.png
     */
    private fun saveBitmapForDebug(bitmap: Bitmap) {
        try {
            val dir = context.getExternalFilesDir(null) ?: return
            val file = File(dir, "debug_label.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Debug bitmap saved: ${file.absolutePath} (${bitmap.width}×${bitmap.height})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save debug bitmap: ${e.message}")
        }
    }
}
