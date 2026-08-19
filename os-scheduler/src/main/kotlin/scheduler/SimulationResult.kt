package scheduler

data class ExecutionSlice(
    val pid: String,
    val startedAt: Int,
    val endedAt: Int,
) {
    val duration: Int
        get() = endedAt - startedAt
}

enum class SchedulerEventType {
    SPAWN,
    DISPATCH,
    RUN,
    PREEMPT,
    IO_WAIT,
    WAKE_UP,
    COMPLETE,
    IDLE,
}

data class SchedulerEvent(
    val timestamp: Int,
    val type: SchedulerEventType,
    val pid: String? = null,
    val message: String,
)

data class ProcessMetrics(
    val pid: String,
    val waitingTime: Int,
    val responseTime: Int,
    val turnaroundTime: Int,
)

data class SimulationMetrics(
    val processes: List<ProcessMetrics>,
    val averageWaitingTime: Double,
    val averageResponseTime: Double,
    val averageTurnaroundTime: Double,
    val throughput: Double,
    val elapsedTime: Int,
)

data class SimulationResult(
    val policyName: String,
    val timeline: List<ExecutionSlice>,
    val events: List<SchedulerEvent>,
    val metrics: SimulationMetrics,
)
