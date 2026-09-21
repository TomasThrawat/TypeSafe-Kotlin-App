package com.tomasthrawat.typesafe

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_ENDPOINT = "https://typesafe-mcp-key.vercel.app"

class MainActivity : Activity() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var statusView: TextView
    private lateinit var resultView: TextView
    private lateinit var endpointInput: EditText
    private lateinit var stateInput: EditText
    private lateinit var questionInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var levelsInput: EditText
    private lateinit var askButton: Button
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUi()
        testHealth()
    }

    private fun createUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
            textDirection = android.view.View.TEXT_DIRECTION_RTL
        }
        scroll.addView(root)

        title(root, "TypeSafe")
        subtitle(root, "تطبيق Android Native بـ Kotlin")

        sectionTitle(root, "الاتصال")
        endpointInput = edit("عنوان الخادم", DEFAULT_ENDPOINT, 1)
        root.addView(endpointInput, fullParams())

        testButton = Button(this).apply { text = "اختبار الاتصال" }
        root.addView(testButton, fullParams())

        statusView = text("جاري الاتصال...")
        root.addView(statusView, fullParams())

        sectionTitle(root, "System One")
        stateInput = edit("السياق / الحالة", "", 4)
        root.addView(stateInput, fullParams())

        questionInput = edit(
            "السؤال",
            "هل هذه المعلومة مدعومة بالأدلة المتاحة؟",
            4
        )
        root.addView(questionInput, fullParams())

        typeSpinner = Spinner(this)
        val types = listOf("noul", "score", "choice")
        typeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            types
        )
        root.addView(typeSpinner, fullParams())

        levelsInput = edit("المستويات، كل مستوى في سطر", "ضعيف\nمتوسط\nقوي", 3)
        levelsInput.visibility = android.view.View.GONE
        root.addView(levelsInput, fullParams())

        typeSpinner.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    levelsInput.visibility =
                        if (types[position] == "score") android.view.View.VISIBLE
                        else android.view.View.GONE
                }
            }
        )

        askButton = Button(this).apply { text = "اسأل TypeSafe" }
        root.addView(askButton, fullParams())

        sectionTitle(root, "النتيجة")
        resultView = text("لا توجد نتيجة بعد.")
        root.addView(resultView, fullParams())

        testButton.setOnClickListener { testHealth() }
        askButton.setOnClickListener { askTypeSafe() }

        setContentView(scroll)
    }

    private fun testHealth() {
        setBusy(true)
        statusView.text = "جاري اختبار الخادم..."
        executor.execute {
            val message = runCatching {
                val raw = request(
                    endpointInput.text.toString().trim().removeSuffix("/") + "/api/health",
                    "GET",
                    null
                )
                val json = JSONObject(raw)
                if (json.optBoolean("ok")) "متصل بـ TypeSafe MCP"
                else "الخادم رد، لكن حالة TypeSafe غير مؤكدة"
            }.getOrElse {
                "فشل الاتصال: " + (it.message ?: "خطأ غير معروف")
            }

            mainHandler.post {
                statusView.text = message
                setBusy(false)
            }
        }
    }

    private fun askTypeSafe() {
        val question = questionInput.text.toString().trim()
        if (question.isEmpty()) return

        setBusy(true)
        resultView.text = "جاري إرسال الطلب..."

        val endpoint = endpointInput.text.toString().trim().removeSuffix("/")
        val state = stateInput.text.toString()
        val type = typeSpinner.selectedItem.toString()
        val levels = levelsInput.text.toString()

        executor.execute {
            val response = runCatching {
                val body = JSONObject()
                    .put("state", state)
                    .put(
                        "questions",
                        JSONObject().put(
                            "q1",
                            JSONObject()
                                .put("type", type)
                                .put("instructions", question)
                                .also { item ->
                                    when (type) {
                                        "score" -> {
                                            val arr = JSONArray()
                                            levels.lines()
                                                .filter { it.isNotBlank() }
                                                .forEach(arr::put)
                                            item.put("levels", arr)
                                        }
                                        "choice" -> item.put("criteria", JSONObject())
                                    }
                                }
                        )
                    )

                request(
                    endpoint + "/api/ask",
                    "POST",
                    body.toString()
                )
            }.fold(
                onSuccess = { pretty(it) },
                onFailure = {
                    "خطأ: " + (it.message ?: "خطأ غير معروف")
                }
            )

            mainHandler.post {
                resultView.text = response
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        mainHandler.post {
            testButton.isEnabled = !busy
            askButton.isEnabled = !busy
        }
    }

    private fun request(url: String, method: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                throw IOException("HTTP " + code + ": " + text)
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun pretty(raw: String): String =
        runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }

    private fun title(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
        }, fullParams())
    }

    private fun subtitle(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.RIGHT
        }, fullParams())
    }

    private fun sectionTitle(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
            setPadding(0, dp(18), 0, dp(6))
        }, fullParams())
    }

    private fun text(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(Color.WHITE)
        gravity = Gravity.RIGHT
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun edit(hint: String, value: String, lines: Int): EditText =
        EditText(this).apply {
            setHint(hint)
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(28, 28, 28))
            gravity = Gravity.TOP or Gravity.RIGHT
            minLines = lines
            maxLines = maxOf(lines, 6)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

    private fun fullParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(4), 0, dp(4))
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
