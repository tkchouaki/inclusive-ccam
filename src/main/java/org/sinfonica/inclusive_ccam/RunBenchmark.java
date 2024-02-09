package org.sinfonica.inclusive_ccam;

import org.matsim.core.config.CommandLine;

import java.util.Locale;

public class RunBenchmark {
    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .build();

        int[] fleetSizes = new int[] { 25, 50, 75, 150, 150, 250, 350, 450 };
        boolean[] useAlonsoMoraValues = new boolean[] {true}; //, false};
        double[] vulnerableProbabilities = new double[]{ 0.5 }; // 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        int[] vulnerableInteractionTimes = new int[]{ 120, 240 }; // , 180, 240, 300};

        for (int fleetSize : fleetSizes) {
	        for(double probability: vulnerableProbabilities) {
	            for(int vulnerableTime: vulnerableInteractionTimes) {
	            	for (boolean useAlonsoMora : useAlonsoMoraValues) {
		                RunSimulation.main(new String[]{
		                        "--config-path", commandLine.getOptionStrict("config-path"),
		                        "--vulnerable-probability", String.valueOf(probability),
		                        "--vulnerable-time", String.valueOf(vulnerableTime),
		                        "--config:controler.outputDirectory", String.format("outputs/fs%s_vs%s_vt%s_%s", String.valueOf(fleetSize), String.valueOf(probability), String.valueOf(vulnerableTime), useAlonsoMora ? "am2" : "drt"),
		                        "--config:controler.lastIteration", "0",
		                        "--fleet-size", String.valueOf(fleetSize),
		                        "--use-alonso-mora", String.valueOf(useAlonsoMora)
		                });
	            	}
	            }
	        }
        }

    }
}
