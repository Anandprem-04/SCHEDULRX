package com.schedulrx.service;

import com.schedulrx.model.GanttBlock;
import com.schedulrx.model.Process;
import com.schedulrx.model.ProcessMetrics;
import com.schedulrx.model.SimulationRequest;
import com.schedulrx.model.SimulationResponse;
import com.schedulrx.service.algorithms.FCFSService;
import com.schedulrx.service.algorithms.SJFService;
import com.schedulrx.service.algorithms.SRTFService;
import com.schedulrx.service.algorithms.RRService;
import com.schedulrx.service.algorithms.PriorityService;
import com.schedulrx.util.MetricsCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final FCFSService fcfsService;
    private final SJFService sjfService;
    private final SRTFService srtfService;
    private final RRService rrService;
    private final PriorityService priorityService;
    private final MetricsCalculator metricsCalculator;

    public enum Algorithm {
        FCFS,
        SJF,
        SRTF,
        RR,
        PRIORITY_NP,
        PRIORITY_P
    }

    public SimulationResponse simulate(SimulationRequest request) {
        Algorithm algo = parseAlgorithm(request.getAlgorithm());

        log.debug("Simulation started: algorithm={}, processes={}",
                algo, request.getProcesses().size());

        // Validate quantum for RR
        if (algo == Algorithm.RR && (request.getQuantum() == null || request.getQuantum() < 1)) {
            throw new IllegalArgumentException("Round Robin requires a time quantum >= 1");
        }

        // Map DTOs to Process objects
        List<Process> processes = mapToProcesses(request.getProcesses());

        // Route to correct algorithm
        List<GanttBlock> ganttBlocks = switch (algo) {
            case FCFS        -> fcfsService.simulate(processes);
            case SJF         -> sjfService.simulate(processes);
            case SRTF        -> srtfService.simulate(processes);
            case RR          -> rrService.simulate(processes, request.getQuantum());
            case PRIORITY_NP -> priorityService.simulateNonPreemptive(processes);
            case PRIORITY_P  -> priorityService.simulatePreemptive(processes);
        };

        // Merge consecutive same-PID blocks
        List<GanttBlock> mergedBlocks = metricsCalculator.mergeConsecutiveBlocks(ganttBlocks);

        // Calculate metrics
        List<ProcessMetrics> metrics = metricsCalculator.calculate(processes);

        log.debug("Simulation complete: {} gantt blocks", mergedBlocks.size());

        return SimulationResponse.builder()
                .algorithmUsed(algo.name())
                .quantumUsed(algo == Algorithm.RR ? request.getQuantum() : null)
                .ganttBlocks(mergedBlocks)
                .metrics(metrics)
                .averageWT(metricsCalculator.averageWT(metrics))
                .averageTAT(metricsCalculator.averageTAT(metrics))
                .averageRT(metricsCalculator.averageRT(metrics))
                .build();
    }

    private Algorithm parseAlgorithm(String raw) {
        try {
            return Algorithm.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown algorithm: '" + raw + "'. Supported: FCFS, SJF, SRTF, RR, PRIORITY_NP, PRIORITY_P");
        }
    }

    private List<Process> mapToProcesses(List<SimulationRequest.ProcessInput> inputs) {
        return inputs.stream().map(input -> {
            Process p = Process.builder()
                    .pid(input.getPid().trim())
                    .arrivalTime(input.getArrivalTime())
                    .burstTime(input.getBurstTime())
                    .priority(input.getPriority())
                    .build();
            p.initForSimulation();
            return p;
        }).collect(Collectors.toList());
    }
}