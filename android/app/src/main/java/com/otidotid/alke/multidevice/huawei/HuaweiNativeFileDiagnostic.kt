package com.otidotid.alke.multidevice.huawei

/** One bounded, explicitly requested diagnostic. Never runs during normal sync. */
internal object HuaweiNativeFileDiagnostic {
    fun run(session: HuaweiFileSession, store: HuaweiFitnessStore, device: String,
        start: Long, end: Long, workouts: List<HuaweiHistoryRead.WorkoutSummary>): String {
        if (!session.supported()) return "Native file transfer commands unavailable."
        val report = mutableListOf<String>()
        val workout = workouts.maxByOrNull { it.end }
        if (workout != null) {
            try {
                val gps = session.read(HuaweiFileRead.Kind.GPS,0,0,workout.number)
                try {
                    val count = try { store.saveGpsCandidate(device,workout,gps) }
                        catch (_: IllegalArgumentException) { null }
                    report += "GPS candidate points: ${count ?: "unsupported layout"}."
                } finally { gps.fill(0) }
            } catch (_: HuaweiFileUnavailable) {
                report += "GPS file unavailable on watch."
            }
        } else report += "No recent workout available for GPS test."
        try {
            val state = session.read(HuaweiFileRead.Kind.SLEEP_STATE,start,end)
            try {
                val data = session.read(HuaweiFileRead.Kind.SLEEP_DATA,start,end)
                try {
                    val counts = try { store.saveTruSleepCandidate(device,state,data,start,end) }
                        catch (_: IllegalArgumentException) { null }
                    report += if (counts == null) "TruSleep: unsupported layout."
                        else "TruSleep candidates: ${counts.states} state, ${counts.acc} ACC, ${counts.ppg} PPG."
                } finally { data.fill(0) }
            } finally { state.fill(0) }
        } catch (_: HuaweiFileUnavailable) {
            report += "TruSleep file unavailable on watch."
        }
        return report.joinToString("\n")
    }
}
