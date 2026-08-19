package scheduler

data class SchedulingDecision(
    val process: ProcessControlBlock,
    val maxRunTicks: Int,
) {
    init {
        require(maxRunTicks > 0) { "maxRunTicks는 0보다 커야 합니다." }
    }
}

interface SchedulerPolicy {
    val name: String

    fun selectNext(
        readyQueue: List<ProcessControlBlock>,
        clock: Int,
    ): SchedulingDecision
}
