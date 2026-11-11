package io.github.bassboost1981.gear360bridge.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bassboost1981.gear360bridge.R

/** Welcher Teil der Kamera in der Zeichnung hervorgehoben wird. */
private enum class Part { POWER, MENU_AND_SHUTTER, DISPLAY, WIFI }

/** Schritt-fuer-Schritt-Anleitung, bis die Kamera erreichbar ist. */
@Composable
fun ConnectGuideScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineSmall)
        Step(1, Part.POWER, stringResource(R.string.guide_step1))
        Step(2, Part.MENU_AND_SHUTTER, stringResource(R.string.guide_step2))
        Step(3, Part.DISPLAY, stringResource(R.string.guide_step3))
        Step(4, Part.WIFI, stringResource(R.string.guide_step4))
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.guide_open_wifi)) }
        Text(stringResource(R.string.guide_waiting), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Step(number: Int, part: Part, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CameraSketch(part, Modifier.size(88.dp))
        Text("$number. $text", style = MaterialTheme.typography.bodyLarge)
    }
}

/** Einfache Zeichnung der Kamera von vorn: Kugel, Linse, Tasten oben und seitlich, Display oben. */
@Composable
private fun CameraSketch(part: Part, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.04f)
        val center = Offset(w / 2, w * 0.56f)
        val radius = w * 0.36f
        fun color(vararg highlighted: Part): Color = if (part in highlighted) accent else line

        drawCircle(line, radius, center, style = stroke)
        drawCircle(line, radius * 0.5f, center, style = stroke)
        // Display oben auf der Kugel
        drawRoundRect(
            color(Part.DISPLAY), Offset(w * 0.36f, w * 0.24f), Size(w * 0.28f, w * 0.1f), CornerRadius(w * 0.02f),
            style = if (part == Part.DISPLAY) androidx.compose.ui.graphics.drawscope.Fill else stroke,
        )
        // Ausloeser oben
        drawRoundRect(color(Part.MENU_AND_SHUTTER), Offset(w * 0.42f, w * 0.08f), Size(w * 0.16f, w * 0.09f), CornerRadius(w * 0.03f))
        // Menue-Taste (oben) und Ein/Aus-Taste (unten) an der Seite
        drawRoundRect(color(Part.MENU_AND_SHUTTER), Offset(w * 0.9f, w * 0.38f), Size(w * 0.07f, w * 0.14f), CornerRadius(w * 0.02f))
        drawRoundRect(color(Part.POWER), Offset(w * 0.9f, w * 0.6f), Size(w * 0.07f, w * 0.14f), CornerRadius(w * 0.02f))
        if (part == Part.WIFI) {
            // Funkwellen
            for (i in 1..3) {
                drawArc(
                    accent, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(center.x - radius * 0.3f * i, center.y - radius * 0.3f * i),
                    size = Size(radius * 0.6f * i, radius * 0.6f * i), style = stroke,
                )
            }
        }
    }
}
