package scheduler

private class CpuBoundProgram(
    private val ticksToRun: Int,
) : ProcessProgram {
    private var executedTicks = 0

    override fun executeOneTick(): ProcessSignal {
        executedTicks += 1
        return if (executedTicks == ticksToRun) {
            ProcessSignal.Exit
        } else {
            ProcessSignal.Continue
        }
    }
}

fun main() {
    val kernel = MiniKernel(RoundRobinPolicy(quantum = 2))

    // 실행 시간은 각 프로그램 내부의 사정이다. 커널에는 알려주지 않는다.
    kernel.spawn("P1", CpuBoundProgram(ticksToRun = 5))
    kernel.spawn("P2", CpuBoundProgram(ticksToRun = 3))
    kernel.spawn("P3", CpuBoundProgram(ticksToRun = 1))

    val result = kernel.runUntilComplete()

    println(result.policyName)
    result.timeline.forEach { slice ->
        println("${slice.startedAt} ~ ${slice.endedAt}: ${slice.pid}")
    }

    println()
    result.metrics.processes.forEach { metrics ->
        println(
            "${metrics.pid}: waiting=${metrics.waitingTime}, " +
                "response=${metrics.responseTime}, " +
                "turnaround=${metrics.turnaroundTime}",
        )
    }
}
