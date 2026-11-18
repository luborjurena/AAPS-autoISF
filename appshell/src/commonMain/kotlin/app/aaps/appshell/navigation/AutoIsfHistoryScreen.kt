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
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import kotlin.math.abs

@Composable
internal fun AutoIsfHistoryScreen(
    persistence: PersistenceLayer,
    date: DateUtil,
    profile: ProfileUtil,
    format: DecimalFormatter,
    back: () -> Unit
) {
    val results by produceState<List<APSResult>?>(null, persistence) {
        val end = date.now()
        value = persistence.getApsResults(end - 86_400_000L, end)
            .filter { (it.rawData() as? RT)?.autoIsf != null && (it.glucoseStatus?.glucose ?: 0.0) > 0 }
            .sortedByDescending { it.date }
    }
    fun number(x: Double?) = x?.let { format.to2Decimal(it) } ?: "--"
    fun ratio(x: Double?) = number(x?.takeUnless { abs(it - 1) < 0.005 })
    Column(Modifier.safeDrawingPadding().fillMaxSize()) {
        Row {
            TextButton(onClick = back) { Text("‹") }
            Text("AutoISF · 24 h", modifier = Modifier.padding(12.dp))
        }
        val rows = results
        if (rows == null) CircularProgressIndicator()
        else if (rows.isEmpty()) Text("—")
        else Column(Modifier.horizontalScroll(rememberScrollState())) {
            HistoryCells(listOf("Time", "BGL", "Final", "acce", "bg", "pp", "dura", "SMB", "iobT", "acce", "Δ", "SΔ", "Req", "TBR", "S5", "S15", "S30", "S60", "S180"))
            LazyColumn {
                items(rows) { row ->
                    val rt = row.rawData() as RT
                    val f = requireNotNull(rt.autoIsf)
                    val g = requireNotNull(row.glucoseStatus)
                    val p = row.oapsProfileAutoIsf
                    HistoryCells(listOf(date.timeString(row.date), profile.fromMgdlToStringInUnits(g.glucose),
                        ratio(f.finalIsf), ratio(f.acceIsf), ratio(f.bgIsf), ratio(f.ppIsf), ratio(f.duraIsf),
                        number(rt.units?.takeUnless { it == 0.0 }), number(f.iobThEffective), number(f.parabolaRaw),
                        profile.fromMgdlToSignedStringInUnits(g.delta), profile.fromMgdlToSignedStringInUnits(g.shortAvgDelta),
                        number(rt.insulinReq?.takeUnless { it == 0.0 }), number(rt.rate)) +
                        listOf(p?.recent_steps_5_minutes, f.steps15, p?.recent_steps_30_minutes, p?.recent_steps_60_minutes, f.steps180)
                            .map { count -> count?.takeIf { it >= 0 }?.toString() ?: "--" })
                }
            }
        }
    }
}

@Composable
private fun HistoryCells(values: List<String>) {
    Row { values.forEach { Text(it, Modifier.width(68.dp).padding(4.dp)) } }
}
