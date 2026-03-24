package com.reagent.tepraprint

import org.json.JSONObject

data class TepraLabel(
    val controlNumber: String,
    val reagentName: String,
    val lotNumber: String,
    val expiryDate: String,
    val tapeWidthMm: Int = 18,
) {
    companion object {
        fun fromJson(json: JSONObject) = TepraLabel(
            controlNumber = json.optString("controlNumber", ""),
            reagentName   = json.optString("reagentName",   ""),
            lotNumber     = json.optString("lotNumber",     ""),
            expiryDate    = json.optString("expiryDate",    ""),
            tapeWidthMm   = json.optInt("tapeWidthMm", 18),
        )

        /** {"labels":[{...},{...}]} 形式の JSON から複数ラベルを解析する */
        fun fromJsonArray(json: JSONObject): List<TepraLabel> {
            val arr = json.optJSONArray("labels") ?: return emptyList()
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}
