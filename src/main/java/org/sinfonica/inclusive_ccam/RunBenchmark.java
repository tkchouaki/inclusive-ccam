package org.sinfonica.inclusive_ccam;

import com.google.common.collect.Sets;
import org.matsim.core.config.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunBenchmark {
    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .build();

        Set<Integer> fleetSizes = new HashSet<>(List.of(100));
        Set<Boolean> useAlonsoMoraValues = new HashSet<>(List.of(false));
        Set<Double> vulnerableProbabilities = new HashSet<>(List.of(0.5)); // 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        Set<Integer> vulnerableInteractionTimes = new HashSet<>(List.of(120, 240));
        Set<Integer> dispatchIntervals = new HashSet<>(List.of(30, 60, 90, 120, 150, 180, 240, 300));
        Set<Boolean> prebookVulnerableUsersValues = new HashSet<>(List.of(true, false));
        Set<Double> prebookingShares = new HashSet<>(List.of(0.0, 0.25, 0.5, 0.75, 1.0));

        for (List params : Sets.cartesianProduct(fleetSizes, useAlonsoMoraValues, vulnerableProbabilities, vulnerableInteractionTimes, dispatchIntervals, prebookVulnerableUsersValues, prebookingShares)) {
            int fleetSize = (int) params.get(0);
            boolean useAlonsoMora = (boolean) params.get(1);
            double probability = (double) params.get(2);
            int vulnerableTime = (int) params.get(3);
            int dispatchInterval = (int) params.get(4);
            boolean prebookingVulnerableUsers = (boolean) params.get(5);
            double prebookingShare = (double) params.get(6);

            String outputDirectory = String.format("outputs/fs%s_vs%s_vt%s_%s", fleetSize, probability, vulnerableTime, useAlonsoMora ? "am" : "drt");

            // Dispatch interval is only relevant with DRT, so no need to perform all the simulations with am with various dispatch intervals
            outputDirectory += String.format("_di%s", useAlonsoMora ? "x" : dispatchInterval);

            // Same thing between prebook vulnerable users and prebooking share
            outputDirectory += String.format("_pv%s_ps%s", prebookingVulnerableUsers, prebookingVulnerableUsers ? "x" : prebookingShare);


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
                    "--vulnerable-probability", String.valueOf(probability),
                    "--vulnerable-time", String.valueOf(vulnerableTime),
                    "--config:controler.outputDirectory", outputDirectory,
                    "--config:controler.lastIteration", "0",
                    "--fleet-size", String.valueOf(fleetSize),
                    "--use-alonso-mora", String.valueOf(useAlonsoMora),
                    "--prebook-vulnerable", String.valueOf(prebookingVulnerableUsers),
                    "--prebooking-probability", String.valueOf(prebookingShare),
                    "--config:multiModeDrt.drt[mode=drt].dispatchInterval", String.valueOf(dispatchInterval)
            };

            RunSimulation.main(simArgs);

            try {
                Files.delete(outputEventsFile);
            } catch (IOException e) {
                System.out.println("Couldn't remove " + outputDirectory);
            }
        }


    }
}
