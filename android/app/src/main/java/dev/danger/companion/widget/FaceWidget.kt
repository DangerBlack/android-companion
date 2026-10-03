package dev.danger.companion.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.BitmapImageProvider
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.danger.companion.CompanionState
import dev.danger.companion.CompanionStore
import dev.danger.companion.SettingsStore
import dev.danger.companion.face.BloubRenderer
import dev.danger.companion.face.FaceAnimBus

class FaceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            FaceContent()
        }
    }

    @Composable
    private fun FaceContent() {
        val context = LocalContext.current
        val state by CompanionStore.flow(context)
            .collectAsState(initial = CompanionState.defaultSleepy())
        val settings by SettingsStore.flow(context).collectAsState(initial = null)
        val label = state.text?.takeIf { it.isNotBlank() }
        val scrollPage by TextScrollBus.page.collectAsState()
        val display = if (label != null) (scrollPage ?: label) else null
        val bgColor = parseBackground(settings?.bgColor)

        var modifier = GlanceModifier.fillMaxSize()
        if (bgColor != null) {
            modifier = modifier.background(ColorProvider(bgColor)).cornerRadius(18.dp)
        }

        Column(
            modifier = modifier.clickable(actionRunCallback<PetCompanionAction>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = if (display != null) {
                    GlanceModifier.defaultWeight().fillMaxWidth()
                } else {
                    GlanceModifier.fillMaxSize()
                },
                contentAlignment = Alignment.Center,
            ) {
                FaceImage(state)
            }
            if (display != null) {
                Text(
                    text = display,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFE8ECF2)),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                )
            }
        }
    }

    private fun parseBackground(value: String?): Color? {
        if (value == null || value.equals(SettingsStore.TRANSPARENT, ignoreCase = true)) return null
        return try {
            Color(android.graphics.Color.parseColor(value))
        } catch (_: Exception) {
            Color(0xFF11151C)
        }
    }

    @Composable
    private fun FaceImage(state: CompanionState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        val widthPx = (size.width.value * density).toInt().coerceAtLeast(48)
        val bitmap = BloubRenderer.render(state, widthPx, widthPx, FaceAnimBus.current)
        Image(
            provider = BitmapImageProvider(bitmap),
            contentDescription = state.instanceLabel,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
