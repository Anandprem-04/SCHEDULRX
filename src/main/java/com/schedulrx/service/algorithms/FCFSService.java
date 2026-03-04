package com.schedulrx.service.algorithms;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FCFSService {

    public List<GanttBlock> simulate(List<Process> processes) {

        // Sort by arrival time
        List<Process> sorted = new ArrayList<>(processes);
        sorted.sort(Comparator.comparingInt(Process::getArrivalTime));

        List<GanttBlock> gantt = new ArrayList<>();
        int currentTime = 0;

        for (Process p : sorted) {

            // CPU is idle — no process has arrived yet
            if (currentTime < p.getArrivalTime()) {
                gantt.add(GanttBlock.builder()
                        .pid(GanttBlock.IDLE_PID)
                        .start(currentTime)
                        .end(p.getArrivalTime())
                        .build());
                currentTime = p.getArrivalTime();
            }

            // First time this process touches the CPU
            p.setFirstExecutionTime(currentTime);

            int endTime = currentTime + p.getBurstTime();

            gantt.add(GanttBlock.builder()
                    .pid(p.getPid())
                    .start(currentTime)
                    .end(endTime)
                    .build());

            p.setCompletionTime(endTime);
            currentTime = endTime;
        }

        return gantt;
    }
}