from dataclasses import dataclass
from enum import Enum, auto
from typing import Optional


class ProcessState(Enum):
    NEW = auto()
    READY = auto()
    RUNNING = auto()
    TERMINATED = auto()


@dataclass
class Process:
    pid: str
    arrival_time: int
    burst_time: int
    remaining_time: int
    state: ProcessState = ProcessState.NEW
    completion_time: Optional[int] = None

    @classmethod
    def create(cls, pid: str, arrival_time: int, burst_time: int) -> "Process":
        if arrival_time < 0:
            raise ValueError("arrival_time must be zero or greater")
        if burst_time <= 0:
            raise ValueError("burst_time must be greater than zero")

        return cls(
            pid=pid,
            arrival_time=arrival_time,
            burst_time=burst_time,
            remaining_time=burst_time,
        )

    @property
    def turnaround_time(self) -> Optional[int]:
        if self.completion_time is None:
            return None
        return self.completion_time - self.arrival_time

    @property
    def waiting_time(self) -> Optional[int]:
        if self.turnaround_time is None:
            return None
        return self.turnaround_time - self.burst_time
