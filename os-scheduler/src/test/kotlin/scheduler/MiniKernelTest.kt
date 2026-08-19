package scheduler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MiniKernelTest {
    @Test
    fun `커널은 프로그램의 전체 실행 시간을 모른 채 신호에 따라 실행한다`() {
        val kernel = MiniKernel(RoundRobinPolicy(quantum = 2))
        kernel.spawn("P1", cpuBoundProgram(5))
        kernel.spawn("P2", cpuBoundProgram(3))
        kernel.spawn("P3", cpuBoundProgram(1))

        val result = kernel.runUntilComplete()

        assertEquals(
            listOf(
                ExecutionSlice("P1", 0, 2),
                ExecutionSlice("P2", 2, 4),
                ExecutionSlice("P3", 4, 5),
                ExecutionSlice("P1", 5, 7),
                ExecutionSlice("P2", 7, 8),
                ExecutionSlice("P1", 8, 9),
            ),
            result.timeline,
        )
    }

    @Test
    fun `실행 도중 외부에서 새 프로세스를 생성할 수 있다`() {
        val kernel = MiniKernel(RoundRobinPolicy(quantum = 2))
        kernel.spawn("P1", cpuBoundProgram(3))

        kernel.tick()
        assertEquals(1, kernel.clock)
        kernel.spawn("P2", cpuBoundProgram(1))

        val result = kernel.runUntilComplete()
        val p2 = result.metrics.processes.single { it.pid == "P2" }

        assertEquals(1, p2.responseTime)
        assertTrue(result.events.any {
            it.type == SchedulerEventType.SPAWN && it.pid == "P2" && it.timestamp == 1
        })
    }

    @Test
    fun `프로세스가 I-O를 요청하면 기다렸다가 다시 Ready 상태가 된다`() {
        val kernel = MiniKernel(RoundRobinPolicy(quantum = 2))
        val signals = ArrayDeque(
            listOf(
                ProcessSignal.WaitForIo(ticks = 3),
                ProcessSignal.Exit,
            ),
        )
        kernel.spawn("P1") { signals.removeFirst() }

        val result = kernel.runUntilComplete()

        assertEquals(
            listOf(
                SchedulerEventType.SPAWN,
                SchedulerEventType.DISPATCH,
                SchedulerEventType.RUN,
                SchedulerEventType.IO_WAIT,
                SchedulerEventType.IDLE,
                SchedulerEventType.WAKE_UP,
                SchedulerEventType.DISPATCH,
                SchedulerEventType.RUN,
                SchedulerEventType.COMPLETE,
            ),
            result.events.map { it.type },
        )
        assertEquals(5, result.metrics.elapsedTime)
        assertEquals(0, result.metrics.processes.single().waitingTime)
    }

    @Test
    fun `대기 응답 반환 시간을 실제 상태 전환으로 계산한다`() {
        val kernel = MiniKernel(RoundRobinPolicy(quantum = 2))
        kernel.spawn("P1", cpuBoundProgram(5))
        kernel.spawn("P2", cpuBoundProgram(3))
        kernel.spawn("P3", cpuBoundProgram(1))

        val result = kernel.runUntilComplete()

        assertEquals(
            listOf(
                ProcessMetrics("P1", waitingTime = 4, responseTime = 0, turnaroundTime = 9),
                ProcessMetrics("P2", waitingTime = 5, responseTime = 2, turnaroundTime = 8),
                ProcessMetrics("P3", waitingTime = 4, responseTime = 4, turnaroundTime = 5),
            ),
            result.metrics.processes,
        )
    }

    @Test
    fun `잘못된 입력을 거부한다`() {
        assertFailsWith<IllegalArgumentException> { RoundRobinPolicy(quantum = 0) }
        assertFailsWith<IllegalArgumentException> { ProcessSignal.WaitForIo(ticks = 0) }

        val kernel = MiniKernel(RoundRobinPolicy(1))
        kernel.spawn("P1", cpuBoundProgram(1))
        assertFailsWith<IllegalArgumentException> {
            kernel.spawn("P1", cpuBoundProgram(1))
        }
    }

    private fun cpuBoundProgram(ticksToRun: Int): ProcessProgram {
        var executedTicks = 0
        return ProcessProgram {
            executedTicks += 1
            if (executedTicks == ticksToRun) {
                ProcessSignal.Exit
            } else {
                ProcessSignal.Continue
            }
        }
    }
}
