package com.fivestars.apkotaupdate.model

import com.google.gson.annotations.SerializedName

data class ApkConfiguration(val name: String, val bucket: String, val prefix: String, @SerializedName("package") val packageName: String)