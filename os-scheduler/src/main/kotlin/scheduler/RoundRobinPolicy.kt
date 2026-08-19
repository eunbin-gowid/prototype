package scheduler

class RoundRobinPolicy(
    private val quantum: Int,
) : SchedulerPolicy {
    init {
        require(quantum > 0) { "quantum은 0보다 커야 합니다." }
    }

    override val name: String = "Round Robin (quantum=$quantum)"

    override fun selectNext(
        readyQueue: List<ProcessControlBlock>,
        clock: Int,
    ): SchedulingDecision {
        check(readyQueue.isNotEmpty()) { "Ready Queue가 비어 있습니다." }
        return SchedulingDecision(
            process = readyQueue.first(),
            maxRunTicks = quantum,
        )
    }
}
