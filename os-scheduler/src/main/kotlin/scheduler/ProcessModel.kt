package scheduler

enum class ProcessState {
    READY,
    RUNNING,
    WAITING,
    TERMINATED,
}

sealed interface ProcessSignal {
    data object Continue : ProcessSignal

    data class WaitForIo(
        val ticks: Int,
    ) : ProcessSignal {
        init {
            require(ticks > 0) { "I/O 대기 시간은 0보다 커야 합니다." }
        }
    }

    data object Exit : ProcessSignal
}

fun interface ProcessProgram {
    fun executeOneTick(): ProcessSignal
}

class ProcessControlBlock internal constructor(
    val pid: String,
    internal val program: ProcessProgram,
    val spawnedAt: Int,
) {
    var state: ProcessState = ProcessState.READY
        internal set

    var usedCpuTime: Int = 0
        internal set

    var accumulatedWaitingTime: Int = 0
        internal set

    var firstStartedAt: Int? = null
        internal set

    var completionTime: Int? = null
        internal set

    internal var readySince: Int = spawnedAt
}
