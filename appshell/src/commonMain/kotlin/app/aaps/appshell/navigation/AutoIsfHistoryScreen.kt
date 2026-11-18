package app.aaps.appshell.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

@Composable
internal fun AutoIsfHistoryScreen(
    persistence: PersistenceLayer,
    date: DateUtil,
    profile: ProfileUtil,
    format: DecimalFormatter,
    back: () -> Unit
) {
    val history by produceState<Result<List<APSResult>>?>(null, persistence) {
        val end = date.now()
        value = try {
            Result.success(
                persistence.getApsResults(end - 86_400_000L, end)
                    .filter { (it.rawData() as? RT)?.autoIsf != null }
                    .sortedByDescending { it.date }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    fun number(x: Double?) = x?.let { format.to2Decimal(it) } ?: "--"
    fun ratio(x: Double?) = number(x?.takeUnless { abs(it - 1) < 0.005 })
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glucose = if (dark) Color(0xFF00FF00) else Color(0xFF007000)
    val acceleration = if (dark) Color(0xFF669CFF) else Color(0xFF0044CC)
    val bg = if (dark) Color(0xFFFF6666) else Color(0xFFB00020)
    val postprandial = glucose
    val duration = if (dark) Color(0xFFFF8800) else Color(0xFF995000)
    val final = if (dark) Color(0xFF00FFFF) else Color(0xFF006B73)
    val insulin = if (dark) Color(0xFF64B5F6) else Color(0xFF125A9C)
    val columnColors = listOf(
        Color.Unspecified, glucose, final, acceleration, bg, postprandial, duration,
        insulin, insulin, Color.Unspecified, glucose, glucose, glucose,
        insulin, insulin, insulin, insulin, insulin, insulin, insulin
    )
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
            Row {
                TextButton(onClick = back) { Text("‹") }
                Text("AutoISF · 24 h", modifier = Modifier.padding(12.dp))
            }
            val loaded = history
            val rows = loaded?.getOrNull()
            if (loaded == null) CircularProgressIndicator(Modifier.padding(16.dp))
            else if (rows == null) Text("Could not load AutoISF history", Modifier.padding(16.dp))
            else if (rows.isEmpty()) Text("No AutoISF runs in the last 24 hours", Modifier.padding(16.dp))
            else Column(Modifier.horizontalScroll(rememberScrollState())) {
                HistoryCells(listOf("Time", "BGL", "Final", "acce", "bg", "pp", "dura", "SMB", "SMBR", "iobT", "acce", "Δ", "SΔ", "Req", "TBR", "S5", "S15", "S30", "S60", "S180"), columnColors, header = true)
                LazyColumn {
                    items(rows) { row ->
                        val rt = row.rawData() as RT
                        val f = requireNotNull(rt.autoIsf)
                        val g = row.glucoseStatus
                        val p = row.oapsProfileAutoIsf
                        val finalRatio = f.finalIsf
                        val finalColor = when {
                            finalRatio == null || abs(finalRatio - 1.0) < 0.005 -> final
                            f.acceIsf?.let { abs(finalRatio - it) < 0.005 } == true -> acceleration
                            f.bgIsf?.let { abs(finalRatio - it) < 0.005 } == true -> bg
                            f.ppIsf?.let { abs(finalRatio - it) < 0.005 } == true -> postprandial
                            f.duraIsf?.let { abs(finalRatio - it) < 0.005 } == true -> duration
                            else -> final
                        }
                        HistoryCells(listOf(date.timeString(row.date), (g?.glucose ?: rt.bg)?.let { profile.fromMgdlToStringInUnits(it) } ?: "--",
                            ratio(f.finalIsf), ratio(f.acceIsf), ratio(f.bgIsf), ratio(f.ppIsf), ratio(f.duraIsf),
                            number(rt.units?.takeUnless { it == 0.0 }), number(f.smbRatio), number(f.iobThEffective), number(f.parabolaRaw),
                            g?.delta?.let { profile.fromMgdlToSignedStringInUnits(it) } ?: "--",
                            g?.shortAvgDelta?.let { profile.fromMgdlToSignedStringInUnits(it) } ?: "--",
                            number(rt.insulinReq?.takeUnless { it == 0.0 }), number(rt.rate)) +
                            listOf(p?.recent_steps_5_minutes, f.steps15, p?.recent_steps_30_minutes, p?.recent_steps_60_minutes, f.steps180)
                                .map { count -> count?.takeIf { it >= 0 }?.toString() ?: "--" }, columnColors, finalColor = finalColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCells(
    values: List<String>,
    colors: List<Color>,
    header: Boolean = false,
    finalColor: Color = colors[2]
) {
    Row {
        values.forEachIndexed { index, value ->
            Text(
                text = value,
                modifier = Modifier.width(68.dp).padding(4.dp),
                color = if (index == 2) finalColor else colors[index],
                fontWeight = if (header) FontWeight.Bold else null
            )
        }
    }
}
