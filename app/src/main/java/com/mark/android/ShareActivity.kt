package com.mark.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File

/** "Share -> Mark": drops a file or text into the Mark inbox so you can say "summarize the PDF I just sent you". */
class ShareActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val inbox = File(PhoneTools.base(), "inbox").apply { mkdirs() }
        var saved = 0
        try {
            val uris = ArrayList<Uri>()
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            for (u in uris) {
                val name = contentResolver.query(u, null, null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                } ?: "file-${System.currentTimeMillis()}"
                contentResolver.openInputStream(u)?.use { i -> File(inbox, name).outputStream().use { o -> i.copyTo(o) } }; saved++
            }
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
                File(inbox, "note-${System.currentTimeMillis()}.txt").writeText(it); saved++
            }
        } catch (e: Exception) { Bus.log("Share failed: ${e.message}") }
        Toast.makeText(this, if (saved > 0) "Saved $saved item(s) to Mark's inbox" else "Nothing to save", Toast.LENGTH_SHORT).show()
        finish()
    }
}
