package com.schedulrx.service.algorithms;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@Service
public class RRService {

    public List<GanttBlock> simulate(List<Process> processes, int quantum) {

        List<GanttBlock> gantt = new ArrayList<>();

        // Sort by arrival time initially
        List<Process> allProcesses = new ArrayList<>(processes);
        allProcesses.sort(Comparator.comparingInt(Process::getArrivalTime));

        Queue<Process> readyQueue = new LinkedList<>();
        int currentTime = 0;
        int index = 0; // pointer into sorted allProcesses
        int n = allProcesses.size();
        int completed = 0;

        // Seed queue with processes arriving at time 0
        while (index < n && allProcesses.get(index).getArrivalTime() <= currentTime) {
            readyQueue.add(allProcesses.get(index++));
        }

        while (completed < n) {

            if (readyQueue.isEmpty()) {
                // CPU idle — jump to next arrival
                int nextArrival = allProcesses.get(index).getArrivalTime();
                gantt.add(GanttBlock.builder()
                        .pid(GanttBlock.IDLE_PID)
                        .start(currentTime)
                        .end(nextArrival)
                        .build());
                currentTime = nextArrival;

                // Enqueue newly arrived processes
                while (index < n && allProcesses.get(index).getArrivalTime() <= currentTime) {
                    readyQueue.add(allProcesses.get(index++));
                }
                continue;
            }

            Process current = readyQueue.poll();

            // Record first execution for response time
            if (current.getFirstExecutionTime() == -1) {
                current.setFirstExecutionTime(currentTime);
            }

            // Run for quantum or remaining time — whichever is smaller
            int runTime = Math.min(quantum, current.getRemainingTime());
            int sliceEnd = currentTime + runTime;

            gantt.add(GanttBlock.builder()
                    .pid(current.getPid())
                    .start(currentTime)
                    .end(sliceEnd)
                    .build());

            current.setRemainingTime(current.getRemainingTime() - runTime);
            currentTime = sliceEnd;

            // Enqueue new arrivals BEFORE re-queuing preempted process
            // This is standard OS textbook behaviour
            while (index < n && allProcesses.get(index).getArrivalTime() <= currentTime) {
                readyQueue.add(allProcesses.get(index++));
            }

            if (current.getRemainingTime() == 0) {
                // Process finished
                current.setCompletionTime(currentTime);
                completed++;
            } else {
                // Not finished — go to back of queue
                readyQueue.add(current);
            }
        }

        return gantt;
    }
}