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

        Set<Integer> fleetSizes = new HashSet<>(List.of(50, 100, 150, 250, 350, 450));
		Set<Boolean> useAlonsoMoraValues = new HashSet<>(List.of(true));
        Set<Double> vulnerableProbabilities = new HashSet<>(List.of(0.5)); // 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        Set<Integer> vulnerableInteractionTimes = new HashSet<>(List.of(120, 240));


		for(List params: Sets.cartesianProduct(fleetSizes, useAlonsoMoraValues, vulnerableProbabilities, vulnerableInteractionTimes)) {
			int fleetSize = (int) params.get(0);
			boolean useAlonsoMora = (boolean) params.get(1);
			double probability = (double) params.get(2);
			int vulnerableTime = (int) params.get(3);
			System.out.printf("%d, %s, %f, %d \n", fleetSize, useAlonsoMora, probability, vulnerableTime);

			String outputDirectory = String.format("outputs/fs%s_vs%s_vt%s_%s", String.valueOf(fleetSize), String.valueOf(probability), String.valueOf(vulnerableTime), useAlonsoMora ? "am2" : "drt");
			Path outputEventsFile = Path.of(outputDirectory, "output_events.xml.gz");

			if(Files.exists(Path.of(outputDirectory, "modestats.csv"))) {
				System.out.println("Skipping simulation with outputDirectory " + outputDirectory);
				if(Files.exists(outputEventsFile)) {
					try {
						Files.delete(outputEventsFile);
					} catch (IOException e) {
						System.out.println("Couldn't remove " + outputDirectory);
					}
				}
				continue;
			}

			RunSimulation.main(new String[]{
					"--config-path", commandLine.getOptionStrict("config-path"),
					"--vulnerable-probability", String.valueOf(probability),
					"--vulnerable-time", String.valueOf(vulnerableTime),
					"--config:controler.outputDirectory", outputDirectory,
					"--config:controler.lastIteration", "0",
					"--fleet-size", String.valueOf(fleetSize),
					"--use-alonso-mora", String.valueOf(useAlonsoMora)
			});

			try {
				Files.delete(outputEventsFile);
			} catch (IOException e) {
				System.out.println("Couldn't remove " + outputDirectory);
			}
		}


    }
}
