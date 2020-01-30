package com.fivestars.apkotaupdate.util

import android.util.Log
import com.fivestars.apkotaupdate.data.Result
import java.io.*


object ApkUtil {

    private const val LOG_TAG = "ApkUtil"

    val libs = "LD_LIBRARY_PATH=/vendor/lib64:/system/lib64:/vendor/lib:/system/lib "

    @Throws(IOException::class, InterruptedException::class)
    private fun executeAsRoot(vararg commands: String): Int {
        // Do the magic
        val p = Runtime.getRuntime().exec("su")
        val es = p.errorStream
        val os = DataOutputStream(p.outputStream)

        try {
            for (command in commands) {
                os.writeBytes(command + "\n")
            }
            os.writeBytes("exit\n")
            os.flush()
        } finally {
            os.close()
        }

        var line: String?
        // we typically don't get any error output here so instantiating a default-sized StringBuilder
        val outputStringBuilder = StringBuilder()
        val errorReader = BufferedReader(InputStreamReader(es))
        line = errorReader.readLine()
        while (line != null) {
            outputStringBuilder.append(line)
            line = errorReader.readLine()
        }
        val output = outputStringBuilder.toString()
        p.waitFor()
        Log.d(LOG_TAG, output.trim { it <= ' ' } + " (" + p.exitValue() + ")")
        return p.exitValue()
    }

    fun installApk(filename: String): Result<String> {
        val file = File(filename)
        if (file.exists()) {
            Log.d(LOG_TAG, "Updating to new version with file: $filename")

            // Install the new apk over the old apk via "pm install -r"
            val command = libs + "chmod 777 ${file.absolutePath} && pm install -r " + file.absolutePath
            Log.d(LOG_TAG, "Executing command 1: $command")
            val result = executeAsRoot(command)
            if (result != 0) {
                Log.e(LOG_TAG, "Could not execute command $command as root")
                return Result.Error(Exception("Could not execute command $command as root"))
            } else {
                return Result.Success("Successfully installed $filename")
            }
        } else {
            val errorMessage = "New app apk file not found: $filename"
            Log.e(LOG_TAG, errorMessage)
            throw IOException(errorMessage)
        }
    }

    fun uninstallApk(filename: String): Result<String> {
        Log.d(LOG_TAG, "Uninstalling package: $filename")

        // Install the new apk over the old apk via "pm install -r"
        val command = libs + "pm uninstall " + filename
        Log.d(LOG_TAG, "Executing command 1: $command")

        val result = executeAsRoot(command)
        if (result != 0) {
            Log.e(LOG_TAG, "Could not execute command $command as root")
            return Result.Error(Exception("Could not execute command $command as root"))
        } else {
            return Result.Success("Successfully uninstalled $filename")
        }
    }
}
