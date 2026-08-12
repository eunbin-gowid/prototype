from collections import deque
from dataclasses import dataclass

from process import Process, ProcessState


@dataclass(frozen=True)
class ExecutionSlice:
    pid: str
    started_at: int
    ended_at: int


class RoundRobinScheduler:
    def __init__(self, quantum: int) -> None:
        if quantum <= 0:
            raise ValueError("quantum must be greater than zero")
        self.quantum = quantum

    def run(self, processes: list[Process]) -> list[ExecutionSlice]:
        if len({process.pid for process in processes}) != len(processes):
            raise ValueError("pid must be unique")

        pending = deque(sorted(processes, key=lambda process: process.arrival_time))
        ready: deque[Process] = deque()
        timeline: list[ExecutionSlice] = []
        clock = 0

        while pending or ready:
            self._enqueue_arrivals(pending, ready, clock)

            if not ready:
                clock = pending[0].arrival_time
                self._enqueue_arrivals(pending, ready, clock)

            current = ready.popleft()
            current.state = ProcessState.RUNNING

            started_at = clock
            duration = min(self.quantum, current.remaining_time)
            clock += duration
            current.remaining_time -= duration
            timeline.append(ExecutionSlice(current.pid, started_at, clock))

            # 실행 도중 도착한 프로세스를 먼저 ready queue에 넣는다.
            self._enqueue_arrivals(pending, ready, clock)

            if current.remaining_time == 0:
                current.state = ProcessState.TERMINATED
                current.completion_time = clock
            else:
                current.state = ProcessState.READY
                ready.append(current)

        return timeline

    @staticmethod
    def _enqueue_arrivals(
        pending: deque[Process], ready: deque[Process], clock: int
    ) -> None:
        while pending and pending[0].arrival_time <= clock:
            process = pending.popleft()
            process.state = ProcessState.READY
            ready.append(process)


def main() -> None:
    processes = [
        Process.create("P1", arrival_time=0, burst_time=5),
        Process.create("P2", arrival_time=1, burst_time=3),
        Process.create("P3", arrival_time=2, burst_time=1),
    ]

    timeline = RoundRobinScheduler(quantum=2).run(processes)

    for execution in timeline:
        print(f"{execution.started_at:>2} ~ {execution.ended_at:>2}: {execution.pid}")

    print()
    for process in processes:
        print(
            f"{process.pid}: waiting={process.waiting_time}, "
            f"turnaround={process.turnaround_time}"
        )


if __name__ == "__main__":
    main()
