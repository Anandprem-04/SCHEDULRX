package com.schedulrx.algotest;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.service.algorithms.SJFService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SJF Scheduling (Non-Preemptive) — Unit Tests")
class SJFServiceTest {

    private SJFService sjf;

    @BeforeEach
    void setUp() { sjf = new SJFService(); }

    private Process proc(String pid, int at, int bt) {
        Process p = new Process();
        p.setPid(pid); p.setArrivalTime(at); p.setBurstTime(bt);
        p.initForSimulation();
        return p;
    }

    @Test
    @DisplayName("All arrive at t=0 — shortest burst runs first")
    void shortestFirstWhenAllArrive() {
        List<GanttBlock> gantt = sjf.simulate(List.of(
            proc("P1", 0, 6), proc("P2", 0, 2), proc("P3", 0, 4)
        ));
        assertThat(gantt).extracting("pid").containsExactly("P2", "P3", "P1");
    }

    @Test
    @DisplayName("Non-preemptive — running process not interrupted by shorter arrival")
    void nonPreemptive_noPreemption() {
        Process p1 = proc("P1", 0, 5);
        Process p2 = proc("P2", 1, 2);
        List<GanttBlock> gantt = sjf.simulate(List.of(p1, p2));
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 5);
        assertThat(gantt.get(1).getPid()).isEqualTo("P2");
    }

    @Test
    @DisplayName("Textbook SJF example — correct order and times")
    void textbookExample() {
        // P1(0,7) P2(2,4) P3(4,1) P4(5,4)
        // Expected: P1(0→7), P3(7→8), P2(8→12), P4(12→16)
        List<GanttBlock> gantt = sjf.simulate(List.of(
            proc("P1", 0, 7), proc("P2", 2, 4),
            proc("P3", 4, 1), proc("P4", 5, 4)
        ));
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 7);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P3", 7, 8);
        assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P2", 8, 12);
        assertThat(gantt.get(3)).extracting("pid","start","end").containsExactly("P4", 12, 16);
    }

    @Test
    @DisplayName("Equal burst times — tie broken by arrival time")
    void tieBreakByArrival() {
        List<GanttBlock> gantt = sjf.simulate(List.of(
            proc("P1", 2, 3), proc("P2", 1, 3), proc("P3", 0, 3)
        ));
        assertThat(gantt).extracting("pid").containsExactly("P3", "P2", "P1");
    }

    @Test
    @DisplayName("IDLE block inserted when CPU has nothing to run")
    void idleBlockInserted() {
        List<GanttBlock> gantt = sjf.simulate(List.of(
            proc("P1", 0, 3), proc("P2", 8, 2)
        ));
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("IDLE", 3, 8);
    }

    @Test
    @DisplayName("Completion times correctly set on all processes")
    void completionTimesCorrect() {
        Process p1 = proc("P1", 0, 6);
        Process p2 = proc("P2", 0, 2);
        Process p3 = proc("P3", 0, 4);
        sjf.simulate(List.of(p1, p2, p3));
        assertThat(p2.getCompletionTime()).isEqualTo(2);
        assertThat(p3.getCompletionTime()).isEqualTo(6);
        assertThat(p1.getCompletionTime()).isEqualTo(12);
    }

    @Test
    @DisplayName("Single process completes correctly")
    void singleProcess() {
        Process p = proc("P1", 0, 4);
        sjf.simulate(List.of(p));
        assertThat(p.getCompletionTime()).isEqualTo(4);
    }

    @Test
    @DisplayName("All same arrival, different burst — ordered by burst")
    void allSameArrivalDifferentBurst() {
        List<GanttBlock> gantt = sjf.simulate(List.of(
            proc("P1", 0, 10), proc("P2", 0, 1),
            proc("P3", 0, 5),  proc("P4", 0, 3)
        ));
        assertThat(gantt).extracting("pid").containsExactly("P2", "P4", "P3", "P1");
    }
}
