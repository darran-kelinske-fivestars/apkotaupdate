package com.fivestars.apkotaupdate

import android.content.Context
import com.fivestars.apkotaupdate.model.ApkConfiguration
import com.google.gson.GsonBuilder

object ConfigurationUtil {

    const val CONFIGURATION_FILE = "upgrade_apk_configuration.json"

    val gson = GsonBuilder().create()

    fun getApkConfiguration(context: Context): List<ApkConfiguration> {
        val configurationFileContents = context.assets.open(CONFIGURATION_FILE).bufferedReader().use {
            it.readText()
        }
        return gson.fromJson(configurationFileContents, Array<ApkConfiguration>::class.java).toList()
    }
}