package com.reagent.tepraprint

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class TepraPrinter(private val device: BluetoothDevice) {

    companion object {
        private const val TAG = "TepraPrinter"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @Throws(IOException::class)
    fun connect() {
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            socket!!.connect()
        } catch (e: IOException) {
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

        stream.write(byteArrayOf(0x1B, 0x40))

        val tapeCode = tapeWidthToCode(tapeWidthMm)
        stream.write(byteArrayOf(0x1B, 0x69.toByte(), 0x6D, tapeCode, 0x00))

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
