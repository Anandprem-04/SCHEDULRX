package com.schedulrx.algotest;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.service.algorithms.SRTFService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SRTF Scheduling (Preemptive SJF) — Unit Tests")
class SRTFServiceTest {

    private SRTFService srtf;

    @BeforeEach
    void setUp() { srtf = new SRTFService(); }

    private Process proc(String pid, int at, int bt) {
        Process p = new Process();
        p.setPid(pid); p.setArrivalTime(at); p.setBurstTime(bt);
        p.initForSimulation();
        return p;
    }

    @Test
    @DisplayName("New arrival with shorter remaining time preempts running process")
    void preemptionOccurs() {
        Process p1 = proc("P1", 0, 5);
        Process p2 = proc("P2", 1, 2);
        List<GanttBlock> gantt = srtf.simulate(List.of(p1, p2));

        // P1: 0→1, P2 preempts: 1→3, P1 resumes: 3→7
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 1);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P2", 1, 3);
        assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P1", 3, 7);
    }

    @Test
    @DisplayName("Arrival with longer burst does NOT preempt")
    void noPreemptionWhenLonger() {
        Process p1 = proc("P1", 0, 3);
        Process p2 = proc("P2", 1, 5);
        List<GanttBlock> gantt = srtf.simulate(List.of(p1, p2));
        assertThat(gantt.get(0)).extracting("pid","end").containsExactly("P1", 3);
        assertThat(gantt.get(1).getPid()).isEqualTo("P2");
    }

    @Test
    @DisplayName("Classic SRTF textbook example — correct completion times")
    void textbookExample() {
        // P1(0,8) P2(1,4) P3(2,9) P4(3,5)
        Process p1 = proc("P1", 0, 8);
        Process p2 = proc("P2", 1, 4);
        Process p3 = proc("P3", 2, 9);
        Process p4 = proc("P4", 3, 5);
        srtf.simulate(List.of(p1, p2, p3, p4));
        assertThat(p2.getCompletionTime()).isEqualTo(5);
        assertThat(p4.getCompletionTime()).isEqualTo(10);
        assertThat(p1.getCompletionTime()).isEqualTo(17);
        assertThat(p3.getCompletionTime()).isEqualTo(26);
    }

    @Test
    @DisplayName("IDLE block inserted when no process available")
    void idleBlockInserted() {
        List<GanttBlock> gantt = srtf.simulate(List.of(
            proc("P1", 0, 2), proc("P2", 5, 3)
        ));
        GanttBlock idle = gantt.stream()
            .filter(b -> b.getPid().equals("IDLE")).findFirst().orElseThrow();
        assertThat(idle.getStart()).isEqualTo(2);
        assertThat(idle.getEnd()).isEqualTo(5);
    }

    @Test
    @DisplayName("Gantt blocks form a continuous timeline — no gaps")
    void continuousTimeline() {
        List<GanttBlock> gantt = srtf.simulate(List.of(
            proc("P1", 0, 4), proc("P2", 2, 2), proc("P3", 3, 5)
        ));
        for (int i = 1; i < gantt.size(); i++) {
            assertThat(gantt.get(i).getStart()).isEqualTo(gantt.get(i-1).getEnd());
        }
    }

    @Test
    @DisplayName("All processes complete — remaining time = 0")
    void allProcessesComplete() {
        List<Process> procs = List.of(
            proc("P1", 0, 3), proc("P2", 1, 4), proc("P3", 2, 2)
        );
        srtf.simulate(procs);
        procs.forEach(p -> assertThat(p.getRemainingTime()).isEqualTo(0));
    }

    @Test
    @DisplayName("First execution time recorded correctly")
    void firstExecutionTimeRecorded() {
        Process p1 = proc("P1", 0, 5);
        Process p2 = proc("P2", 1, 2);
        srtf.simulate(List.of(p1, p2));
        assertThat(p1.getFirstExecutionTime()).isEqualTo(0);
        assertThat(p2.getFirstExecutionTime()).isEqualTo(1);
    }

    @Test
    @DisplayName("Single process — runs straight through")
    void singleProcess() {
        Process p = proc("P1", 0, 4);
        srtf.simulate(List.of(p));
        assertThat(p.getCompletionTime()).isEqualTo(4);
    }
}
