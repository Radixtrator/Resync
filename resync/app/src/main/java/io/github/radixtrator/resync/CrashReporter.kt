package io.github.radixtrator.resync

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Catches a crash anywhere in the app, saves its stack trace with the device and version, and shows it in
 * [CrashActivity] instead of the system's "app has a bug" dialog, so whoever hit it can copy or share it. The report
 * screen runs in its own process (":crash", see the manifest), which never builds the [AppContainer] that may be what
 * crashed.
 */
object CrashReporter {
    private const val PROCESS_SUFFIX = ":crash"
    private const val FILE = "last-crash.txt"

    /** Whether this is the report screen's process, where the app itself shouldn't start up. */
    fun isCrashProcess(app: Application): Boolean = processName(app).endsWith(PROCESS_SUFFIX)

    fun install(app: Application) {
        val fallback = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(app.filesDir, FILE).writeText(report(app, thread, error))
                app.startActivity(
                    Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                // Reporting failed too: let the system handle it as it would have.
                fallback?.uncaughtException(thread, error)
            }
        }
    }

    fun lastReport(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    private fun report(context: Context, thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        return buildString {
            appendLine("Resync ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
            val config = context.resources.configuration
            appendLine("Screen: ${config.screenWidthDp}x${config.screenHeightDp} dp, smallest ${config.smallestScreenWidthDp} dp")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
    }

    private fun processName(app: Application): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault("")
}

/** The crash report: the saved stack trace, with Copy, Share and Reopen. Plain views, so it needs none of the app. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.lastReport(this) ?: "No crash report was saved."
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Resync crashed"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        val hint = TextView(this).apply {
            text = "Please send this report to the developer (Share or Copy below). It says what went wrong."
            setPadding(0, pad / 2, 0, pad)
        }
        val trace = TextView(this).apply {
            text = report
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(trace) }

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(button("Copy") {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Resync crash", report))
                Toast.makeText(this@CrashActivity, "Copied", Toast.LENGTH_SHORT).show()
            })
            addView(button("Share") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Resync crash report")
                    .putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, "Share crash report"))
            })
            addView(button("Reopen") {
                startActivity(Intent(this@CrashActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(hint)
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        // Edge to edge is enforced on new Android versions: keep clear of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        setContentView(root)
    }
}
