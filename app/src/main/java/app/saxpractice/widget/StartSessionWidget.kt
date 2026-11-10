package app.saxpractice.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.saxpractice.MainActivity
import app.saxpractice.R

/** Home-screen widget: one tap starts today's session. */
class StartSessionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_SESSION, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).padding(12.dp)
                        .clickable(actionStartActivity(intent)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(context.getString(R.string.app_name), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer))
                    Text(
                        context.getString(R.string.widget_start),
                        style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer),
                    )
                }
            }
        }
    }
}

class StartSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StartSessionWidget()
}
