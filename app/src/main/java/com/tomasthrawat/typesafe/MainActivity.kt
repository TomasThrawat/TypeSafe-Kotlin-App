package com.tomasthrawat.typesafe

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import android.util.Base64

private const val DEFAULT_ENDPOINT =
    "https://typesafe-mcp-key-hyouka1.vercel.app"
private const val PICK_FILES = 7001

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val api = TypeSafeApi()
    private val generation = AtomicLong(0L)

    private lateinit var endpointInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var messageInput: EditText
    private lateinit var chatView: TextView
    private lateinit var attachmentView: TextView
    private lateinit var statusView: TextView
    private lateinit var sendButton: Button
    private lateinit var attachButton: Button
    private lateinit var clearAttachmentButton: Button

    private val history = mutableListOf<ChatMessage>()
    private val pendingAttachments = mutableListOf<ChatAttachment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUi()
        loadSettings()
        checkHealth()
    }

    private fun createUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(textView("TypeSafe AI", 28f, true))
        root.addView(
            textView(
                "محادثة AI حقيقية مع دعم الصور والملفات وMCP",
                14f,
                false
            )
        )

        root.addView(section("الخادم"))

        endpointInput = editText(
            hint = "Vercel endpoint",
            value = DEFAULT_ENDPOINT,
            lines = 1
        )
        root.addView(endpointInput, fullParams())

        apiKeyInput = editText(
            hint = "OpenRouter API Key المجاني",
            value = "",
            lines = 1
        ).apply {
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(apiKeyInput, fullParams())

        root.addView(
            textView(
                "المفتاح يُرسل عبر HTTPS فقط ولا يوجد داخل APK.",
                11f,
                false
            )
        )

        root.addView(section("النموذج"))

        modelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "Auto: مجاني + fallback",
                    "Nemotron 3 Ultra (free)",
                    "Ling 3.0 Flash VL (free)"
                )
            )
        }
        root.addView(modelSpinner, fullParams())

        statusView = textView("جاري فحص الخادم...", 12f, false)
        root.addView(statusView, fullParams())

        root.addView(section("المحادثة"))

        chatView = textView(
            "",
            15f,
            false
        ).apply {
            hint = "اكتب أي شيء: سؤال، كود، فكرة لعبة، أو طلب تحليل."
            setBackgroundColor(Color.rgb(18, 18, 18))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        root.addView(
            chatView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(360)
            )
        )

        root.addView(section("المرفقات"))

        attachmentView = textView("لا توجد مرفقات", 12f, false)
        root.addView(attachmentView, fullParams())

        val attachmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        attachButton = Button(this).apply {
            text = "ملف / صورة"
            setOnClickListener { pickFiles() }
        }

        clearAttachmentButton = Button(this).apply {
            text = "مسح المرفقات"
            setOnClickListener {
                pendingAttachments.clear()
                refreshAttachments()
            }
        }

        attachmentRow.addView(attachButton, weightParams(1f))
        attachmentRow.addView(
            clearAttachmentButton,
            weightParams(1f)
        )
        root.addView(attachmentRow, fullParams())

        root.addView(section("الرسالة"))

        messageInput = editText(
            hint = "اكتب رسالتك...",
            value = "",
            lines = 5
        )
        root.addView(messageInput, fullParams())

        sendButton = Button(this).apply {
            text = "إرسال إلى AI"
            setOnClickListener { sendMessage() }
        }
        root.addView(sendButton, fullParams())

        val healthButton = Button(this).apply {
            text = "فحص الخادم"
            setOnClickListener { checkHealth() }
        }
        root.addView(healthButton, fullParams())

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()

        if (message.isEmpty() && pendingAttachments.isEmpty()) {
            toast("اكتب رسالة أو أضف ملفًا.")
            return
        }

        saveSettings()

        val userText = message.ifEmpty {
            "Please analyze the attached files or images."
        }

        history.add(ChatMessage("user", userText))
        appendChat("أنت", userText)

        val currentAttachments = pendingAttachments.toList()

        val selectedModel = when (modelSpinner.selectedItemPosition) {
            1 -> "nvidia/nemotron-3-ultra-550b-a55b:free"
            2 -> "inclusionai/ling-3.0-flash-vl:free"
            else -> "auto"
        }

        val currentGeneration = generation.incrementAndGet()
        setBusy(true)
        statusView.text = "AI يعمل..."

        executor.execute {
            val result = api.chat(
                endpoint = endpointInput.text.toString(),
                openRouterApiKey = apiKeyInput.text.toString(),
                messages = history.toList(),
                attachments = currentAttachments,
                model = selectedModel
            )

            runOnUiThread {
                if (currentGeneration != generation.get()) {
                    return@runOnUiThread
                }

                result.onSuccess { answer ->
                    history.add(ChatMessage("assistant", answer))
                    appendChat("AI", answer)
                    messageInput.text.clear()
                    pendingAttachments.clear()
                    refreshAttachments()
                    statusView.text = "جاهز"
                }.onFailure { error ->
                    statusView.text = "فشل الطلب"
                    appendChat(
                        "خطأ",
                        error.message ?: "Unknown error"
                    )
                }

                setBusy(false)
            }
        }
    }

    private fun checkHealth() {
        val endpoint = endpointInput.text.toString().trim()
        val apiKey = apiKeyInput.text.toString().trim()

        if (endpoint.isEmpty()) {
            statusView.text = "أدخل عنوان الخادم"
            return
        }

        saveSettings()

        val currentGeneration = generation.incrementAndGet()
        statusView.text = "جاري الفحص..."

        executor.execute {
            val result = api.health(endpoint, apiKey)

            runOnUiThread {
                if (currentGeneration != generation.get()) {
                    return@runOnUiThread
                }

                result.onSuccess { response ->
                    val json = runCatching {
                        org.json.JSONObject(response)
                    }.getOrNull()

                    val keyReceived = json
                        ?.optBoolean("openRouterApiKeyProvided", false)
                        ?: false

                    statusView.text = if (keyReceived) {
                        "الخادم يعمل • المفتاح وصل"
                    } else {
                        "الخادم يعمل • المفتاح لم يصل"
                    }
                }.onFailure { error ->
                    statusView.text =
                        "تعذر الوصول للخادم: ${describeNetworkError(error)}"
                }
            }
        }
    }

    private fun pickFiles() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            },
            PICK_FILES
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode != PICK_FILES ||
            resultCode != RESULT_OK ||
            data == null
        ) {
            return
        }

        val uris = mutableListOf<Uri>()

        data.data?.let { uris.add(it) }

        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                uris.add(clip.getItemAt(index).uri)
            }
        }

        for (uri in uris.distinct().take(4)) {
            runCatching { readAttachment(uri) }
                .onSuccess { attachment ->
                    pendingAttachments.removeAll {
                        it.name == attachment.name
                    }
                    pendingAttachments.add(attachment)
                }
                .onFailure {
                    toast("تعذر قراءة أحد المرفقات.")
                }
        }

        refreshAttachments()
    }

    private fun readAttachment(uri: Uri): ChatAttachment {
        val name = queryDisplayName(uri) ?: "attachment"
        val mimeType =
            contentResolver.getType(uri) ?: "application/octet-stream"

        if (mimeType.startsWith("image/")) {
            val imageDataUrl = encodeImage(uri)

            return ChatAttachment(
                name = name,
                mimeType = "image/jpeg",
                sizeBytes = imageDataUrl.length.toLong(),
                imageDataUrl = imageDataUrl
            )
        }

        val lowerName = name.lowercase()

        val textLike =
            mimeType.startsWith("text/") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".json") ||
                lowerName.endsWith(".kt") ||
                lowerName.endsWith(".java") ||
                lowerName.endsWith(".xml") ||
                lowerName.endsWith(".gradle") ||
                lowerName.endsWith(".kts") ||
                lowerName.endsWith(".js") ||
                lowerName.endsWith(".ts") ||
                lowerName.endsWith(".py") ||
                lowerName.endsWith(".c") ||
                lowerName.endsWith(".cpp") ||
                lowerName.endsWith(".h") ||
                lowerName.endsWith(".html") ||
                lowerName.endsWith(".css") ||
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".log")

        val textContent =
            if (textLike) {
                contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                        .take(1_000_000)
                        .toByteArray()
                        .toString(Charsets.UTF_8)
                }
            } else {
                null
            }

        val sizeBytes =
            contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length.takeIf { length -> length >= 0L } ?: 0L
            } ?: 0L

        return ChatAttachment(
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            textContent = textContent
        )
    }

    private fun encodeImage(uri: Uri): String {
        val bitmap = contentResolver.openInputStream(uri).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input)) {
                "Invalid image"
            }
        }

        val maxSide = 1280
        val scale = minOf(
            1f,
            maxSide.toFloat() / bitmap.width.toFloat(),
            maxSide.toFloat() / bitmap.height.toFloat()
        )

        val resized =
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

        val output = ByteArrayOutputStream()

        resized.compress(
            Bitmap.CompressFormat.JPEG,
            82,
            output
        )

        if (resized !== bitmap) resized.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()

        val bytes = output.toByteArray()

        require(bytes.size <= 3_000_000) {
            "Image is too large"
        }

        return "data:image/jpeg;base64," +
            Base64.encodeToString(
                bytes,
                Base64.NO_WRAP
            )
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)
        }

        return uri.lastPathSegment
    }

    private fun refreshAttachments() {
        attachmentView.text =
            if (pendingAttachments.isEmpty()) {
                "لا توجد مرفقات"
            } else {
                pendingAttachments.joinToString("\\n") {
                    "• \${it.name} (\${it.mimeType})"
                }
            }
    }

    private fun appendChat(role: String, message: String) {
        if (chatView.text.toString().isBlank()) {
            chatView.text = ""
        }

        chatView.append(
            "\\n$role:\\n$message\\n"
        )
    }

    private fun loadSettings() {
        val prefs =
            getSharedPreferences("typesafe_settings", MODE_PRIVATE)

        endpointInput.setText(
            prefs.getString("endpoint", DEFAULT_ENDPOINT)
        )

        apiKeyInput.setText(
            prefs.getString("openrouter_key", "")
        )
    }

    private fun saveSettings() {
        getSharedPreferences(
            "typesafe_settings",
            MODE_PRIVATE
        )
            .edit()
            .putString(
                "endpoint",
                endpointInput.text.toString().trim()
            )
            .putString(
                "openrouter_key",
                apiKeyInput.text.toString().trim()
            )
            .apply()
    }

    private fun setBusy(busy: Boolean) {
        sendButton.isEnabled = !busy
        attachButton.isEnabled = !busy
        clearAttachmentButton.isEnabled = !busy
    }

    private fun section(label: String): TextView =
        textView(label, 18f, true).apply {
            setPadding(0, dp(18), 0, dp(8))
        }

    private fun textView(
        value: String,
        size: Float,
        bold: Boolean
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.RIGHT

            if (bold) {
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

            setPadding(0, dp(4), 0, dp(4))
        }

    private fun editText(
        hint: String,
        value: String,
        lines: Int
    ): EditText =
        EditText(this).apply {
            setHint(hint)
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(24, 24, 24))
            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )
            minLines = lines
            maxLines = lines
            gravity = Gravity.TOP or Gravity.RIGHT
        }

    private fun fullParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(4), 0, dp(4))
        }

    private fun weightParams(
        weight: Float
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            weight
        ).apply {
            setMargins(
                dp(2),
                0,
                dp(2),
                0
            )
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun describeNetworkError(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()

        return when (root) {
            is java.net.UnknownHostException ->
                "تعذر حل اسم الخادم"
            is java.net.ConnectException ->
                "تعذر فتح الاتصال"
            is java.net.SocketTimeoutException ->
                "انتهت مهلة الاتصال"
            is javax.net.ssl.SSLException ->
                "فشل اتصال TLS"
            else ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: root.javaClass.simpleName
        }.take(220)
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        executor.shutdownNow()
        super.onDestroy()
    }
}
