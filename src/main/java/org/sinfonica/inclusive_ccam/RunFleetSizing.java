package org.sinfonica.inclusive_ccam;

import org.matsim.core.config.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.IntStream;

public class RunFleetSizing {
    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .allowOptions("vulnerable-probability", "vulnerable-time")
                .build();

        double vulnerableProbability = commandLine.hasOption("vulnerable-probability") ? Double.parseDouble(commandLine.getOptionStrict("vulnerable-probability")) : 0.5;
        int vulnerableInteractionTimes = commandLine.hasOption("vulnerable-time") ? Integer.parseInt(commandLine.getOptionStrict("vulnerable-time")) : 120;
        int[] fleetSizes = IntStream.range(1,21).map(i -> i*50).toArray();
        for(int fleetSize: fleetSizes) {
            String probabilityString = String.format(String.format(Locale.US, "%.1f", vulnerableProbability));

            String timeString = String.format(Locale.US, "%d", vulnerableInteractionTimes);

            String outputDirectory = String.format("outputs_fleet_sizing/output_%s_%s_%d", probabilityString, timeString, fleetSize);

            if(Files.exists(Path.of(outputDirectory, "modestats.csv"))) {
                System.out.println("Skipping simulation with outputDirectory " + outputDirectory);
                continue;
            }

            RunSimulation.main(new String[]{
                    "--config-path", commandLine.getOptionStrict("config-path"),
                    "--vulnerable-probability", probabilityString,
                    "--vulnerable-time", timeString,
                    "--config:controler.outputDirectory", outputDirectory,
                    "--config:controler.lastIteration", "0",
                    "--config:multiModeDrt.drt[mode=drt].vehiclesFile", String.format("drt_vehicles_%d.xml.gz", fleetSize)
            });
        }
    }
}
