package com.reagent.tepraprint

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import jp.co.kingjim.tepraprint.sdk.TepraPrint
import jp.co.kingjim.tepraprint.sdk.TepraPrintCallback
import jp.co.kingjim.tepraprint.sdk.TepraPrintDiscoverPrinter
import jp.co.kingjim.tepraprint.sdk.TepraPrintParameterKey
import jp.co.kingjim.tepraprint.sdk.TepraPrintPrintingPhase
import jp.co.kingjim.tepraprint.sdk.TepraPrintStatusError
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * TEPRA SR5500P への Bluetooth 接続・印刷を担当するクラス。
 *
 * 公式 TEPRA Print SDK (TepraPrint.jar) を使用する。
 * Bluetooth アドレスとコンテキストを渡して生成し、connect() → print() → disconnect() の順で呼ぶ。
 */
class TepraPrinter(private val context: Context, private val address: String) {

    companion object {
        private const val TAG = "TepraPrinter"
        private const val PRINT_TIMEOUT_SEC = 60L
        /** Bluetooth Classic の Bonjour サービスタイプ (SDK 内部定数と一致) */
        private const val BT_SERVICE_TYPE = "_pdl-datastream._bluetooth."
    }

    private val tepraPrint = TepraPrint(context)

    /**
     * プリンター接続情報を SDK に設定する。
     * 実際の接続は [print] 呼び出し時に SDK が行う。
     */
    fun connect() {
        val printerInfo = mapOf(
            TepraPrintDiscoverPrinter.PRINTER_INFO_NAME to "TEPRA SR5500P",
            // SDK の DeviceConnectionBluetooth.open() は "Serial Number" キーで MAC アドレスを取得する。
            // PRINTER_INFO_HOST ("host") ではなく PRINTER_INFO_SERIAL_NUMBER ("Serial Number") を使う。
            TepraPrintDiscoverPrinter.PRINTER_INFO_SERIAL_NUMBER to address,
            TepraPrintDiscoverPrinter.PRINTER_INFO_TYPE to BT_SERVICE_TYPE,
        )
        tepraPrint.setPrinterInformation(printerInfo)
        Log.d(TAG, "Printer info configured for address: $address")
    }

    /**
     * ラベル Bitmap を SDK 経由でプリンターへ送信する。
     * [connect] の後に呼ぶこと。完了まで呼び出しスレッドをブロックする。
     *
     * @param bitmap       [TepraLabelRenderer.render] で生成したラベル Bitmap
     * @param tapeWidthMm  テープ幅（mm）。印刷パラメータとして SDK へ渡す。
     * @throws IOException 印刷エラーまたはタイムアウト時
     */
    @Throws(IOException::class)
    fun print(bitmap: Bitmap, tapeWidthMm: Int) {
        val latch = CountDownLatch(1)
        var printError = TepraPrintStatusError.NoError

        tepraPrint.setCallback(object : TepraPrintCallback {
            override fun onChangePrintOperationPhase(printer: TepraPrint, phase: Int) {
                Log.d(TAG, "printPhase=$phase")
                if (phase == TepraPrintPrintingPhase.Complete) {
                    latch.countDown()
                }
            }

            override fun onSuspendPrintOperation(printer: TepraPrint, phase: Int, error: Int) {
                Log.w(TAG, "printSuspended: phase=$phase error=$error")
            }

            override fun onAbortPrintOperation(printer: TepraPrint, phase: Int, error: Int) {
                Log.e(TAG, "printAborted: phase=$phase error=$error")
                printError = error
                latch.countDown()
            }

            override fun onChangeTapeFeedOperationPhase(printer: TepraPrint, phase: Int) {
                Log.d(TAG, "tapeFeedPhase=$phase")
            }

            override fun onAbortTapeFeedOperation(printer: TepraPrint, phase: Int, error: Int) {
                Log.e(TAG, "tapeFeedAborted: phase=$phase error=$error")
                latch.countDown()
            }
        })

        val options = mapOf<String, Any>(
            TepraPrintParameterKey.Copies to 1,
            TepraPrintParameterKey.TapeWidth to tapeWidthMm,
        )
        tepraPrint.doPrint(bitmap, options)

        if (!latch.await(PRINT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            throw IOException("印刷タイムアウト (${PRINT_TIMEOUT_SEC}秒)")
        }
        if (printError != TepraPrintStatusError.NoError) {
            throw IOException("印刷エラー (code=$printError)")
        }

        Log.d(TAG, "Print completed: ${bitmap.width}×${bitmap.height} dots, tape=${tapeWidthMm}mm")
    }

    /** SDK が接続ライフサイクルを管理するため、明示的な切断処理は不要。 */
    fun disconnect() {
        Log.d(TAG, "disconnect (managed by SDK)")
    }
}
