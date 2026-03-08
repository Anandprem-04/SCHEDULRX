package com.schedulrx.algotest;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.service.algorithms.FCFSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FCFS Scheduling — Unit Tests")
class FCFSServiceTest {

    private FCFSService fcfs;

    @BeforeEach
    void setUp() { fcfs = new FCFSService(); }

    private Process proc(String pid, int at, int bt) {
        Process p = new Process();
        p.setPid(pid); p.setArrivalTime(at); p.setBurstTime(bt);
        p.initForSimulation();
        return p;
    }

    @Test
    @DisplayName("Single process — runs from arrival to completion")
    void singleProcess() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(proc("P1", 0, 5)));
        assertThat(gantt).hasSize(1);
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 5);
    }

    @Test
    @DisplayName("Three processes same arrival — executes in input order")
    void threeProcessesSameArrival() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(
            proc("P1", 0, 3), proc("P2", 0, 2), proc("P3", 0, 4)
        ));
        assertThat(gantt).hasSize(3);
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 3);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P2", 3, 5);
        assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P3", 5, 9);
    }

    @Test
    @DisplayName("Staggered arrivals — correct execution order and times")
    void staggeredArrivals() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(
            proc("P1", 0, 4), proc("P2", 2, 3), proc("P3", 5, 2)
        ));
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 4);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P2", 4, 7);
        assertThat(gantt.get(2)).extracting("pid","start","end").containsExactly("P3", 7, 9);
    }

    @Test
    @DisplayName("Gap between processes — IDLE block inserted")
    void idleBlockInserted() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(
            proc("P1", 0, 2), proc("P2", 5, 3)
        ));
        assertThat(gantt).hasSize(3);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("IDLE", 2, 5);
    }

    @Test
    @DisplayName("First process arrives late — IDLE block at start")
    void idleAtStart() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(proc("P1", 3, 2)));
        assertThat(gantt).hasSize(2);
        assertThat(gantt.get(0)).extracting("pid","start","end").containsExactly("IDLE", 0, 3);
        assertThat(gantt.get(1)).extracting("pid","start","end").containsExactly("P1", 3, 5);
    }

    @Test
    @DisplayName("Completion times set correctly on Process objects")
    void completionTimesSet() {
        Process p1 = proc("P1", 0, 3);
        Process p2 = proc("P2", 0, 2);
        fcfs.simulate(List.of(p1, p2));
        assertThat(p1.getCompletionTime()).isEqualTo(3);
        assertThat(p2.getCompletionTime()).isEqualTo(5);
    }

    @Test
    @DisplayName("First execution time set correctly")
    void firstExecutionTimeSet() {
        Process p1 = proc("P1", 0, 3);
        Process p2 = proc("P2", 1, 2);
        fcfs.simulate(List.of(p1, p2));
        assertThat(p1.getFirstExecutionTime()).isEqualTo(0);
        assertThat(p2.getFirstExecutionTime()).isEqualTo(3);
    }

    @Test
    @DisplayName("No IDLE blocks when processes arrive back-to-back")
    void noIdleWhenContinuous() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(
            proc("P1", 0, 3), proc("P2", 3, 2), proc("P3", 5, 1)
        ));
        assertThat(gantt).noneMatch(b -> b.getPid().equals("IDLE"));
    }

    @Test
    @DisplayName("Textbook convoy effect — P1(0,24) blocks P2 and P3")
    void textbookConvoyEffect() {
        List<GanttBlock> gantt = fcfs.simulate(List.of(
            proc("P1", 0, 24), proc("P2", 0, 3), proc("P3", 0, 3)
        ));
        assertThat(gantt.get(0)).extracting("pid","end").containsExactly("P1", 24);
        assertThat(gantt.get(1)).extracting("pid","end").containsExactly("P2", 27);
        assertThat(gantt.get(2)).extracting("pid","end").containsExactly("P3", 30);
    }
}
