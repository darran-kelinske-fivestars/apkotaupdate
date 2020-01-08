package com.fivestars.apkotaupdate


import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.work.*
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.fivestars.apkotaupdate.util.ApkUtil
import com.fivestars.apkotaupdate.util.S3Util
import com.fivestars.apkotaupdate.data.S3Status
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext



class UpgradeApkJob(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) , CoroutineScope {

    private val credentialsProvider = StaticCredentialsProvider(AnonymousAWSCredentials())
    private val region: Region = Region.getRegion(Regions.US_EAST_1)
    private val s3Client = AmazonS3Client(credentialsProvider, region, ClientConfiguration())

    private val job: Job = Job()

    override fun doWork(): Result {

        val apkConfigurations = ConfigurationUtil.getApkConfiguration(applicationContext)
        val apkDetails = apkConfigurations[0]

        return runBlocking {

            val installedApkModifiedDate = getInstalledApkModifiedDate()
            val latestApkModifiedDate = S3Util.getLatestApkFileDate(s3Client, apkDetails)

            if (installedApkModifiedDate != latestApkModifiedDate) {
                val channel: Channel<S3Status> = Channel()
                launch(Dispatchers.IO) {
                    S3Util.downloadApk(applicationContext, s3Client, apkDetails, channel)
                }

                channel.consumeEach {
                    when (it) {
                        is S3Status.Progress -> Log.i(TAG, "APK download progress: $it.progress")
                        is S3Status.Success -> {
                            setInstalledApkModifiedDate(latestApkModifiedDate)
                            when (installDownloadedApk(it.apkFile)) {
                                is com.fivestars.apkotaupdate.data.Result.Success -> Log.i(TAG, "APK successfully installed.")
                                is com.fivestars.apkotaupdate.data.Result.Error -> {
                                    setInstalledApkModifiedDate(installedApkModifiedDate)
                                    Log.e(TAG, "Unable to install APK")
                                }
                            }
                            channel.close()
                        }
                        is S3Status.Error -> {
                            Log.e(TAG, it.error)
                            channel.close()
                        }
                    }
                }
            }
            return@runBlocking Result.success()
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private fun getInstalledApkModifiedDate(): Long {
        return applicationContext.getSharedPreferences(PREFERENCES_UPGRADE, MODE_PRIVATE).getLong(KEY_APK_MODIFIED_DATE, 0)
    }

    @SuppressLint("ApplySharedPref")
    private fun setInstalledApkModifiedDate(modifiedDate: Long) {
        applicationContext.getSharedPreferences(PREFERENCES_UPGRADE, MODE_PRIVATE).edit().putLong(KEY_APK_MODIFIED_DATE, modifiedDate).commit()
    }

    private suspend fun installDownloadedApk(apkFile: File): com.fivestars.apkotaupdate.data.Result<String> {
        return withContext(Dispatchers.IO) {
            when (val installResult = ApkUtil.installApk(apkFile.absolutePath)) {
                is com.fivestars.apkotaupdate.data.Result.Success -> {
                    apkFile.delete()
                    return@withContext installResult
                }
                is com.fivestars.apkotaupdate.data.Result.Error ->
                    return@withContext com.fivestars.apkotaupdate.data.Result.Error(installResult.exception)
            }

        }
    }

    companion object {
        const val TAG = "upgrade_apk_job"
        const val PREFERENCES_UPGRADE = "upgrade"
        const val KEY_APK_MODIFIED_DATE = "apk_modified_date"
        private const val UPDATE_INTERVAL: Long = 1 // hourly for testing

        fun scheduleJob(context: Context) {

            val constraints = Constraints.Builder()
                    .setRequiresDeviceIdle(true)
                    .setRequiresCharging(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val work = PeriodicWorkRequest.Builder(UpgradeApkJob::class.java, UPDATE_INTERVAL, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueue(work)
        }
    }
}