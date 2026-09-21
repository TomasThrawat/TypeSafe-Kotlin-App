package com.tomasthrawat.typesafe

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatContainer: LinearLayout
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
            clipToPadding = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        header.addView(textView("TypeSafe AI", 30f, true))
        header.addView(
            textView(
                "محادثة AI سريعة مع الصور والملفات وMCP",
                13f,
                false
            )
        )
        root.addView(header, fullParams())

        root.addView(section("الخادم"))
        val serverCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        endpointInput = editText(
            hint = "Vercel endpoint",
            value = DEFAULT_ENDPOINT,
            lines = 1
        )
        serverCard.addView(endpointInput, fullParams())

        apiKeyInput = editText(
            hint = "OpenRouter API Key",
            value = "",
            lines = 1
        ).apply {
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        serverCard.addView(apiKeyInput, fullParams())
        serverCard.addView(
            textView(
                "المفتاح محفوظ محليًا ويُرسل عبر HTTPS فقط.",
                10.5f,
                false
            )
        )
        root.addView(serverCard, fullParams())

        root.addView(section("النموذج"))
        val modelCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
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
            background = roundedBackground(Color.rgb(28, 28, 28), Color.rgb(58, 58, 58), 12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        modelCard.addView(modelSpinner, fullParams())
        statusView = textView("جاري فحص الخادم...", 12f, false).apply {
            setPadding(dp(4), dp(8), dp(4), 0)
            setTextColor(Color.LTGRAY)
        }
        modelCard.addView(statusView, fullParams())
        root.addView(modelCard, fullParams())

        root.addView(section("المحادثة"))
        val chatCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        chatScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
        }
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        chatScrollView.addView(
            chatContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        chatCard.addView(
            chatScrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(380)
            )
        )
        root.addView(chatCard, fullParams())

        root.addView(section("المرفقات"))
        val attachmentCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        attachmentView = textView("لا توجد مرفقات", 12f, false)
        attachmentCard.addView(attachmentView, fullParams())

        val attachmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        attachButton = actionButton("إضافة ملف / صورة") { pickFiles() }
        clearAttachmentButton = actionButton("مسح المرفقات") {
            pendingAttachments.clear()
            refreshAttachments()
        }
        attachmentRow.addView(attachButton, weightParams(1f))
        attachmentRow.addView(clearAttachmentButton, weightParams(1f))
        attachmentCard.addView(attachmentRow, fullParams())
        root.addView(attachmentCard, fullParams())

        root.addView(section("رسالتك"))
        val composerCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        messageInput = editText(
            hint = "اكتب رسالتك...",
            value = "",
            lines = 5
        )
        composerCard.addView(messageInput, fullParams())

        sendButton = actionButton("إرسال") { sendMessage() }.apply {
            textSize = 15f
        }
        composerCard.addView(sendButton, fullParams())

        val healthButton = actionButton("فحص الخادم") { checkHealth() }
        composerCard.addView(healthButton, fullParams())
        root.addView(composerCard, fullParams())

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
        val isUser = role == "أنت"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(10), dp(9))
            background = roundedBackground(
                if (isUser) Color.rgb(42, 42, 42) else Color.rgb(22, 22, 22),
                Color.rgb(58, 58, 58),
                18
            )
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        val label = TextView(this).apply {
            text = if (isUser) "أنت" else "AI"
            textSize = 11f
            setTextColor(if (isUser) Color.LTGRAY else Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            label,
            LinearLayout.LayoutParams(0, dp(26), 1f)
        )

        if (!isUser) {
            val copyButton = TextView(this).apply {
                text = "نسخ"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBackground(
                    Color.rgb(34, 34, 34),
                    Color.rgb(70, 70, 70),
                    10
                )
                setPadding(dp(10), 0, dp(10), 0)
                minWidth = dp(54)
                minHeight = dp(30)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val clipboard =
                        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("TypeSafe AI", message)
                    )
                    toast("تم نسخ الرسالة")
                }
            }
            topRow.addView(
                copyButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(30)
                )
            )
        }

        val body = TextView(this).apply {
            text = message.trim()
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), dp(3), 0)
            gravity = Gravity.START
            setTextIsSelectable(true)
        }

        bubble.addView(topRow)
        bubble.addView(body)
        body.maxWidth =
            (resources.displayMetrics.widthPixels * 0.78f).toInt()

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        chatContainer.addView(row)

        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
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
        TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.rgb(170, 170, 170))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
            setPadding(dp(2), dp(16), dp(2), dp(7))
            letterSpacing = 0.04f
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
                setTypeface(typeface, android.graphics.Typeface.BOLD)
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
            setHintTextColor(Color.rgb(125, 125, 125))
            background = roundedBackground(
                Color.rgb(20, 20, 20),
                Color.rgb(52, 52, 52),
                12
            )
            setPadding(dp(14), dp(11), dp(14), dp(11))
            minLines = lines
            maxLines = lines
            gravity = Gravity.TOP or Gravity.RIGHT
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            background = roundedBackground(
                Color.rgb(14, 14, 14),
                Color.rgb(40, 40, 40),
                16
            )
        }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }

    private fun actionButton(
        label: String,
        action: () -> Unit
    ): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            background = roundedBackground(
                Color.rgb(30, 30, 30),
                Color.rgb(70, 70, 70),
                12
            )
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { action() }
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

