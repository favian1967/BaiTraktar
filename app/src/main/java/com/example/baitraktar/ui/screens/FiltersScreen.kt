package com.example.baitraktar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.baitraktar.data.MeResult
import com.example.baitraktar.data.OrdersRepository
import com.example.baitraktar.data.remote.MeDto
import com.example.baitraktar.ui.components.BackTopBar
import com.example.baitraktar.ui.components.EmptyState
import com.example.baitraktar.ui.components.KworkSlider
import com.example.baitraktar.ui.components.SecondaryButton
import com.example.baitraktar.ui.components.SectionRule
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OnLime
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val PROFILE_MIN = 40

/**
 * What the AI filters by, on the shared server: the profile in the user's own words and the
 * score threshold. Same values the bot's /profile and /threshold change. A personal parser has
 * no /api/me — its filters live in config.json, and the screen says so instead of faking a form.
 */
@Composable
fun FiltersScreen(repository: OrdersRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<MeResult?>(null) }
    var saved by remember { mutableStateOf<MeDto?>(null) }
    var profile by remember { mutableStateOf("") }
    var threshold by remember { mutableIntStateOf(60) }
    var slider by remember { mutableFloatStateOf(0.6f) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun apply(me: MeDto) {
        saved = me
        profile = me.profile
        threshold = me.threshold
        slider = me.threshold / 100f
    }

    suspend fun load() {
        loaded = null
        val r = repository.fetchMe()
        if (r is MeResult.Success) apply(r.me)
        loaded = r
    }

    LaunchedEffect(Unit) { load() }

    val current = saved
    val changed = current != null && (profile.trim() != current.profile || threshold != current.threshold)
    val profileOk = profile.trim().length >= PROFILE_MIN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BackTopBar(onBack = onBack) {
            if (loaded is MeResult.Success) {
                Button(
                    onClick = {
                        saving = true
                        note = null
                        scope.launch {
                            val r = repository.updateMe(
                                profile = profile.trim().takeIf { it != current?.profile },
                                threshold = threshold.takeIf { it != current?.threshold },
                            )
                            saving = false
                            when (r) {
                                is MeResult.Success -> {
                                    apply(r.me)
                                    note = "Сохранено. Новые заказы оцениваются уже так."
                                }
                                is MeResult.Failure -> note = r.message
                                MeResult.Unsupported -> note = "Сервер не поддерживает настройку из приложения."
                            }
                        }
                    },
                    enabled = changed && profileOk && !saving,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = OnLime),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(if (saving) "Сохраняю…" else "Сохранить", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
        ) {
            Text("Фильтры", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "ИИ читает каждый новый заказ и сравнивает с вашим профилем. Сюда приходят те, что набрали порог.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Space.section))

            when (val r = loaded) {
                null -> Text("Загружаю…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                MeResult.Unsupported -> EmptyState(
                    "Фильтры задаются на сервере",
                    detail = "Это личный парсер: что пропускать, решают config.json, keywords.json и profile.md на VPS.",
                )
                is MeResult.Failure -> {
                    EmptyState("Не удалось загрузить", detail = r.message)
                    SecondaryButton(text = "Повторить", onClick = { scope.launch { load() } })
                }
                is MeResult.Success -> {
                    SectionRule("Профиль")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Что вы делаете и чего не берёте — своими словами, как в боте.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = profile,
                        onValueChange = { profile = it; note = null },
                        minLines = 4,
                        isError = !profileOk,
                        supportingText = {
                            if (!profileOk) Text("Минимум $PROFILE_MIN символов — по паре слов ИИ не поймёт, что подходит.")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = OutlineSoft,
                            cursorColor = Lime,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )

                    Spacer(Modifier.height(Space.section))
                    SectionRule("Порог") {
                        Text("$threshold%", style = MonoType.value, color = Lime)
                    }
                    Spacer(Modifier.height(4.dp))
                    KworkSlider(
                        value = slider,
                        onValueChange = {
                            slider = it
                            // Шаг 5: разница между 63 и 64 % у модели — шум, а не смысл.
                            threshold = ((it * 100) / 5).roundToInt() * 5
                            note = null
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("больше заказов", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Spacer(Modifier.weight(1f))
                        Text("только точные", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
            note?.let {
                Spacer(Modifier.height(Space.block))
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.height(Space.section))
        }
    }
}
