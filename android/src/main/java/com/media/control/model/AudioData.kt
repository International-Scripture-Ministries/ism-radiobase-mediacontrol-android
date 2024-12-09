package com.media.control.model

import com.google.gson.annotations.SerializedName

class AudioData(
    @SerializedName("url")
    var url: String,
    @SerializedName("image")
    var image: String,
    @SerializedName("title")
    var title: String,
    @SerializedName("pos")
    var pos: String,
    @SerializedName("speed")
    var speed: String
)
