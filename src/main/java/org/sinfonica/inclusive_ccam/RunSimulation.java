package org.sinfonica.inclusive_ccam;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.*;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.sinfonica.inclusive_ccam.heterogenous_users.drt.UserSpecificStopTimeModule;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class RunSimulation {

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        CommandLine commandLine = new CommandLine.Builder(args)
                .requireOptions("config-path")
                .allowOptions("random-seed")
                .allowOptions("vulnerable-probability")
                .allowOptions("vulnerable-time")
                .build();

        String configPath = commandLine.getOptionStrict("config-path");
        Integer randomSeed = commandLine.hasOption("random-seed") ? Integer.parseInt(commandLine.getOptionStrict("random-seed")) : 1234;

        Config config = ConfigUtils.loadConfig(configPath, new DvrpConfigGroup(), new MultiModeDrtConfigGroup());
        commandLine.applyConfiguration(config);

        MultiModeDrtConfigGroup multiModeDrtConfigGroup = (MultiModeDrtConfigGroup) config.getModules().get(MultiModeDrtConfigGroup.GROUP_NAME);
        Set<String> drtModes = multiModeDrtConfigGroup.modes().collect(Collectors.toSet());

        Scenario scenario = ScenarioUtils.createScenario(config);
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());
        ScenarioUtils.loadScenario(scenario);
        scenario.getPopulation().getPersons().values().stream()
                .flatMap(p -> p.getSelectedPlan().getPlanElements().stream())
                .filter(e -> e instanceof Leg)
                .map(e -> (Leg) e)
                .filter(l -> drtModes.contains(l.getMode()))
                .forEach(l -> l.setRoute(null));

        double vulnerableProbability = commandLine.hasOption("vulnerable-probability") ? Double.parseDouble(commandLine.getOptionStrict("vulnerable-probability")): 0;
        double vulnerableTime = commandLine.hasOption("vulnerable-time") ? Double.parseDouble(commandLine.getOptionStrict("vulnerable-time")) : 120.0;

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

        controler.configureQSimComponents( DvrpQSimComponents.activateAllModes((MultiModal<?>) config.getModules().get(MultiModeDrtConfigGroup.GROUP_NAME)));
        controler.run();
    }
}
