# SchedulerX — CPU Scheduling Algorithm Visualizer

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![Tests](https://img.shields.io/badge/Tests-61%20passing-success?style=flat-square)
![Deploy](https://img.shields.io/badge/Deploy-AWS%20EC2-yellow?style=flat-square)

> A web-based CPU Scheduling Algorithm Visualizer that lets you simulate, visualize, and understand all major OS scheduling algorithms in real time — complete with animated Gantt charts and full performance metrics.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Live Demo](#live-demo)
- [Features](#features)
- [Supported Algorithms](#supported-algorithms)
- [Tech Stack](#tech-stack)
- [System Architecture](#system-architecture)
- [Security Architecture](#security-architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Input Constraints](#input-constraints)
- [Metrics Formulas](#metrics-formulas)
- [Performance](#performance)
- [Deployment](#deployment)
- [Contributing](#contributing)
- [Author](#author)

---

## Overview

SchedulerX was built to help CS/IT students and enthusiasts **visualize, verify, and understand** CPU scheduling algorithms before exams, assignments, or vivas. Instead of manually tracing through textbook examples, you can input your processes and instantly see the Gantt chart and all performance metrics.

Everything runs **stateless** — no database, no login required. Just open the site, enter your processes, and hit Run.

---

## Live Demo

🌐 **[schedulerx.askanand.me](https://schedulerx.askanand.me)**

---

## Features

- 🎯 **6 Scheduling Algorithms** — FCFS, SJF, SRTF, Round Robin, Priority (NP & P)
- 📊 **Animated Gantt Chart** — color-coded process blocks with timeline ticks and wipe-in animation
- 📈 **Performance Metrics Table** — Completion Time, Turnaround Time, Waiting Time, Response Time per process
- 📉 **Averages** — Average TAT, WT, RT displayed below the metrics table
- 🔄 **Priority Mode Toggle** — animated segmented pill toggle to switch between "Lower = Higher priority" and "Higher = Higher priority"
- 🌙 **Dark / Light Theme** — Cyberpunk Teal (dark) and Sky Blue (light) with animated pill toggle
- ⚡ **Real-time** — results in under 200ms for 15 processes
- 🔒 **Secure** — rate limiting, input validation, CORS, request size limits
- 📱 **Responsive** — works on desktop and mobile

---

## Supported Algorithms

| Algorithm | Type | Key Behaviour |
|---|---|---|
| **FCFS** | Non-Preemptive | Processes execute strictly in order of arrival. Simple but can cause convoy effect. |
| **SJF** | Non-Preemptive | Shortest burst time runs first. Minimizes average waiting time but not preemptive. |
| **SRTF** | Preemptive | If a new process arrives with shorter remaining time, it preempts the current process. |
| **RR** | Preemptive | Each process gets a fixed time slice (quantum). Fair but has higher context-switch overhead. |
| **Priority NP** | Non-Preemptive | Lowest priority number runs first. Once started, runs to completion. |
| **Priority P** | Preemptive | Higher priority arrival immediately preempts the running process. |

> **Priority convention:** By default, lower number = higher priority (standard OS textbook convention). You can toggle this on the UI.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.0.3 (Java 17) |
| Frontend | HTML5 / Tailwind CSS / Vanilla JavaScript |
| Validation | Spring Boot Validation (`@Valid`, `@NotBlank`, `@Min`, `@Max`, `@Pattern`) |
| Build Tool | Maven |
| Server | Nginx (Reverse Proxy) |
| Hosting | AWS EC2 t3.micro (Ubuntu 24.04 LTS) |
| SSL | PositiveSSL (Namecheap) |
| Database | None — fully stateless, in-memory only |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Browser                          │
│              (HTML + Tailwind CSS + JS)                 │
└─────────────────────┬───────────────────────────────────┘
                      │  HTTPS (port 443)
                      ▼
┌─────────────────────────────────────────────────────────┐
│              Nginx Reverse Proxy                        │
│         schedulerx.askanand.me (port 80/443)            │
│         HTTP → HTTPS redirect on port 80                │
│         → forwards to localhost:8080                    │
└─────────────────────┬───────────────────────────────────┘
                      │  HTTP (port 8080, internal only)
                      ▼
┌─────────────────────────────────────────────────────────┐
│           Spring Boot Application (JAR)                 │
│                                                         │
│  ┌─────────────────┐      ┌──────────────────────────┐  │
│  │  Static Files   │      │   REST API               │  │
│  │  /static/       │      │   POST /api/simulate     │  │
│  │  index.html     │      └──────────┬───────────────┘  │
│  │  script.js      │                 │                  │
│  │  style.css      │                 ▼                  │
│  └─────────────────┘      ┌──────────────────────────┐  │
│                           │   SchedulerService       │  │
│                           │   (Algorithm Dispatcher) │  │
│                           └──────────┬───────────────┘  │
│                                      │                  │
│              ┌───────────┬───────────┼──────────┬────┐  │
│              ▼           ▼           ▼          ▼    ▼  │
│           FCFS         SJF         SRTF         RR  PRI │
│                                                         │
│                    MetricsCalculator                    │
│              (TAT, WT, RT, Averages)                    │
└─────────────────────────────────────────────────────────┘
```

### Request Flow

```
1. User fills process table in browser
2. JS collects inputs → sends POST /api/simulate
3. RateLimitFilter checks IP request count
4. @Valid validates all fields on SimulationRequest
5. SchedulerController checks for duplicate PIDs
6. Dispatches to correct algorithm service
7. Algorithm returns List<GanttBlock>
8. MetricsCalculator computes TAT, WT, RT per process + averages
9. SimulationResponse returned as JSON
10. JS renders animated Gantt chart + metrics table
```

---

## Security Architecture

### Layers of Protection

```
Incoming Request
       │
       ▼
┌─────────────────────────┐
│     RateLimitFilter     │  ← 10 req/min per IP (Fixed Window Counter)
│     (Servlet Filter)    │    Returns 429 Too Many Requests if exceeded
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│   Request Size Limit    │  ← Max 10KB body (Tomcat config)
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│   @Valid Validation     │  ← @NotBlank, @Min, @Max, @Pattern on all fields
│   (Spring Validation)   │    Returns 400 Bad Request if invalid
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│  Duplicate PID Check    │  ← Controller rejects duplicate PIDs with 400
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│    Business Logic       │  ← Algorithm runs safely within validated bounds
└─────────────────────────┘
```

### Security Measures Summary

| Threat | Measure |
|---|---|
| DoS via request flood | Rate limiting — 10 req/min per IP (no library, pure Java) |
| DoS via huge burst time | `@Max(100)` on `burstTime` |
| DoS via huge arrival time | `@Max(500)` on `arrivalTime` |
| XSS / injection via PID | `@Pattern(regexp="^[a-zA-Z0-9_-]+$")` + `@Size(max=10)` |
| Oversized request body | `server.tomcat.max-http-form-post-size=10KB` |
| Cross-origin API abuse | `@CrossOrigin(origins="https://schedulerx.askanand.me")` |
| Stack trace leakage | `spring.web.error.include-stacktrace=never` |
| Server version exposure | `server.server-header=` (blank header) |
| Duplicate process abuse | Controller-level duplicate PID rejection |

### Rate Limiting Implementation

Pure Java **Servlet Filter** — no Bucket4j or external library needed:

```
Each IP gets: { requestCount, windowStartTimestamp } stored in ConcurrentHashMap

On each request:
  → Window expired (>60s)?  → Reset count to 1, allow request
  → Count < 10?             → Increment count, allow request
  → Count >= 10?            → Return 429 "Too many requests"
```

---

## Project Structure

```
schedulrx/
├── src/
│   ├── main/
│   │   ├── java/com/schedulrx/
│   │   │   ├── SchedulrxApplication.java          ← Spring Boot entry point
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   └── SchedulerController.java        ← POST /api/simulate endpoint
│   │   │   │                                          Duplicate PID check here
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── SchedulerService.java           ← Dispatches to correct algorithm
│   │   │   │   └── algorithms/
│   │   │   │       ├── FCFSService.java
│   │   │   │       ├── SJFService.java
│   │   │   │       ├── SRTFService.java
│   │   │   │       ├── RRService.java
│   │   │   │       └── PriorityService.java        ← NP + Preemptive both inside
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── Process.java                    ← pid, arrivalTime, burstTime,
│   │   │   │   │                                      remainingTime, firstExecTime, CT
│   │   │   │   ├── GanttBlock.java                 ← pid, start, end
│   │   │   │   ├── ProcessMetrics.java             ← TAT, WT, RT per process
│   │   │   │   ├── SimulationRequest.java          ← Incoming JSON DTO + @Valid rules
│   │   │   │   └── SimulationResponse.java         ← Outgoing JSON DTO
│   │   │   │
│   │   │   ├── util/
│   │   │   │   └── MetricsCalculator.java          ← TAT, WT, RT formulas + averages
│   │   │   │
│   │   │   └── config/
│   │   │       └── RateLimitFilter.java            ← Fixed Window Counter, no library
│   │   │
│   │   └── resources/
│   │       ├── application.properties              ← Port, error config, size limits
│   │       └── static/
│   │           ├── index.html                      ← Single page UI (Tailwind)
│   │           ├── script.js                       ← Simulation logic, Gantt render,
│   │           │                                      theme toggle, priority toggle
│   │           └── style.css                       ← CSS variables, dark/light themes
│   │
│   └── test/
│       └── java/com/schedulrx/
│           ├── SchedulerControllerTest.java        ← MockMvc integration tests
│           ├── MetricsCalculatorTest.java          ← Formula unit tests
│           └── algotest/
│               ├── FCFSServiceTest.java
│               ├── SJFServiceTest.java
│               ├── SRTFServiceTest.java
│               ├── RRServiceTest.java
│               └── PriorityServiceTest.java
│
├── pom.xml
└── README.md
```

---

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+

Verify Java:
```bash
java -version
# Should show: openjdk version "17.x.x"
```

### Clone the Repository

```bash
git clone https://github.com/Anandprem-04/schedulrx.git
cd schedulrx
```

### Run Locally

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows (PowerShell)
$env:JAVA_HOME = "C:/Program Files/Java/jdk-17"
.\mvnw spring-boot:run
```

Open your browser at:
```
http://localhost:8080
```

### Build JAR

```bash
# Linux / macOS
./mvnw clean package -DskipTests

# Windows
.\mvnw clean package -DskipTests
```

JAR will be generated at:
```
target/schedulrx-0.0.1-SNAPSHOT.jar
```

Run the JAR directly:
```bash
java -jar target/schedulrx-0.0.1-SNAPSHOT.jar
```

### Run Tests

```bash
./mvnw clean test
```

Expected:
```
Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## API Reference

### `POST /api/simulate`

Simulates a CPU scheduling algorithm and returns Gantt chart blocks + performance metrics.

**Request Body:**

```json
{
  "algorithm": "RR",
  "quantum": 2,
  "processes": [
    { "pid": "P1", "arrivalTime": 0, "burstTime": 5, "priority": 1 },
    { "pid": "P2", "arrivalTime": 2, "burstTime": 3, "priority": 2 },
    { "pid": "P3", "arrivalTime": 4, "burstTime": 1, "priority": 3 }
  ]
}
```

**Request Fields:**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `algorithm` | String | ✅ | — | `FCFS`, `SJF`, `SRTF`, `RR`, `PRIORITY_NP`, `PRIORITY_P` |
| `quantum` | Integer | RR only | min: 1 | Time quantum for Round Robin |
| `processes` | Array | ✅ | 1–15 items | List of processes to simulate |
| `pid` | String | ✅ | max 10 chars, `[a-zA-Z0-9_-]` | Process identifier |
| `arrivalTime` | Integer | ✅ | 0–500 | Time process enters the ready queue |
| `burstTime` | Integer | ✅ | 1–100 | Total CPU time required |
| `priority` | Integer | Priority only | — | Lower = higher priority (default convention) |

**Response Body:**

```json
{
  "ganttBlocks": [
    { "pid": "P1", "start": 0, "end": 2 },
    { "pid": "P2", "start": 2, "end": 4 },
    { "pid": "IDLE", "start": 4, "end": 5 }
  ],
  "metrics": [
    {
      "pid": "P1",
      "arrivalTime": 0,
      "burstTime": 5,
      "completionTime": 7,
      "turnaroundTime": 7,
      "waitingTime": 2,
      "responseTime": 0
    }
  ],
  "averageWT": 2.33,
  "averageTAT": 5.67,
  "averageRT": 1.0,
  "algorithmUsed": "RR",
  "quantumUsed": 2
}
```

> `IDLE` blocks appear in `ganttBlocks` when the CPU has no process to run.

**Error Responses:**

| HTTP Status | Reason |
|---|---|
| `400 Bad Request` | Empty process list, missing algorithm, zero burst, negative arrival |
| `400 Bad Request` | Duplicate PIDs in the same request |
| `429 Too Many Requests` | Exceeded 10 requests/minute from your IP |

---

## Input Constraints

| Field | Min | Max | Notes |
|---|---|---|---|
| Processes per request | 1 | 15 | — |
| PID length | 1 | 10 chars | `[a-zA-Z0-9_-]` only |
| Arrival Time | 0 | 500 | — |
| Burst Time | 1 | 100 | — |
| Time Quantum | 1 | — | Round Robin only |
| Requests per minute | — | 10 per IP | Returns 429 if exceeded |

---

## Metrics Formulas

| Metric | Formula | What It Measures |
|---|---|---|
| **Completion Time (CT)** | When process finishes | Absolute finish time on the CPU timeline |
| **Turnaround Time (TAT)** | `CT − Arrival Time` | Total time from arrival to completion |
| **Waiting Time (WT)** | `TAT − Burst Time` | Time spent waiting in the ready queue |
| **Response Time (RT)** | `First CPU Time − Arrival Time` | Time until first CPU allocation |

Averages are the arithmetic mean of each metric across all processes.

---

## Performance

| Metric | Value |
|---|---|
| API response time (15 processes) | < 200ms |
| JVM memory footprint | ~256MB |
| Max processes per simulation | 15 |
| Estimated concurrent users (t3.micro) | ~50 |

---

## Deployment

See [cloudsetup.md](cloudsetup.md) for the complete step-by-step EC2 + Nginx + SSL deployment guide.

**Quick summary:**
1. Build JAR → `.\mvnw clean package -DskipTests`
2. Upload JAR to EC2 via `scp`
3. Configure Nginx as reverse proxy (443 → 8080)
4. Install SSL certificate
5. Run JAR as `systemd` service for auto-restart on reboot

---

## Contributing

1. Fork the repository
2. Create a feature branch
```bash
git checkout -b feature/your-feature-name
```
3. Commit your changes
```bash
git commit -m "Add: description of your change"
```
4. Push and open a Pull Request
```bash
git push origin feature/your-feature-name
```

---

## Author

**Anand Prem**

[![GitHub](https://img.shields.io/badge/GitHub-Anandprem--04-181717?style=flat-square&logo=github)](https://github.com/Anandprem-04)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Anand%20Prem-0077B5?style=flat-square&logo=linkedin)](https://www.linkedin.com/in/anand-prem-0bba95296)
[![Email](https://img.shields.io/badge/Email-anandprem2565%40gmail.com-D14836?style=flat-square&logo=gmail)](mailto:anandprem2565@gmail.com)

---


> Built with ❤️ to make OS concepts less painful for CS students everywhere.
