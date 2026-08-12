# 첫 번째 작업: OS Scheduler

## 큰 그림

프로토타입 스터디의 첫 번째 작업입니다. 운영체제의 프로세스와 CPU가 어떻게 협력하는지 직접 만들어 봅니다.

이번 작업의 중심은 스케줄링 알고리즘을 외우는 것이 아니라, 다음 경계를 코드로 확인하는 것입니다.

```mermaid
flowchart TD
    caller[외부 호출자] -->|spawn| kernel[MiniKernel]
    kernel --> pcb[ProcessControlBlock]
    kernel --> ready[Ready Queue]
    kernel --> waiting[Waiting Queue]
    kernel --> policy[SchedulerPolicy]
    policy -->|다음 프로세스 선택| cpu[CPU]
    cpu -->|한 tick 실행| program[ProcessProgram]
    program --> continue[Continue]
    program --> io[WaitForIo]
    program --> exit[Exit]
    continue --> ready
    io --> waiting
    exit --> terminated[TERMINATED]
```

프로세스의 전체 실행 시간을 커널이 미리 아는 모델이 아닙니다. 프로그램을 한 tick 실행한 뒤 반환된 신호를 보고 커널이 상태를 바꿉니다.

```text
spawn
→ READY
→ RUNNING
→ Continue, WaitForIo, Exit
→ READY, WAITING, TERMINATED
```

## 대표 구현

### Kotlin

- `ProcessProgram`: 한 tick 실행 후 신호를 반환하는 프로그램
- `ProcessSignal`: `Continue`, `WaitForIo`, `Exit`
- `ProcessControlBlock`: 커널이 관리하는 프로세스 상태
- `MiniKernel`: 생성, 실행, 선점, I/O 대기, wake-up, 종료 관리
- `SchedulerPolicy`: Ready Queue에서 다음 프로세스를 선택
- `RoundRobinPolicy`: quantum 기반 정책

### HTML

- `kernel-visualizer.html`: 프로세스 생성과 신호, 상태 전환 시각화
- `round-robin-visualizer.html`: Round Robin 실행 순서 시각화
- `eevdf-visualizer.html`: EEVDF의 lag와 virtual deadline 시각화

### 참고

- [프로토타입 스터디 Notion](https://app.notion.com/p/3c18db64698e80b7a2deea620ad0f64e)
- [OS Scheduler 구현 코드](../os-scheduler/README.md)

## 작업 로그

### 2026-08-12 수요일

- Round Robin 개념과 PCB, 문맥 전환을 정리했습니다.
- Python으로 Round Robin 시뮬레이터를 만들었습니다.
- Kotlin으로 Round Robin 구현을 시작했습니다.
- Round Robin HTML 시각화를 만들었습니다.
- SJF와 EEVDF의 선택 원리를 공부했습니다.
- EEVDF의 `lag`, `eligible`, `virtual deadline`을 단순 모델로 만들었습니다.
- EEVDF HTML 시각화를 만들었습니다.

### 2026-08-19 수요일

- `arrivalTime`, `burstTime`을 커널이 미리 아는 모델의 문제를 확인했습니다.
- 프로세스가 실행 결과를 신호로 반환하는 구조로 바꿨습니다.
- `MiniKernel`을 추가했습니다.
- `ProcessProgram`과 `ProcessSignal`을 추가했습니다.
- `Continue`, `WaitForIo`, `Exit`에 따른 상태 전환을 구현했습니다.
- `SchedulerPolicy`와 `RoundRobinPolicy`를 분리했습니다.
- Kotlin 테스트를 추가했습니다.
- 프로세스를 생성하고 한 tick씩 실행하는 HTML을 만들었습니다.
- HTML에서 Round Robin과 FCFS를 선택하고 quantum을 설정할 수 있게 했습니다.

## 다음 작업

- FCFS, SJF, SRTF 정책을 Kotlin에 추가합니다.
- HTML 시각화가 공통 이벤트 로그를 사용하도록 연결합니다.
- I/O와 quantum 차이를 한 화면에서 비교합니다.
- 이후 Priority, MLFQ, 멀티코어, EEVDF를 연결합니다.
