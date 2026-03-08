package com.schedulrx.algotest;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.service.algorithms.RRService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Round Robin Scheduling — Unit Tests")
class RRServiceTest {

    private RRService rr;

    @BeforeEach
    void setUp() { rr = new RRService(); }

    private Process proc(String pid, int at, int bt) {
        Process p = new Process();
        p.setPid(pid); p.setArrivalTime(at); p.setBurstTime(bt);
        p.initForSimulation();
        return p;
    }

    @Test
    @DisplayName("Two processes Q=2 — interleave correctly")
    void twoProcessesInterleave() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 4), proc("P2", 0, 4)
        ), 2);
        assertThat(gantt).hasSize(4);
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 2);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P2", 2, 4);
        assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P1", 4, 6);
        assertThat(gantt.get(3)).extracting("pid","start","end").containsExactly("P2", 6, 8);
    }

    @Test
    @DisplayName("Process finishes within quantum — no wasted time slice")
    void processFinishesWithinQuantum() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 3), proc("P2", 0, 1)
        ), 3);
        GanttBlock p2Block = gantt.stream()
            .filter(b -> b.getPid().equals("P2")).findFirst().orElseThrow();
        assertThat(p2Block.getEnd() - p2Block.getStart()).isEqualTo(1);
    }

    @Test
    @DisplayName("Q=1 — processes alternate every single unit")
    void quantumOne() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 2), proc("P2", 0, 2)
        ), 1);
        assertThat(gantt).hasSize(4);
        assertThat(gantt.get(0).getPid()).isEqualTo("P1");
        assertThat(gantt.get(1).getPid()).isEqualTo("P2");
        assertThat(gantt.get(2).getPid()).isEqualTo("P1");
        assertThat(gantt.get(3).getPid()).isEqualTo("P2");
    }

    @Test
    @DisplayName("Quantum larger than all burst times — behaves like FCFS")
    void largeQuantumLikesFCFS() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 3), proc("P2", 0, 2), proc("P3", 0, 4)
        ), 100);
        assertThat(gantt).hasSize(3);
        assertThat(gantt).extracting("pid").containsExactly("P1", "P2", "P3");
    }

    @Test
    @DisplayName("New arrival joins queue after current quantum ends")
    void newArrivalJoinsAfterQuantum() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 4), proc("P2", 1, 2)
        ), 2);
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 2);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P2", 2, 4);
        assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P1", 4, 6);
    }

    @Test
    @DisplayName("IDLE block inserted when no process available")
    void idleBlockInserted() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 2), proc("P2", 6, 2)
        ), 2);
        assertThat(gantt.stream().anyMatch(b -> b.getPid().equals("IDLE"))).isTrue();
    }

    @Test
    @DisplayName("All processes complete — remaining time = 0")
    void allProcessesComplete() {
        List<Process> procs = List.of(
            proc("P1", 0, 5), proc("P2", 1, 3), proc("P3", 2, 4)
        );
        rr.simulate(procs, 2);
        procs.forEach(p -> assertThat(p.getRemainingTime()).isEqualTo(0));
    }

    @Test
    @DisplayName("Completion times set correctly Q=2")
    void completionTimesSet() {
        Process p1 = proc("P1", 0, 4);
        Process p2 = proc("P2", 0, 4);
        rr.simulate(List.of(p1, p2), 2);
        assertThat(p1.getCompletionTime()).isEqualTo(6);
        assertThat(p2.getCompletionTime()).isEqualTo(8);
    }

    @Test
    @DisplayName("Gantt blocks form a continuous timeline")
    void continuousTimeline() {
        List<GanttBlock> gantt = rr.simulate(List.of(
            proc("P1", 0, 5), proc("P2", 0, 3), proc("P3", 0, 4)
        ), 2);
        for (int i = 1; i < gantt.size(); i++) {
            assertThat(gantt.get(i).getStart()).isEqualTo(gantt.get(i-1).getEnd());
        }
    }

    @Test
    @DisplayName("Textbook RR: P1(0,24) P2(0,3) P3(0,3) Q=4 — total time = 30")
    void textbookExample() {
        List<Process> procs = List.of(
            proc("P1", 0, 24), proc("P2", 0, 3), proc("P3", 0, 3)
        );
        rr.simulate(procs, 4);
        int lastEnd = procs.stream().mapToInt(Process::getCompletionTime).max().orElse(0);
        assertThat(lastEnd).isEqualTo(30);
    }
}
