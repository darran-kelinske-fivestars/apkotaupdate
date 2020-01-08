package com.fivestars.apkotaupdate.util

import android.content.Context
import android.util.Log
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.fivestars.apkotaupdate.model.ApkConfiguration
import com.fivestars.apkotaupdate.UpgradeApkJob.Companion.TAG
import com.fivestars.apkotaupdate.data.S3Status
import java.io.File
import kotlinx.coroutines.channels.SendChannel


object S3Util {

    fun getLatestApkFileDate(s3Client: AmazonS3Client, apkConfiguration: ApkConfiguration): Long {
        val objectListing =
                s3Client.listObjects(apkConfiguration.bucket, apkConfiguration.prefix)
        val s3ObjectSummary =
                objectListing.objectSummaries.sortedByDescending { it.lastModified }[0]

        return s3ObjectSummary.lastModified.time
    }

    fun downloadApk(s3Client: AmazonS3Client, context: Context, apkConfiguration: ApkConfiguration, channel: SendChannel<S3Status>) {
        val objectListing =
                s3Client.listObjects(apkConfiguration.bucket, apkConfiguration.prefix)
        val s3ObjectSummary =
                objectListing.objectSummaries.sortedByDescending { it.lastModified }[0]

        val apkObject = s3Client.getObject(s3ObjectSummary.bucketName, s3ObjectSummary.key)

        val apkFile = File.createTempFile(apkConfiguration.name, ".apk", context.cacheDir)

        val transferObserver = TransferUtility.builder().s3Client(s3Client).context(context).build()
                .download(
                        apkObject.bucketName,
                        apkObject.key,
                        apkFile
                )

        transferObserver.setTransferListener(object : TransferListener {
            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                channel.offer(S3Status.Progress("APK Download ${(100 * bytesCurrent / bytesTotal)}% complete."))
            }

            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.IN_PROGRESS) {
                    channel.offer(S3Status.Progress("Starting APK Download"))
                }
                if (state == TransferState.COMPLETED) {
                    channel.offer(S3Status.Success(apkFile))
                }
            }

            override fun onError(id: Int, ex: Exception?) {
                apkFile.delete()
                channel.offer(S3Status.Error(ex.toString()))
            }
        })
    }
}
