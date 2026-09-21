package com.tomasthrawat.typesafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_ENDPOINT = "https://typesafe-mcp-key.vercel.app"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TypeSafeApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSafeApp() {
    val api = remember { TypeSafeApi() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var endpoint by rememberSaveable { mutableStateOf(DEFAULT_ENDPOINT) }
    var state by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable {
        mutableStateOf("هل هذه المعلومة مدعومة بالأدلة المتاحة؟")
    }
    var type by rememberSaveable { mutableStateOf("noul") }
    var levels by rememberSaveable { mutableStateOf("ضعيف\nمتوسط\nقوي") }

    var connectionStatus by remember { mutableStateOf("لم يتم الاختبار") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            connectionStatus = "جاري الاتصال..."
            connectionStatus = withContext(Dispatchers.IO) {
                api.health(endpoint).fold(
                    onSuccess = { "تم الاتصال بالخادم" },
                    onFailure = { "فشل الاتصال: " + it.message }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TypeSafe")
                        Text(
                            "تطبيق Android Native",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الاتصال", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("عنوان الخادم") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    connectionStatus = "جاري الاختبار..."
                                    connectionStatus = withContext(Dispatchers.IO) {
                                        api.health(endpoint).fold(
                                            onSuccess = { "تم الاتصال بالخادم" },
                                            onFailure = { "فشل الاتصال: " + it.message }
                                        )
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy
                        ) {
                            Text("اختبار الاتصال")
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            connectionStatus,
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System One", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("السياق / الحالة") },
                        minLines = 3
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("السؤال") },
                        minLines = 3
                    )

                    Spacer(Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = type,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("نوع السؤال") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("noul", "score", "choice").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        type = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (type == "score") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = levels,
                            onValueChange = { levels = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("المستويات، كل مستوى في سطر") },
                            minLines = 3
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                result = "جاري إرسال الطلب..."
                                val response = withContext(Dispatchers.IO) {
                                    api.ask(
                                        TypeSafeRequest(
                                            endpoint = endpoint,
                                            state = state,
                                            question = question,
                                            type = type,
                                            levels = levels.lines()
                                        )
                                    )
                                }
                                result = response.fold(
                                    onSuccess = { prettyJson(it) },
                                    onFailure = { "خطأ: " + it.message }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy && question.isNotBlank()
                    ) {
                        Text(if (busy) "جاري التنفيذ..." else "اسأل TypeSafe")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("النتيجة", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(result.ifBlank { "لا توجد نتيجة بعد." })
                }
            }
        }
    }
}

private fun prettyJson(raw: String): String =
    runCatching {
        org.json.JSONObject(raw).toString(2)
    }.getOrElse { raw }
