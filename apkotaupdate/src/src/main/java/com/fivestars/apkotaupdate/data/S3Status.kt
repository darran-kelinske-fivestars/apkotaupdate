package com.fivestars.apkotaupdate.data

import java.io.File

sealed class S3Status {
    data class Progress(val progress: String) : S3Status()
    data class Success(val apkFile: File) : S3Status()
    data class Error(val error: String) : S3Status()
}