package com.example.constructorar


import com.google.gson.annotations.SerializedName

data class InstructionModel(
    @SerializedName("augImages")
    val augImages: List<String>,
    @SerializedName("distance")
    val distance: Double,
    @SerializedName("modelName")
    val modelName: String,
    @SerializedName("tooltip")
    val tooltip: String
)