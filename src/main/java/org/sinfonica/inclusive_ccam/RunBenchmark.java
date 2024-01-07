package org.sinfonica.inclusive_ccam;

import org.matsim.core.config.CommandLine;

import java.util.Locale;

public class RunBenchmark {
    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .build();

        double[] vulnerableProbabilities = new double[]{0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1};
        int[] vulnerableInteractionTimes = new int[]{120, 180, 240, 300};

        for(double probability: vulnerableProbabilities) {
            String probabilityString = String.format(String.format(Locale.US, "%.1f", probability));
            for(int vulnerableTime: vulnerableInteractionTimes) {
                String timeString = String.format(Locale.US, "%d", vulnerableTime);
                RunSimulation.main(new String[]{
                        "--config-path", commandLine.getOptionStrict("config-path"),
                        "--vulnerable-probability", probabilityString,
                        "--vulnerable-time", timeString,
                        "--config:controler.outputDirectory", String.format("outputs/output_%s_%s", probabilityString, timeString),
                        "--config:controler.lastIteration", "0"
                });
            }
        }

    }
}
