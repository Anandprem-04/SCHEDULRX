package com.schedulrx.service.algorithms;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PriorityService {

    // -------------------------------------------------------
    // Non-Preemptive — once selected, runs to completion
    // -------------------------------------------------------
    public List<GanttBlock> simulateNonPreemptive(List<Process> processes) {

        List<GanttBlock> gantt = new ArrayList<>();
        List<Process> remaining = new ArrayList<>(processes);
        int currentTime = 0;
        int completed = 0;
        int n = processes.size();

        while (completed < n) {

            int time = currentTime; // effectively final for lambda
            List<Process> available = remaining.stream()
                    .filter(p -> p.getArrivalTime() <= time)
                    .toList();

            if (available.isEmpty()) {
                int nextArrival = remaining.stream()
                        .mapToInt(Process::getArrivalTime)
                        .min()
                        .orElse(currentTime + 1);

                gantt.add(GanttBlock.builder()
                        .pid(GanttBlock.IDLE_PID)
                        .start(currentTime)
                        .end(nextArrival)
                        .build());

                currentTime = nextArrival;
                continue;
            }

            // Lower number = higher priority, tie break by arrival time
            Process selected = available.stream()
                    .min(Comparator.comparingInt(Process::getPriority)
                            .thenComparingInt(Process::getArrivalTime))
                    .orElseThrow();

            selected.setFirstExecutionTime(currentTime);
            int endTime = currentTime + selected.getBurstTime();

            gantt.add(GanttBlock.builder()
                    .pid(selected.getPid())
                    .start(currentTime)
                    .end(endTime)
                    .build());

            selected.setCompletionTime(endTime);
            currentTime = endTime;
            remaining.remove(selected);
            completed++;
        }

        return gantt;
    }

    // -------------------------------------------------------
    // Preemptive — higher priority arrival preempts running process
    // -------------------------------------------------------
    public List<GanttBlock> simulatePreemptive(List<Process> processes) {

        List<GanttBlock> gantt = new ArrayList<>();
        List<Process> remaining = new ArrayList<>(processes);

        int currentTime = 0;
        int completed = 0;
        int n = processes.size();

        String lastPid = null;
        int blockStart = 0;

        while (completed < n) {

            int time = currentTime; // effectively final for lambda
            List<Process> available = remaining.stream()
                    .filter(p -> p.getArrivalTime() <= time && p.getRemainingTime() > 0)
                    .toList();

            if (available.isEmpty()) {
                if (!GanttBlock.IDLE_PID.equals(lastPid)) {
                    if (lastPid != null) {
                        gantt.add(GanttBlock.builder()
                                .pid(lastPid)
                                .start(blockStart)
                                .end(currentTime)
                                .build());
                    }
                    blockStart = currentTime;
                    lastPid = GanttBlock.IDLE_PID;
                }
                currentTime++;
                continue;
            }

            // Pick highest priority process
            Process selected = available.stream()
                    .min(Comparator.comparingInt(Process::getPriority)
                            .thenComparingInt(Process::getArrivalTime))
                    .orElseThrow();

            if (selected.getFirstExecutionTime() == -1) {
                selected.setFirstExecutionTime(currentTime);
            }

            // Context switch detected — flush previous block
            if (!selected.getPid().equals(lastPid)) {
                if (lastPid != null) {
                    gantt.add(GanttBlock.builder()
                            .pid(lastPid)
                            .start(blockStart)
                            .end(currentTime)
                            .build());
                }
                blockStart = currentTime;
                lastPid = selected.getPid();
            }

            // Execute one tick
            selected.setRemainingTime(selected.getRemainingTime() - 1);
            currentTime++;

            if (selected.getRemainingTime() == 0) {
                selected.setCompletionTime(currentTime);
                remaining.remove(selected);
                completed++;

                gantt.add(GanttBlock.builder()
                        .pid(lastPid)
                        .start(blockStart)
                        .end(currentTime)
                        .build());
                lastPid = null;
                blockStart = currentTime;
            }
        }

        return gantt;
    }
}
