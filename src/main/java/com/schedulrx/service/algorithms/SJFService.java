package com.schedulrx.service.algorithms;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SJFService {

    public List<GanttBlock> simulate(List<Process> processes) {

        List<GanttBlock> gantt = new ArrayList<>();
        List<Process> remaining = new ArrayList<>(processes);
        int currentTime = 0;
        int completed = 0;
        int n = processes.size();

        while (completed < n) {

            // All processes that have arrived by currentTime
            int time = currentTime;
            List<Process> available = remaining.stream()
                    .filter(p -> p.getArrivalTime() <= time)
                    .collect(Collectors.toList());

            if (available.isEmpty()) {
                // CPU idle — jump to next arrival
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

            // Pick shortest burst time — tie break by arrival time
            Process selected = available.stream()
                    .min(Comparator.comparingInt(Process::getBurstTime)
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
}