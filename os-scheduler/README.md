# 프로토타입

평소 궁금했던 기술, 라이브러리, 프레임워크를 직접 만들어 보며 동작 원리와 구조를 공부하는 작업입니다.

공통 프로젝트나 정해진 커리큘럼은 없습니다. 만들고 싶은 것을 고르고, AI와 함께 작은 버전부터 구현하고, 직접 실행하고 테스트하면서 이해합니다.

지금은 운영체제 스케줄러를 만들고 있습니다. mini Kafka와 mini Redis를 만든 다음, 이번에는 프로세스와 CPU가 어떻게 협력하는지 직접 확인합니다.

## OS Scheduler

현재 구현은 프로그램의 전체 실행 시간을 커널이 미리 아는 방식이 아닙니다. 외부에서 프로세스를 생성하고, 프로그램은 CPU에서 한 tick 실행된 뒤 `Continue`, `WaitForIo`, `Exit` 중 하나를 반환합니다.

```text
spawn
→ READY
→ RUNNING
→ Continue, WaitForIo, Exit
→ READY, WAITING, TERMINATED
```

## Kotlin 공통 엔진

프로세스 모델, 정책, 커널 실행, 이벤트, 지표 계산을 분리했습니다.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home gradle run
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home gradle test
```

주요 구조:

- `ProcessProgram`: CPU에서 한 tick 실행된 뒤 신호를 반환하는 프로그램
- `ProcessSignal`: `Continue`, `WaitForIo`, `Exit`
- `ProcessControlBlock`: 커널이 관리하는 현재 프로세스 상태
- `SchedulerPolicy`: 다음 프로세스와 최대 실행 시간을 결정하는 정책
- `MiniKernel`: `spawn`, 실행, 선점, I/O 대기, wake-up, 종료를 관리
- `SimulationResult`: 타임라인, 표준 이벤트 로그, 성능 지표
- `RoundRobinPolicy`: quantum을 사용하는 첫 번째 정책 구현

프로세스는 외부에서 `spawn()`으로 생성됩니다. 커널은 전체 CPU 실행 시간을 미리 받지 않으며, 프로그램이 실행 후 반환하는 신호만 보고 상태를 바꿉니다.

현재 계산하는 지표는 waiting time, response time, turnaround time, throughput입니다.

공통 커널은 `src/main/kotlin/scheduler` 아래에 있습니다. 루트의 `EevdfScheduler.kt`는 EEVDF 선택 규칙만 관찰하기 위한 별도 교육용 실험입니다.

## 움직이는 시각화

`round-robin-visualizer.html`을 브라우저에서 열고 **재생** 또는 **한 단계**를 누릅니다.

`kernel-visualizer.html`은 현재 MiniKernel 모델을 보여줍니다. `P1 생성` 같은 버튼으로 프로세스를 외부에서 만들고, `CPU 한 tick`으로 프로그램 신호와 상태 전환을 관찰합니다.

## EEVDF 교육용 버전

- `EevdfScheduler.kt`: 동일 가중치와 1 tick 선점을 가정한 Kotlin 구현
- `eevdf-visualizer.html`: lag, eligibility, virtual deadline 선택 과정을 움직여 보여주는 화면

이 구현은 Linux 커널을 복제한 것이 아니라 EEVDF의 선택 원리를 학습하기 위한 모델입니다.
