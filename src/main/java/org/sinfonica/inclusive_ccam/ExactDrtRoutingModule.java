package org.sinfonica.inclusive_ccam;

import org.matsim.alonso_mora.travel_time.MatrixTravelTimeEstimator;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.insertion.DetourTimeEstimator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.core.router.util.TravelTime;

public class ExactDrtRoutingModule extends AbstractDvrpModeQSimModule {
	protected ExactDrtRoutingModule(String mode) {
		super(mode);
	}

	@Override
	protected void configureQSim() {
		bindModal(DetourTimeEstimator.class).toProvider(modalProvider(getter -> {
			TravelTime travelTime = getter.getModal(TravelTime.class);
			Network network = getter.getModal(Network.class);

			// use the exact calculator from AM which builds a link-to-link matrix upfront
			MatrixTravelTimeEstimator estimator = MatrixTravelTimeEstimator.create(network, travelTime, 8.5 * 3600.0);

			return new DetourTimeEstimator() {
				@Override
				public double estimateTime(Link from, Link to, double departureTime) {
					return estimator.estimateTravelTime(from, to, departureTime, 0.0);
				}
			};
		}));
	}
}
