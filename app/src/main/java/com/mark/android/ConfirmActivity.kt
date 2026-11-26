package com.mark.android

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** The model cannot confirm its own irreversible actions: you press the button. */
class ConfirmActivity : Activity() {
    private var answered = false
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Bus.dialog = this
        setFinishOnTouchOutside(false)
        AlertDialog.Builder(this)
            .setTitle("Mark asks")
            .setMessage(intent.getStringExtra("msg") ?: "Continue?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ -> answer(true) }
            .setNegativeButton("No") { _, _ -> answer(false) }
            .show()
    }
    private fun answer(v: Boolean) { answered = true; Bus.pending?.complete(v); finish() }
    override fun onDestroy() { if (Bus.dialog === this) Bus.dialog = null; if (!answered) Bus.pending?.complete(false); super.onDestroy() }
}
