package com.reagent.tepraprint

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * TEPRA SR5500P への Bluetooth SPP 接続・印刷を担当するクラス。
 *
 * 現状は Android 標準の SPP（シリアルポートプロファイル）で接続し、
 * ラベルデータをテキスト形式で送信するプレースホルダー実装。
 *
 * KING JIM の TEPRA Android SDK（.aar）を libs/ に配置したら、
 * [sendPrintCommand] 内の TODO 箇所を SDK の API 呼び出しに差し替えてください。
 */
class TepraPrinter(private val device: BluetoothDevice) {

    companion object {
        private const val TAG = "TepraPrinter"
        /** Bluetooth Classic SPP の標準 UUID */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    /** プリンターへ接続する。失敗時は [IOException] をスローする。 */
    @Throws(IOException::class)
    fun connect() {
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket!!.connect()
        outputStream = socket!!.outputStream
        Log.d(TAG, "Connected to ${device.name} (${device.address})")
    }

    /** ラベルデータを送信して印刷を実行する。[connect] の後に呼ぶこと。 */
    @Throws(IOException::class)
    fun print(label: TepraLabel) {
        val stream = outputStream ?: throw IOException("Not connected to printer")
        sendPrintCommand(stream, label)
        stream.flush()
        Log.d(TAG, "Print command sent for control number: ${label.controlNumber}")
    }

    /**
     * プリンターへ送るバイト列を組み立てて書き込む。
     *
     * TODO: KING JIM TEPRA SDK (.aar) が入手できたら、以下のプレースホルダーを
     *       SDK の印刷 API に置き換えること。
     *
     * 置き換え例:
     *   val sdk = KingJimTepraManager(device)
     *   sdk.printLabel(label.toTepraSdkFormat())
     */
    private fun sendPrintCommand(stream: OutputStream, label: TepraLabel) {
        // ---- プレースホルダー: テキスト形式で送信（接続確認用）----
        val payload = buildString {
            appendLine("管理番号: ${label.controlNumber}")
            appendLine("試薬名  : ${label.reagentName}")
            appendLine("ロットNo: ${label.lotNumber}")
            appendLine("有効期限: ${label.expiryDate}")
            appendLine("数 量  : ${label.quantity} ${label.unit}")
        }
        stream.write(payload.toByteArray(Charsets.UTF_8))
        // ---- ここまでプレースホルダー ----
    }

    /** 接続を閉じる。例外は握りつぶしてログに残す。 */
    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
            Log.d(TAG, "Disconnected from ${device.name}")
        } catch (e: IOException) {
            Log.w(TAG, "Error closing connection: ${e.message}")
        }
    }
}
