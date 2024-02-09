package org.sinfonica.inclusive_ccam;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.GlpkMpsAssignmentParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.MatrixEstimatorParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.SequenceGeneratorType;
import org.matsim.alonso_mora.AlonsoMoraConfigurator;
import org.matsim.alonso_mora.MultiModeAlonsoMoraConfigGroup;
import org.matsim.alonso_mora.travel_time.MatrixTravelTimeEstimator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.drt.optimizer.insertion.DetourTimeEstimator;
import org.matsim.contrib.drt.prebooking.PrebookingParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.dvrp.run.MultiModal;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.algorithms.NetworkSegmentDoubleLinks;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.sinfonica.inclusive_ccam.heterogenous_users.drt.UserSpecificStopTimeModule;
import org.sinfonica.inclusive_ccam.heterogenous_users.drt.UserSpecificStopTimeProvider;

public class RunSimulation {

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .allowOptions("random-seed")
                .allowOptions("vulnerable-probability")
                .allowOptions("vulnerable-time")
                .allowOptions("fair-costs")
                .allowOptions("fleet-size")
                .allowOptions("use-alonso-mora")
                .build();

        String configPath = commandLine.getOptionStrict("config-path");
        Integer randomSeed = commandLine.hasOption("random-seed") ? Integer.parseInt(commandLine.getOptionStrict("random-seed")) : 1234;

        Config config = ConfigUtils.loadConfig(configPath, new DvrpConfigGroup(), new MultiModeDrtConfigGroup());
        commandLine.applyConfiguration(config);
        
        StrategySettings settings = new StrategySettings();
        settings.setStrategyName("KeepLastSelected");
        settings.setWeight(1.0);
        config.replanning().addStrategySettings(settings);
        config.transit().setTransitScheduleFile(null);

        config.qsim().setFlowCapFactor(1e9);
        config.qsim().setStorageCapFactor(1e9);
        config.controller().setLastIteration(0);

        MultiModeDrtConfigGroup multiModeDrtConfigGroup = (MultiModeDrtConfigGroup) config.getModules().get(MultiModeDrtConfigGroup.GROUP_NAME);
        Set<String> drtModes = multiModeDrtConfigGroup.modes().collect(Collectors.toSet());
        
        int fleetSize = commandLine.getOption("fleet-size").map(Integer::parseInt).orElse(100);
        
        multiModeDrtConfigGroup.getModalElements().forEach(item -> {
        	item.vehiclesFile = "drt_vehicles_" + fleetSize + ".xml.gz";
        	item.addParameterSet(new PrebookingParams());
        	
        	item.removeParameterSet(item.getRebalancingParams().get());
        	
        });

        Scenario scenario = ScenarioUtils.createScenario(config);
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());
        ScenarioUtils.loadScenario(scenario);
        scenario.getPopulation().getPersons().values().stream()
                .flatMap(p -> p.getSelectedPlan().getPlanElements().stream())
                .filter(e -> e instanceof Leg)
                .map(e -> (Leg) e)
                .filter(l -> drtModes.contains(l.getMode()))
                .forEach(l -> l.setRoute(null));
        
        new NetworkSegmentDoubleLinks().run(scenario.getNetwork());

        double vulnerableProbability = commandLine.hasOption("vulnerable-probability") ? Double.parseDouble(commandLine.getOptionStrict("vulnerable-probability")): 0;
        double vulnerableTime = commandLine.hasOption("vulnerable-time") ? Double.parseDouble(commandLine.getOptionStrict("vulnerable-time")) : 120.0;
        //boolean fairCosts = commandLine.hasOption("fair-costs") && Boolean.parseBoolean(commandLine.getOptionStrict("fair-costs"));

        Random random = new Random(randomSeed);
        scenario.getPopulation().getPersons().values().forEach(p -> {
            Double drtInteractionTime = 60.0;
            if(random.nextDouble() <= vulnerableProbability) {
                drtInteractionTime = vulnerableTime;
            }
            p.getAttributes().putAttribute("drtInteractionTime", drtInteractionTime);
        });


        Controler controler = new Controler(scenario);
        controler.addOverridingModule(new DvrpModule());
        controler.addOverridingModule(new MultiModeDrtModule());

        drtModes.forEach(mode -> {
            controler.addOverridingModule(new UserSpecificStopTimeModule(mode));
        });
        
		drtModes.forEach(mode -> {
			controler.addOverridingQSimModule(new AbstractDvrpModeQSimModule(mode) {
				@Override
				protected void configureQSim() {
					bindModal(UserSpecificStopTimeProvider.class).to(UserSpecificStopTimeProvider.class);
				}
			});
		});

        boolean useExactTravelTimesForDrt = true;
        if (useExactTravelTimesForDrt) {
			drtModes.forEach(mode -> {
				controler.addOverridingQSimModule(new ExactDrtRoutingModule(mode));
			});
        }
        
        boolean useAlonsoMora = commandLine.getOption("use-alonso-mora").map(Boolean::parseBoolean).orElse(false);        
        if (useAlonsoMora) {
			config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles(true);

			MultiModeAlonsoMoraConfigGroup multiModeConfig = new MultiModeAlonsoMoraConfigGroup();
			config.addModule(multiModeConfig);

			AlonsoMoraConfigGroup amConfig = new AlonsoMoraConfigGroup();
			multiModeConfig.addParameterSet(amConfig);

			amConfig.maximumQueueTime = 0.0;

			amConfig.assignmentInterval = 30;
			amConfig.relocationInterval = 30;

			amConfig.congestionMitigation.allowBareReassignment = false;
			amConfig.congestionMitigation.allowPickupViolations = true;
			amConfig.congestionMitigation.allowPickupsWithDropoffViolations = true;
			amConfig.congestionMitigation.preserveVehicleAssignments = true;

			amConfig.rerouteDuringScheduling = false;
			amConfig.checkDeterminsticTravelTimes = false;
			amConfig.sequenceGeneratorType = SequenceGeneratorType.Combined;
			
			amConfig.clearAssignmentSolver();
			amConfig.clearRelocationSolver();
			amConfig.clearTravelTimeEstimator();

			GlpkMpsAssignmentParameters assignmentParameters = new GlpkMpsAssignmentParameters();
			amConfig.addParameterSet(assignmentParameters);

			/*GlpkMpsRelocationParameters relocationParameters = new GlpkMpsRelocationParameters();
			amConfig.addParameterSet(relocationParameters);*/
			amConfig.relocationInterval = 0;

			MatrixEstimatorParameters estimator = new MatrixEstimatorParameters();
			amConfig.addParameterSet(estimator);

			AlonsoMoraConfigurator.configure(controler, amConfig.mode);
		}

        controler.configureQSimComponents( DvrpQSimComponents.activateAllModes((MultiModal<?>) config.getModules().get(MultiModeDrtConfigGroup.GROUP_NAME)));
        controler.run();
    }
}
