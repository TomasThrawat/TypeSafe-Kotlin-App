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

private const val DEFAULT_ENDPOINT = "https://typesafe-mcp-key-hyouka1.vercel.app"

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
                val answer = runCatching {
                    JSONObject(response).optJSONObject("answers")?.optJSONObject("q1")
                }.getOrNull()
                val visibleResult = when (type) {
                    "noul" -> {
                        val value = answer?.optDouble("noul", Double.NaN) ?: Double.NaN
                        if (!value.isNaN()) {
                            "Noul: ${String.format(java.util.Locale.US, "%.1f%%", value * 100.0)}"
                        } else {
                            "Noul: no value returned"
                        }
                    }
                    "score" -> {
                        val value = answer?.optDouble("score", Double.NaN) ?: Double.NaN
                        if (!value.isNaN()) {
                            "Score: $value"
                        } else {
                            "Score: no value returned"
                        }
                    }
                    "choice" -> {
                        val value = answer?.optString("choice").orEmpty()
                        if (value.isNotBlank()) {
                            "Choice: $value"
                        } else {
                            "Choice: no value returned"
                        }
                    }
                    else -> pretty(response)
                }
                resultView.text = visibleResult + "\n\nJSON:\n" + pretty(response)
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                setBusy(false)