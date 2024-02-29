package org.sinfonica.inclusive_ccam;

import com.google.common.collect.Sets;
import org.matsim.core.config.CommandLine;

import java.io.File;
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

        Set<Integer> fleetSizes = new HashSet<>(List.of(100, 150, 200, 250, 300, 350, 400, 450));
        Set<Boolean> useAlonsoMoraValues = new HashSet<>(List.of(true));
        Set<Double> vulnerableProbabilities = new HashSet<>(List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9));
        Set<Integer> vulnerableInteractionTimes = new HashSet<>(List.of(120, 240));
        Set<Integer> dispatchIntervals = new HashSet<>(List.of(1));
        Set<Boolean> prebookVulnerableUsersValues = new HashSet<>(List.of(false));
        Set<Double> prebookingShares = new HashSet<>(List.of(0.0));
        Set<Boolean> minimizePassengerDelayValues = new HashSet<>(List.of(false, true));
        Set<Boolean> alonsoMoraInclusivePenaltyValues = new HashSet<>(List.of(false, true));
        Set<Double> alonsoMoraWeightFactorValues = new HashSet<>();
        for(double weightFactor=100.0; weightFactor<2000.0; weightFactor+=100) {
            alonsoMoraWeightFactorValues.add(weightFactor);
        }
        alonsoMoraWeightFactorValues.add(1.0);

        Map<String, String[]> simulationTasks = new HashMap<>();

        String baseOutputPath = commandLine.getOption("base-output-path").orElse("outputs");

        for (List params : Sets.cartesianProduct(fleetSizes, useAlonsoMoraValues, vulnerableProbabilities, vulnerableInteractionTimes, dispatchIntervals, prebookVulnerableUsersValues, prebookingShares, minimizePassengerDelayValues, alonsoMoraInclusivePenaltyValues, alonsoMoraWeightFactorValues)) {
            int fleetSize = (int) params.get(0);
            boolean useAlonsoMora = (boolean) params.get(1);
            double vulnerableProbability = (double) params.get(2);
            int vulnerableTime = (int) params.get(3);
            int dispatchInterval = useAlonsoMora ? 1 : (int) params.get(4);
            boolean prebookingVulnerableUsers = (boolean) params.get(5);
            double prebookingShare = (double) params.get(6);
            boolean minimizePassengerDelay = ( (boolean) params.get(7) ) && !useAlonsoMora;
            boolean alonsoMoraInclusivePenalty = ((boolean) params.get(8)) && useAlonsoMora;
            double alonsoMoraWeightFactor = (double) params.get(9);

            //The name of the output dir should look like
            //fs100_vs0.0_vtx_am_dix_pvx_ps0.0_pwf0.1

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

            if(alonsoMoraInclusivePenalty) {
                outputDirectory += String.format("_pwf%s", alonsoMoraWeightFactor);
            } else {
                outputDirectory += "_pwfx";
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
                    "--config:multiModeDrt.drt[mode=drt].dispatchInterval", String.valueOf(dispatchInterval),
                    "--am-inclusive-penalty", String.valueOf(alonsoMoraInclusivePenalty),
                    "--am-weight-alpha", String.valueOf(alonsoMoraWeightFactor)
            };

            simulationTasks.put(outputDirectory, simArgs);
        }

        int parallelSims = Integer.parseInt(commandLine.getOption("parallel-sims").orElse("1"));

        System.out.printf("About to perform %d simulations, with %d running in parallel\n", simulationTasks.size(), parallelSims);

        boolean noSim = Boolean.parseBoolean(commandLine.getOption("no-sim").orElse("false"));

        if(noSim) {
            return;
        }

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = RunSimulation.class.getName();

        List<ProcessBuilder> processBuilders = new ArrayList<>();
        Process[] runningProcesses = new Process[parallelSims];

        for(String[] simArgs: simulationTasks.values()) {
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            command.add("-cp");
            command.add(classpath);
            command.add(className);
            command.addAll(Arrays.stream(simArgs).toList());
            ProcessBuilder processBuilder = new ProcessBuilder(command).inheritIO();
            processBuilders.add(processBuilder);
        }

        boolean running = true;
        while(running) {
            running = false;
            for(int i=0;i<parallelSims;i++) {
                if(runningProcesses[i]==null) {
                    if(processBuilders.size() == 0) {
                        continue;
                    }
                    try {
                        runningProcesses[i] = processBuilders.remove(0).start();
                        running = true;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if(!runningProcesses[i].isAlive()) {
                    if(runningProcesses[i].exitValue() != 0) {
                        running = false;
                        break;
                    }
                    runningProcesses[i] = null;
                } else {
                    running = true;
                }
            }
        }

        for(int i=0; i<parallelSims; i++) {
            if(runningProcesses[i] != null && runningProcesses[i].isAlive()) {
                runningProcesses[i].destroyForcibly();
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
