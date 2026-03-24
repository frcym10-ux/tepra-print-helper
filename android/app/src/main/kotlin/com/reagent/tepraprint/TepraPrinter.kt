package com.reagent.tepraprint

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * TEPRA SR5500P への Bluetooth Classic SPP 接続・印刷を担当するクラス。
 *
 * プロトコル: TEPRA ESC/P ラスター形式
 *   1. ESC @        (0x1B 0x40)           — 初期化
 *   2. ESC i m      (0x1B 0x69 0x6D ..)   — テープ幅設定
 *   3. ラスター行    (0x67 0x00 [n] [data]) — 1行ずつ送信
 *   4. FF           (0x0C)                — 印刷・テープ排出
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
        // まず標準の SPP UUID で接続を試みる
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            socket!!.connect()
        } catch (e: IOException) {
            // Android では "read failed, socket might closed" が出ることがある。
            // UUID 経由が失敗した場合はリフレクションで channel 1 を直接指定して再試行する。
            Log.w(TAG, "SPP UUID connect failed (${e.message}), retrying via channel 1 reflection")
            socket?.close()
            @Suppress("DiscouragedPrivateApi")
            socket = device.javaClass
                .getMethod("createRfcommSocket", Int::class.java)
                .invoke(device, 1) as BluetoothSocket
            socket!!.connect()
        }
        outputStream = socket!!.outputStream
        Log.d(TAG, "Connected to ${device.name} (${device.address})")
    }

    @Throws(IOException::class)
    fun print(bitmap: Bitmap, tapeWidthMm: Int) {
        val stream = outputStream ?: throw IOException("Not connected to printer")
        sendTepraRaster(stream, bitmap, tapeWidthMm)
        stream.flush()
        Log.d(TAG, "Print data sent (${bitmap.width}×${bitmap.height} dots, tape=${tapeWidthMm}mm)")
    }

    private fun sendTepraRaster(stream: OutputStream, bitmap: Bitmap, tapeWidthMm: Int) {
        val dotsPerRow = TepraLabelRenderer.mmToDots(tapeWidthMm)
        val bytesPerRow = (dotsPerRow + 7) / 8

        // 1. 初期化（ESC @）
        stream.write(byteArrayOf(0x1B, 0x40))

        // 2. テープ幅設定（ESC i m [tapeCode] 0x00）
        val tapeCode = tapeWidthToCode(tapeWidthMm)
        stream.write(byteArrayOf(0x1B, 0x69.toByte(), 0x6D, tapeCode, 0x00))

        // 3. ラスターデータ（Bitmap の各行）
        for (y in 0 until bitmap.height) {
            val rowBytes = ByteArray(bytesPerRow)
            for (x in 0 until dotsPerRow.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                val luma = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (luma < 128) {
                    rowBytes[x / 8] = (rowBytes[x / 8].toInt() or (0x80 ushr (x % 8))).toByte()
                }
            }
            stream.write(byteArrayOf(0x67, 0x00, bytesPerRow.toByte()))
            stream.write(rowBytes)
        }

        // 4. 印刷・テープ排出（FF）
        stream.write(byteArrayOf(0x0C))
    }

    private fun tapeWidthToCode(widthMm: Int): Byte = when (widthMm) {
        4  -> 0x04
        6  -> 0x06
        9  -> 0x09
        12 -> 0x0C
        18 -> 0x12
        24 -> 0x18
        36 -> 0x24
        else -> 0x12
    }.toByte()

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
