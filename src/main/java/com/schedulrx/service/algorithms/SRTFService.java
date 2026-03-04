package com.schedulrx.service.algorithms;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SRTFService {

    public List<GanttBlock> simulate(List<Process> processes) {

        List<GanttBlock> gantt = new ArrayList<>();
        List<Process> remaining = new ArrayList<>(processes);

        int currentTime = 0;
        int completed = 0;
        int n = processes.size();

        String lastPid = null;
        int blockStart = 0;

        while (completed < n) {

            int time = currentTime; // effectively final for lambda

            // All arrived processes with remaining burst > 0
            List<Process> available = remaining.stream()
                    .filter(p -> p.getArrivalTime() <= time && p.getRemainingTime() > 0)
                    .collect(Collectors.toList());

            if (available.isEmpty()) {
                // CPU idle
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

            // Pick process with shortest remaining time
            // Tie break by arrival time
            Process selected = available.stream()
                    .min(Comparator.comparingInt(Process::getRemainingTime)
                            .thenComparingInt(Process::getArrivalTime))
                    .orElseThrow();

            // Record first execution for response time
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

            // Process finished
            if (selected.getRemainingTime() == 0) {
                selected.setCompletionTime(currentTime);
                remaining.remove(selected);
                completed++;

                // Flush completed block
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