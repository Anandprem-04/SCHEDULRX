package com.schedulrx.util;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.model.ProcessMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MetricsCalculator {

    // Build per-process metrics after simulation completes
    public List<ProcessMetrics> calculate(List<Process> processes) {
        return processes.stream()
                .map(p -> ProcessMetrics.builder()
                        .pid(p.getPid())
                        .arrivalTime(p.getArrivalTime())
                        .burstTime(p.getBurstTime())
                        .completionTime(p.getCompletionTime())
                        .turnaroundTime(p.getCompletionTime() - p.getArrivalTime())
                        .waitingTime((p.getCompletionTime() - p.getArrivalTime()) - p.getBurstTime())
                        .responseTime(p.getFirstExecutionTime() - p.getArrivalTime())
                        .build())
                .collect(Collectors.toList());
    }

    public double averageWT(List<ProcessMetrics> metrics) {
        return metrics.stream()
                .mapToInt(ProcessMetrics::getWaitingTime)
                .average()
                .orElse(0.0);
    }

    public double averageTAT(List<ProcessMetrics> metrics) {
        return metrics.stream()
                .mapToInt(ProcessMetrics::getTurnaroundTime)
                .average()
                .orElse(0.0);
    }

    public double averageRT(List<ProcessMetrics> metrics) {
        return metrics.stream()
                .mapToInt(ProcessMetrics::getResponseTime)
                .average()
                .orElse(0.0);
    }

    // Merges consecutive blocks with same PID
    // Example: [{A,0,2},{A,2,4}] → [{A,0,4}]
    // Keeps Gantt chart clean especially for non-preemptive algorithms
    public List<GanttBlock> mergeConsecutiveBlocks(List<GanttBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return blocks;

        List<GanttBlock> merged = new ArrayList<>();
        GanttBlock current = blocks.get(0);

        for (int i = 1; i < blocks.size(); i++) {
            GanttBlock next = blocks.get(i);
            if (next.getPid().equals(current.getPid()) && next.getStart() == current.getEnd()) {
                current = GanttBlock.builder()
                        .pid(current.getPid())
                        .start(current.getStart())
                        .end(next.getEnd())
                        .build();
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}