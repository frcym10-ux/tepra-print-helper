package com.reagent.tepraprint

import org.json.JSONObject

data class TepraLabel(
    val controlNumber: String,
    val reagentName: String,
    val lotNumber: String,
    val expiryDate: String,
    val quantity: String,
    val unit: String
) {
    companion object {
        fun fromJson(json: JSONObject) = TepraLabel(
            controlNumber = json.optString("controlNumber", ""),
            reagentName   = json.optString("reagentName",   ""),
            lotNumber     = json.optString("lotNumber",     ""),
            expiryDate    = json.optString("expiryDate",    ""),
            quantity      = json.optString("quantity",      ""),
            unit          = json.optString("unit",          "")
        )
    }
}
