package com.reagent.tepraprint

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {

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
        val data = intent.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        if (data.scheme != "tepra-print") return

        Log.d(TAG, "tepra-print:// URL received: $data")

        val jsonParam = data.getQueryParameter("data")
        if (jsonParam == null) {
            Log.w(TAG, "Missing 'data' query parameter in URL: $data")
            return
        }

        // 印刷専用 Activity に委譲する
        startActivity(
            Intent(this, PrintStatusActivity::class.java).apply {
                putExtra(PrintStatusActivity.EXTRA_LABEL_JSON, jsonParam)
            }
        )
    }
}
