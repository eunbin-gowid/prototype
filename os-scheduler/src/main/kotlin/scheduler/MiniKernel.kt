package scheduler

class MiniKernel(
    private val policy: SchedulerPolicy,
) {
    private data class WaitingProcess(
        val process: ProcessControlBlock,
        val wakeUpAt: Int,
    )

    private val readyQueue = mutableListOf<ProcessControlBlock>()
    private val waiting = mutableListOf<WaitingProcess>()
    private val processes = mutableListOf<ProcessControlBlock>()
    private val timeline = mutableListOf<ExecutionSlice>()
    private val events = mutableListOf<SchedulerEvent>()

    private var running: ProcessControlBlock? = null
    private var remainingSlice: Int = 0

    var clock: Int = 0
        private set

    fun spawn(
        pid: String,
        program: ProcessProgram,
    ) {
        require(pid.isNotBlank()) { "pid는 비어 있을 수 없습니다." }
        require(processes.none { it.pid == pid }) { "PID는 중복될 수 없습니다." }

        val process = ProcessControlBlock(
            pid = pid,
            program = program,
            spawnedAt = clock,
        )
        processes += process
        readyQueue += process
        events += SchedulerEvent(
            timestamp = clock,
            type = SchedulerEventType.SPAWN,
            pid = pid,
            message = "$pid 프로세스가 생성됐습니다.",
        )
    }

    fun hasWork(): Boolean =
        running != null || readyQueue.isNotEmpty() || waiting.isNotEmpty()

    /**
     * CPU를 한 tick 실행합니다.
     *
     * 실행 가능한 프로세스가 없고 I/O 대기 프로세스만 있다면 다음 wake-up까지
     * 시계를 이동한 후 한 tick을 실행합니다.
     */
    fun tick(): Boolean {
        wakeUpProcesses()

        if (running == null && readyQueue.isEmpty()) {
            if (waiting.isEmpty()) {
                return false
            }

            val wakeUpAt = waiting.minOf { it.wakeUpAt }
            events += SchedulerEvent(
                timestamp = clock,
                type = SchedulerEventType.IDLE,
                message = "CPU가 t=$wakeUpAt 까지 쉽니다.",
            )
            clock = wakeUpAt
            wakeUpProcesses()
        }

        if (running == null) {
            dispatch()
        }

        val current = checkNotNull(running)
        val startedAt = clock
        val signal = current.program.executeOneTick()

        clock += 1
        remainingSlice -= 1
        current.usedCpuTime += 1
        recordExecution(current.pid, startedAt, clock)
        events += SchedulerEvent(
            timestamp = startedAt,
            type = SchedulerEventType.RUN,
            pid = current.pid,
            message = "${current.pid}가 1 tick 실행됐습니다.",
        )

        when (signal) {
            ProcessSignal.Continue -> {
                if (remainingSlice == 0) {
                    preempt(current)
                }
            }

            is ProcessSignal.WaitForIo -> waitForIo(current, signal.ticks)
            ProcessSignal.Exit -> terminate(current)
        }

        return true
    }

    fun runUntilComplete(maxTicks: Int = 100_000): SimulationResult {
        require(maxTicks > 0) { "maxTicks는 0보다 커야 합니다." }
        var executedSteps = 0

        while (hasWork()) {
            check(executedSteps < maxTicks) {
                "프로세스가 $maxTicks step 안에 끝나지 않았습니다."
            }
            tick()
            executedSteps += 1
        }

        return result()
    }

    fun result(): SimulationResult {
        check(processes.isNotEmpty()) { "생성된 프로세스가 없습니다." }
        check(processes.all { it.state == ProcessState.TERMINATED }) {
            "모든 프로세스가 끝난 뒤에 결과를 계산할 수 있습니다."
        }

        val processMetrics = processes.map { process ->
            val firstStartedAt = checkNotNull(process.firstStartedAt)
            val completionTime = checkNotNull(process.completionTime)
            ProcessMetrics(
                pid = process.pid,
                waitingTime = process.accumulatedWaitingTime,
                responseTime = firstStartedAt - process.spawnedAt,
                turnaroundTime = completionTime - process.spawnedAt,
            )
        }

        return SimulationResult(
            policyName = policy.name,
            timeline = timeline.toList(),
            events = events.toList(),
            metrics = SimulationMetrics(
                processes = processMetrics,
                averageWaitingTime = processMetrics.map { it.waitingTime }.average(),
                averageResponseTime = processMetrics.map { it.responseTime }.average(),
                averageTurnaroundTime = processMetrics.map { it.turnaroundTime }.average(),
                throughput = processes.size.toDouble() / clock,
                elapsedTime = clock,
            ),
        )
    }

    private fun dispatch() {
        val decision = policy.selectNext(readyQueue.toList(), clock)
        val selected = decision.process
        check(readyQueue.remove(selected)) {
            "정책은 Ready Queue에 있는 프로세스를 선택해야 합니다."
        }

        selected.accumulatedWaitingTime += clock - selected.readySince
        selected.state = ProcessState.RUNNING
        if (selected.firstStartedAt == null) {
            selected.firstStartedAt = clock
        }
        running = selected
        remainingSlice = decision.maxRunTicks
        events += SchedulerEvent(
            timestamp = clock,
            type = SchedulerEventType.DISPATCH,
            pid = selected.pid,
            message = "${selected.pid}: READY -> RUNNING",
        )
    }

    private fun preempt(process: ProcessControlBlock) {
        process.state = ProcessState.READY
        process.readySince = clock
        readyQueue += process
        running = null
        events += SchedulerEvent(
            timestamp = clock,
            type = SchedulerEventType.PREEMPT,
            pid = process.pid,
            message = "${process.pid}: RUNNING -> READY",
        )
    }

    private fun waitForIo(
        process: ProcessControlBlock,
        ioTicks: Int,
    ) {
        process.state = ProcessState.WAITING
        waiting += WaitingProcess(
            process = process,
            wakeUpAt = clock + ioTicks,
        )
        running = null
        events += SchedulerEvent(
            timestamp = clock,
            type = SchedulerEventType.IO_WAIT,
            pid = process.pid,
            message = "${process.pid}: RUNNING -> WAITING ($ioTicks ticks)",
        )
    }

    private fun terminate(process: ProcessControlBlock) {
        process.state = ProcessState.TERMINATED
        process.completionTime = clock
        running = null
        events += SchedulerEvent(
            timestamp = clock,
            type = SchedulerEventType.COMPLETE,
            pid = process.pid,
            message = "${process.pid}: RUNNING -> TERMINATED",
        )
    }

    private fun wakeUpProcesses() {
        val waking = waiting
            .filter { it.wakeUpAt <= clock }
            .sortedWith(compareBy({ it.wakeUpAt }, { it.process.pid }))

        waking.forEach { waitingProcess ->
            waiting.remove(waitingProcess)
            val process = waitingProcess.process
            process.state = ProcessState.READY
            process.readySince = clock
            readyQueue += process
            events += SchedulerEvent(
                timestamp = clock,
                type = SchedulerEventType.WAKE_UP,
                pid = process.pid,
                message = "${process.pid}: WAITING -> READY",
            )
        }
    }

    private fun recordExecution(
        pid: String,
        startedAt: Int,
        endedAt: Int,
    ) {
        val previous = timeline.lastOrNull()
        if (previous?.pid == pid && previous.endedAt == startedAt) {
            timeline[timeline.lastIndex] = previous.copy(endedAt = endedAt)
        } else {
            timeline += ExecutionSlice(pid, startedAt, endedAt)
        }
    }
}
