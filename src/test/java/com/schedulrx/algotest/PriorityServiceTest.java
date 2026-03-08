package com.schedulrx.algotest;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.service.algorithms.PriorityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Priority Scheduling — Unit Tests")
class PriorityServiceTest {

    private PriorityService priority;

    @BeforeEach
    void setUp() { priority = new PriorityService(); }

    private Process proc(String pid, int at, int bt, int prio) {
        Process p = new Process();
        p.setPid(pid); p.setArrivalTime(at);
        p.setBurstTime(bt); p.setPriority(prio);
        p.initForSimulation();
        return p;
    }

    @Nested
    @DisplayName("Non-Preemptive Priority")
    class NonPreemptive {

        @Test
        @DisplayName("Lower number = higher priority — runs first")
        void lowerNumberHigherPriority() {
            List<GanttBlock> gantt = priority.simulateNonPreemptive(List.of(
                proc("P1", 0, 3, 3),
                proc("P2", 0, 2, 1),
                proc("P3", 0, 4, 2)
            ));
            assertThat(gantt).extracting("pid").containsExactly("P2", "P3", "P1");
        }

        @Test
        @DisplayName("Non-preemptive — high priority arrival does NOT interrupt running process")
        void noPreemptionDespiteHigherPriority() {
            Process p1 = proc("P1", 0, 5, 3);
            Process p2 = proc("P2", 1, 2, 1);
            List<GanttBlock> gantt = priority.simulateNonPreemptive(List.of(p1, p2));
            assertThat(gantt.get(0)).extracting("pid","end").containsExactly("P1", 5);
            assertThat(gantt.get(1).getPid()).isEqualTo("P2");
        }

        @Test
        @DisplayName("Tie in priority — broken by arrival time")
        void tieBreakByArrival() {
            List<GanttBlock> gantt = priority.simulateNonPreemptive(List.of(
                proc("P1", 3, 3, 1),
                proc("P2", 1, 3, 1),
                proc("P3", 2, 3, 1)
            ));
            // IDLE from 0→1 since earliest arrival is t=1
            assertThat(gantt).extracting("pid").containsExactly("IDLE", "P2", "P3", "P1");
        }

        @Test
        @DisplayName("IDLE block inserted when no process arrived yet")
        void idleBlockInserted() {
            List<GanttBlock> gantt = priority.simulateNonPreemptive(List.of(
                proc("P1", 3, 2, 1), proc("P2", 6, 3, 2)
            ));
            assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("IDLE", 0, 3);
        }

        @Test
        @DisplayName("Completion times set correctly")
        void completionTimesCorrect() {
            Process p1 = proc("P1", 0, 3, 2);
            Process p2 = proc("P2", 0, 2, 1);
            priority.simulateNonPreemptive(List.of(p1, p2));
            assertThat(p2.getCompletionTime()).isEqualTo(2);
            assertThat(p1.getCompletionTime()).isEqualTo(5);
        }

        @Test
        @DisplayName("Single process completes correctly")
        void singleProcess() {
            Process p = proc("P1", 0, 4, 1);
            priority.simulateNonPreemptive(List.of(p));
            assertThat(p.getCompletionTime()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Preemptive Priority")
    class Preemptive {

        @Test
        @DisplayName("Higher priority arrival preempts running process")
        void preemptionOccurs() {
            Process p1 = proc("P1", 0, 6, 3);
            Process p2 = proc("P2", 2, 3, 1);
            List<GanttBlock> gantt = priority.simulatePreemptive(List.of(p1, p2));
            // P1: 0→2, P2 preempts: 2→5, P1 resumes: 5→9
            assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 2);
            assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P2", 2, 5);
            assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P1", 5, 9);
        }

        @Test
        @DisplayName("Lower priority arrival does NOT preempt running process")
        void lowerPriorityNoPreempt() {
            Process p1 = proc("P1", 0, 5, 1);
            Process p2 = proc("P2", 2, 3, 3);
            List<GanttBlock> gantt = priority.simulatePreemptive(List.of(p1, p2));
            assertThat(gantt.get(0)).extracting("pid","end").containsExactly("P1", 5);
        }

        @Test
        @DisplayName("Higher priority process finishes first")
        void higherPriorityFinishesFirst() {
            Process p1 = proc("P1", 0, 10, 3);
            Process p2 = proc("P2", 2,  5, 2);
            Process p3 = proc("P3", 4,  3, 1);
            priority.simulatePreemptive(List.of(p1, p2, p3));
            assertThat(p3.getCompletionTime()).isLessThan(p2.getCompletionTime());
            assertThat(p2.getCompletionTime()).isLessThan(p1.getCompletionTime());
        }

        @Test
        @DisplayName("All processes complete — remaining time = 0")
        void allProcessesComplete() {
            List<Process> procs = List.of(
                proc("P1", 0, 4, 2),
                proc("P2", 1, 3, 1),
                proc("P3", 3, 2, 3)
            );
            priority.simulatePreemptive(procs);
            procs.forEach(p -> assertThat(p.getRemainingTime()).isEqualTo(0));
        }

        @Test
        @DisplayName("Gantt blocks form a continuous timeline")
        void continuousTimeline() {
            List<GanttBlock> gantt = priority.simulatePreemptive(List.of(
                proc("P1", 0, 5, 2),
                proc("P2", 2, 3, 1),
                proc("P3", 4, 4, 3)
            ));
            for (int i = 1; i < gantt.size(); i++) {
                assertThat(gantt.get(i).getStart()).isEqualTo(gantt.get(i-1).getEnd());
            }
        }

        @Test
        @DisplayName("First execution times set correctly")
        void firstExecutionTimesSet() {
            Process p1 = proc("P1", 0, 6, 2);
            Process p2 = proc("P2", 3, 2, 1);
            priority.simulatePreemptive(List.of(p1, p2));
            assertThat(p1.getFirstExecutionTime()).isEqualTo(0);
            assertThat(p2.getFirstExecutionTime()).isEqualTo(3);
        }
    }
}
