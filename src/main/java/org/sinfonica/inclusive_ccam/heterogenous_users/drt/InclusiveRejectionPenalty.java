package org.sinfonica.inclusive_ccam.heterogenous_users.drt;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;

import java.util.Objects;

public class InclusiveRejectionPenalty implements AssignmentSolver.RejectionPenalty {

    private final double unassignmentPenalty;
    private final double baseRejectionPenalty;
    private final double lowestInteractionTime;
    private final PassengerStopDurationProvider stopDurationProvider;
    private final double weightAlpha;

    public InclusiveRejectionPenalty(Population population, double unassignmentPenalty, double baseRejectionPenalty, PassengerStopDurationProvider stopDurationProvider, double weightAlpha) {
        this.unassignmentPenalty = unassignmentPenalty;
        this.baseRejectionPenalty = baseRejectionPenalty;
        this.lowestInteractionTime = population.getPersons().values().stream().map(p -> p.getAttributes().getAttribute("drtInteractionTIme")).filter(Objects::nonNull).filter(o -> o instanceof Double).mapToDouble(o -> (double) o).min().orElse(60.0);
        this.stopDurationProvider = stopDurationProvider;
        if(! (this.stopDurationProvider instanceof  UserSpecificStopTimeProvider)) {
            throw new IllegalStateException("Can only use this penalty with the %s PassengerStopDurationProvider, %s provided".formatted(UserSpecificStopTimeProvider.class.getName(), this.stopDurationProvider.getClass().getName()));
        }
        this.weightAlpha = weightAlpha;
    }

    public InclusiveRejectionPenalty(Population population, double unassignmentPenalty, double baseRejectionPenalty, PassengerStopDurationProvider stopDurationProvider) {
        this(population, unassignmentPenalty, baseRejectionPenalty, stopDurationProvider, 1.0);
    }

    @Override
    public double getPenalty(AlonsoMoraRequest request) {
        double interactionTime = this.stopDurationProvider.calcPickupDuration(null, request.getDrtRequest());
        double weight = interactionTime / this.lowestInteractionTime;
        if (weight > 1) {
            weight*=weightAlpha;
        }
        if(request.isAssigned()) {
            return this.unassignmentPenalty * weight;
        }
        return this.baseRejectionPenalty * weight;
    }
}
