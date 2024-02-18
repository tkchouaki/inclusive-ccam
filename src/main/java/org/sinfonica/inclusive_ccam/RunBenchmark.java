package org.sinfonica.inclusive_ccam;

import com.google.common.collect.Sets;
import org.matsim.core.config.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RunBenchmark {

    public static class SimTask implements Runnable {

        private final String[] args;

        public SimTask(String[] args) {
            this.args = args;
        }

        @Override
        public void run() {
            try {
                RunSimulation.main(this.args);
            } catch (CommandLine.ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .allowOptions("parallel-sims")
                .allowOptions("base-output-path")
                .allowOptions("no-sim")
                .build();

        Set<Integer> fleetSizes = new HashSet<>(List.of(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600));
        Set<Boolean> useAlonsoMoraValues = new HashSet<>(List.of(false));
        Set<Double> vulnerableProbabilities = new HashSet<>(List.of(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0));
        Set<Integer> vulnerableInteractionTimes = new HashSet<>(List.of(120, 240));
        Set<Integer> dispatchIntervals = new HashSet<>(List.of(1));
        Set<Boolean> prebookVulnerableUsersValues = new HashSet<>(List.of(true, false));
        Set<Double> prebookingShares = new HashSet<>(List.of(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0));
        Set<Boolean> minimizePassengerDelayValues = new HashSet<>(List.of(false, true));

        Map<String, String[]> simulationTasks = new HashMap<>();

        String baseOutputPath = commandLine.getOption("base-output-path").orElse("outputs");

        for (List params : Sets.cartesianProduct(fleetSizes, useAlonsoMoraValues, vulnerableProbabilities, vulnerableInteractionTimes, dispatchIntervals, prebookVulnerableUsersValues, prebookingShares, minimizePassengerDelayValues)) {
            int fleetSize = (int) params.get(0);
            boolean useAlonsoMora = (boolean) params.get(1);
            double vulnerableProbability = (double) params.get(2);
            int vulnerableTime = (int) params.get(3);
            int dispatchInterval = useAlonsoMora ? 1 : (int) params.get(4);
            boolean prebookingVulnerableUsers = (boolean) params.get(5);
            double prebookingShare = (double) params.get(6);
            boolean minimizePassengerDelay = ( (boolean) params.get(7) ) && !useAlonsoMora;

            String outputDirectory = Paths.get(baseOutputPath, String.format("fs%s_vs%s_vt%s", fleetSize, vulnerableProbability,  vulnerableProbability > 0 ? vulnerableTime: "x")).toString();

            if(useAlonsoMora) {
                outputDirectory += "_am";
            } else {
                if (minimizePassengerDelay) {
                    outputDirectory += "_drt2";
                } else {
                    outputDirectory += "_drt";
                }
            }

            // Dispatch interval is only relevant with DRT, so no need to perform all the simulations with am with various dispatch intervals
            outputDirectory += String.format("_di%s", useAlonsoMora ? "x" : dispatchInterval);

            if(vulnerableProbability > 0.0) {
                // Same thing between prebook vulnerable users and prebooking share
                outputDirectory += String.format("_pv%s_ps%s", prebookingVulnerableUsers, prebookingVulnerableUsers ? "x" : prebookingShare);
                if(prebookingVulnerableUsers) {
                    prebookingShare = 0.0;
                }
            } else {
                outputDirectory += String.format("_pvx_ps%s", prebookingShare);
                prebookingVulnerableUsers = false;
            }


            Path outputEventsFile = Path.of(outputDirectory, "output_events.xml.gz");

            if (Files.exists(Path.of(outputDirectory, "modestats.csv"))) {
                System.out.println("Skipping simulation with outputDirectory " + outputDirectory);
                if (Files.exists(outputEventsFile)) {
                    try {
                        Files.delete(outputEventsFile);
                    } catch (IOException e) {
                        System.out.println("Couldn't remove " + outputDirectory);
                    }
                }
                continue;
            }

            String[] simArgs = new String[]{
                    "--config-path", commandLine.getOptionStrict("config-path"),
                    "--vulnerable-probability", String.valueOf(vulnerableProbability),
                    "--vulnerable-time", String.valueOf(vulnerableTime),
                    "--config:controler.outputDirectory", outputDirectory,
                    "--config:controler.lastIteration", "0",
                    "--fleet-size", String.valueOf(fleetSize),
                    "--use-alonso-mora", String.valueOf(useAlonsoMora),
                    "--prebook-vulnerable", String.valueOf(prebookingVulnerableUsers),
                    "--prebooking-probability", String.valueOf(prebookingShare),
                    "--minimize-passenger-delays", String.valueOf(minimizePassengerDelay),
                    "--config:multiModeDrt.drt[mode=drt].dispatchInterval", String.valueOf(dispatchInterval)
            };

            simulationTasks.put(outputDirectory, simArgs);
        }

        int parallelSims = Integer.parseInt(commandLine.getOption("parallel-sims").orElse("1"));

        System.out.printf("About to perform %d simulations, with %d running in parallel\n", simulationTasks.size(), parallelSims);

        boolean noSim = Boolean.parseBoolean(commandLine.getOption("no-sim").orElse("false"));

        if(noSim) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(parallelSims);

        List<String[]> tasks = new ArrayList<>(simulationTasks.values());
        Collections.shuffle(tasks);

        List<Future> futures = tasks.stream().map(SimTask::new).map(executor::submit).collect(Collectors.toList());

        while(!executor.isTerminated()) {
            for(Future future: futures) {
                try {
                    future.get(1, TimeUnit.MICROSECONDS);
                } catch (InterruptedException | ExecutionException e) {
                    executor.shutdownNow();
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {

                }
            }
        }

        for(String outputDirectory: simulationTasks.keySet()) {
            Path outputEventsFile = Path.of(outputDirectory, "output_events.xml.gz");
            try {
                Files.delete(outputEventsFile);
            } catch (IOException e) {
                System.out.println("Couldn't remove " + outputDirectory);
            }
        }

    }
}
