package org.sinfonica.inclusive_ccam.heterogenous_users.drt;

import org.matsim.contrib.drt.stops.ParallelStopTimeCalculator;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;
import org.matsim.contrib.drt.stops.StopTimeCalculator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;

public class UserSpecificStopTimeModule extends AbstractDvrpModeModule {

    public UserSpecificStopTimeModule(String mode) {
        super(mode);
    }

    @Override
    public void install() {
        bindModal(PassengerStopDurationProvider.class).to(UserSpecificStopTimeProvider.class);
    }

}
