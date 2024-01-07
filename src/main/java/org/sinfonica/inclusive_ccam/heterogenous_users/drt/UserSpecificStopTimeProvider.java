package org.sinfonica.inclusive_ccam.heterogenous_users.drt;

import com.google.inject.Inject;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;

import java.util.Objects;

public class UserSpecificStopTimeProvider implements PassengerStopDurationProvider {

    private final Population population;
    private final static double DEFAULT_STOP_DURATION = 60;

    @Inject
    public UserSpecificStopTimeProvider(Population population) {
        this.population = population;
    }

    @Override
    public double calcPickupDuration(DvrpVehicle dvrpVehicle, DrtRequest drtRequest) {
        return drtRequest.getPassengerIds().stream()
                .map(id -> this.population.getPersons().get(id))
                .map(p -> p.getAttributes().getAttribute("drtInteractionTime"))
                .filter(Objects::nonNull)
                .filter(o -> o instanceof Double)
                .mapToDouble(d -> (double) d)
                .max().orElse(DEFAULT_STOP_DURATION);
    }

    @Override
    public double calcDropoffDuration(DvrpVehicle dvrpVehicle, DrtRequest drtRequest) {
        return this.calcPickupDuration(dvrpVehicle, drtRequest);
    }
}
