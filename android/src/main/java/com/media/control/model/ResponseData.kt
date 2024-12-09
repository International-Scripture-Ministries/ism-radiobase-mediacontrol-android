package com.media.control.model

import com.google.gson.annotations.SerializedName

class ResponseData(
    @SerializedName("uuid")
    var uuid: String,
    @SerializedName("url")
    var url: String,
    @SerializedName("state")
    var state: String,
    @SerializedName("duration")
    var duration: String,
    @SerializedName("position")
    var position: String,
    @SerializedName("date")
    var date: String
)
