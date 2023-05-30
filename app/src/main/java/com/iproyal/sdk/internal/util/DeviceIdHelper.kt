package com.iproyal.sdk.internal.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.Exception
import java.util.*

internal object DeviceIdHelper {

    @Synchronized
    fun id(context: Context): String {
        val installation = File(context.filesDir, "INSTALLATION")
        return try {
            if (!installation.exists()) {
                writeInstallationFile(installation)
            }
            readInstallationFile(installation)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @Throws(IOException::class)
    private fun readInstallationFile(installation: File): String {
        val f = RandomAccessFile(installation, "r")
        val bytes = ByteArray(f.length().toInt())
        f.readFully(bytes)
        f.close()
        return String(bytes)
    }

    @Throws(IOException::class)
    private fun writeInstallationFile(installation: File) {
        val out = FileOutputStream(installation)
        val id: String = UUID.randomUUID().toString()
        out.write(id.toByteArray())
        out.close()
    }

}