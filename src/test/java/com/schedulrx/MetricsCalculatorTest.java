package com.schedulrx;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.model.ProcessMetrics;
import com.schedulrx.util.MetricsCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MetricsCalculator — Unit Tests")
class MetricsCalculatorTest {

    private MetricsCalculator calculator;

    @BeforeEach
    void setUp() { calculator = new MetricsCalculator(); }

    private Process completedProc(String pid, int at, int bt, int ct, int fet) {
        Process p = new Process();
        p.setPid(pid); p.setArrivalTime(at); p.setBurstTime(bt);
        p.setCompletionTime(ct); p.setFirstExecutionTime(fet);
        return p;
    }

    private ProcessMetrics buildMetrics(String pid, int tat, int wt, int rt) {
        ProcessMetrics m = new ProcessMetrics();
        m.setPid(pid); m.setTurnaroundTime(tat);
        m.setWaitingTime(wt); m.setResponseTime(rt);
        return m;
    }

    // ── Metric formulas ───────────────────────────────────────────

    @Test
    @DisplayName("TAT = CT - AT")
    void turnaroundTimeFormula() {
        Process p = completedProc("P1", 0, 5, 5, 0);
        assertThat(calculator.calculate(List.of(p)).get(0).getTurnaroundTime()).isEqualTo(5);
    }

    @Test
    @DisplayName("WT = TAT - BT")
    void waitingTimeFormula() {
        Process p = completedProc("P1", 0, 3, 7, 0);
        assertThat(calculator.calculate(List.of(p)).get(0).getWaitingTime()).isEqualTo(4);
    }

    @Test
    @DisplayName("RT = FirstExecutionTime - AT")
    void responseTimeFormula() {
        Process p = completedProc("P1", 2, 3, 8, 5);
        assertThat(calculator.calculate(List.of(p)).get(0).getResponseTime()).isEqualTo(3);
    }

    @Test
    @DisplayName("Zero wait — process runs immediately on arrival")
    void zeroWaitTime() {
        Process p = completedProc("P1", 0, 4, 4, 0);
        ProcessMetrics m = calculator.calculate(List.of(p)).get(0);
        assertThat(m.getWaitingTime()).isEqualTo(0);
        assertThat(m.getResponseTime()).isEqualTo(0);
    }

    @Test
    @DisplayName("All fields echoed correctly in metrics output")
    void allFieldsEchoed() {
        Process p = completedProc("P2", 3, 5, 10, 4);
        ProcessMetrics m = calculator.calculate(List.of(p)).get(0);
        assertThat(m.getPid()).isEqualTo("P2");
        assertThat(m.getArrivalTime()).isEqualTo(3);
        assertThat(m.getBurstTime()).isEqualTo(5);
        assertThat(m.getCompletionTime()).isEqualTo(10);
        assertThat(m.getTurnaroundTime()).isEqualTo(7);
        assertThat(m.getWaitingTime()).isEqualTo(2);
        assertThat(m.getResponseTime()).isEqualTo(1);
    }

    // ── Averages ─────────────────────────────────────────────────

    @Test
    @DisplayName("Average WT is arithmetic mean")
    void averageWaitingTime() {
        List<ProcessMetrics> metrics = List.of(
            buildMetrics("P1", 0, 2, 0),
            buildMetrics("P2", 0, 4, 0),
            buildMetrics("P3", 0, 2, 0)
        );
        assertThat(calculator.averageWT(metrics)).isCloseTo(2.67, within(0.01));
    }

    @Test
    @DisplayName("Average TAT is arithmetic mean")
    void averageTurnaroundTime() {
        List<ProcessMetrics> metrics = List.of(
            buildMetrics("P1", 5, 0, 0),
            buildMetrics("P2", 3, 0, 0),
            buildMetrics("P3", 7, 0, 0)
        );
        assertThat(calculator.averageTAT(metrics)).isCloseTo(5.0, within(0.01));
    }

    @Test
    @DisplayName("Average RT is arithmetic mean")
    void averageResponseTime() {
        List<ProcessMetrics> metrics = List.of(
            buildMetrics("P1", 0, 0, 0),
            buildMetrics("P2", 0, 0, 4),
            buildMetrics("P3", 0, 0, 2)
        );
        assertThat(calculator.averageRT(metrics)).isCloseTo(2.0, within(0.01));
    }

    @Test
    @DisplayName("Single process averages equal individual values")
    void singleProcessAverages() {
        List<ProcessMetrics> metrics = List.of(buildMetrics("P1", 6, 3, 1));
        assertThat(calculator.averageTAT(metrics)).isEqualTo(6.0);
        assertThat(calculator.averageWT(metrics)).isEqualTo(3.0);
        assertThat(calculator.averageRT(metrics)).isEqualTo(1.0);
    }

    // ── mergeConsecutiveBlocks ─────────────────────────────────────

    @Test
    @DisplayName("Consecutive same-PID blocks are merged into one")
    void mergesConsecutiveBlocks() {
        List<GanttBlock> merged = calculator.mergeConsecutiveBlocks(List.of(
            GanttBlock.builder().pid("P1").start(0).end(2).build(),
            GanttBlock.builder().pid("P1").start(2).end(4).build(),
            GanttBlock.builder().pid("P2").start(4).end(6).build()
        ));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0)).extracting("pid","start","end").containsExactly("P1", 0, 4);
        assertThat(merged.get(1)).extracting("pid","start","end").containsExactly("P2", 4, 6);
    }

    @Test
    @DisplayName("Non-consecutive same-PID blocks are NOT merged")
    void doesNotMergeNonConsecutive() {
        List<GanttBlock> merged = calculator.mergeConsecutiveBlocks(List.of(
            GanttBlock.builder().pid("P1").start(0).end(2).build(),
            GanttBlock.builder().pid("P2").start(2).end(4).build(),
            GanttBlock.builder().pid("P1").start(4).end(6).build()
        ));
        assertThat(merged).hasSize(3);
    }

    @Test
    @DisplayName("Empty block list returns empty")
    void emptyBlockList() {
        assertThat(calculator.mergeConsecutiveBlocks(List.of())).isEmpty();
    }

    @Test
    @DisplayName("All blocks same PID — fully merged into one")
    void allSamePidFullyMerged() {
        List<GanttBlock> merged = calculator.mergeConsecutiveBlocks(List.of(
            GanttBlock.builder().pid("P1").start(0).end(1).build(),
            GanttBlock.builder().pid("P1").start(1).end(2).build(),
            GanttBlock.builder().pid("P1").start(2).end(3).build(),
            GanttBlock.builder().pid("P1").start(3).end(4).build()
        ));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0)).extracting("start","end").containsExactly(0, 4);
    }
}
