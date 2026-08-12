data class EevdfTask(
    val pid: String,
    val arrivalTime: Int,
    val totalRuntime: Int,
    val requestedSlice: Int,
    var remainingRuntime: Int = totalRuntime,
    var virtualRuntime: Double = 0.0,
    var virtualDeadline: Double? = null,
)

data class EevdfTick(
    val time: Int,
    val pid: String,
    val averageVirtualRuntime: Double,
    val lagBeforeRun: Double,
    val virtualDeadline: Double,
)

/**
 * EEVDF의 선택 원리를 보여주는 교육용 단순 모델.
 *
 * 가정:
 * - 모든 task의 weight가 같다.
 * - 모든 task는 I/O sleep 없이 계속 runnable이다.
 * - 한 번에 1 tick 실행한 뒤 다시 선택한다.
 *
 * 실제 Linux 구현에는 정수 기반 가중치 계산, sleeper lag decay,
 * run queue tree, SMP load balancing 등의 세부 사항이 더 있다.
 */
class EducationalEevdfScheduler {
    fun run(tasks: List<EevdfTask>): List<EevdfTick> {
        require(tasks.isNotEmpty()) { "task가 하나 이상 필요합니다." }
        require(tasks.all { it.totalRuntime > 0 && it.requestedSlice > 0 }) {
            "실행 시간과 요청 slice는 0보다 커야 합니다."
        }

        val timeline = mutableListOf<EevdfTick>()
        var clock = 0

        while (tasks.any { it.remainingRuntime > 0 }) {
            var runnable = tasks.filter {
                it.arrivalTime <= clock && it.remainingRuntime > 0
            }

            if (runnable.isEmpty()) {
                clock = tasks.filter { it.remainingRuntime > 0 }.minOf { it.arrivalTime }
                runnable = tasks.filter {
                    it.arrivalTime <= clock && it.remainingRuntime > 0
                }
            }

            val averageVruntime = runnable.map { it.virtualRuntime }.average()

            // 새 작업은 현재 공정 기준선에서 시작한다. 과거 CPU 빚을 받지 않는다.
            runnable.filter { it.virtualDeadline == null }.forEach {
                it.virtualRuntime = averageVruntime
                it.virtualDeadline = averageVruntime + it.requestedSlice
            }

            // lag = 공정 기준선 - 내가 받은 CPU 시간.
            // 0 이상이면 CPU를 받을 자격(eligible)이 있다.
            val eligible = runnable.filter {
                averageVruntime - it.virtualRuntime >= -1e-9
            }

            val current = eligible.minWithOrNull(
                compareBy<EevdfTask> { it.virtualDeadline!! }
                    .thenBy { it.virtualRuntime }
                    .thenBy { it.pid },
            ) ?: error("eligible task가 없습니다.")

            val lag = averageVruntime - current.virtualRuntime
            timeline += EevdfTick(
                time = clock,
                pid = current.pid,
                averageVirtualRuntime = averageVruntime,
                lagBeforeRun = lag,
                virtualDeadline = current.virtualDeadline!!,
            )

            current.remainingRuntime -= 1
            current.virtualRuntime += 1.0
            clock += 1

            // 이번 요청 slice를 모두 사용했으면 다음 가상 마감 시간을 발급한다.
            if (
                current.virtualRuntime >= current.virtualDeadline!! &&
                current.remainingRuntime > 0
            ) {
                current.virtualDeadline = current.virtualRuntime + current.requestedSlice
            }
        }

        return timeline
    }
}

fun main() {
    val tasks = listOf(
        EevdfTask(pid = "A", arrivalTime = 0, totalRuntime = 8, requestedSlice = 4),
        EevdfTask(pid = "B", arrivalTime = 0, totalRuntime = 8, requestedSlice = 4),
        EevdfTask(pid = "C", arrivalTime = 3, totalRuntime = 3, requestedSlice = 1),
    )

    EducationalEevdfScheduler().run(tasks).forEach { tick ->
        println(
            "t=${tick.time}: ${tick.pid} " +
                "lag=${"%.1f".format(tick.lagBeforeRun)} " +
                "VD=${"%.1f".format(tick.virtualDeadline)}",
        )
    }
}
