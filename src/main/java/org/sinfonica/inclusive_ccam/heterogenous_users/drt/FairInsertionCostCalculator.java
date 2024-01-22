package org.sinfonica.inclusive_ccam.heterogenous_users.drt;

import org.matsim.contrib.drt.optimizer.insertion.CostCalculationStrategy;
import org.matsim.contrib.drt.optimizer.insertion.InsertionDetourTimeCalculator;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator;
import org.matsim.contrib.drt.passenger.DrtRequest;

public class FairInsertionCostCalculator implements CostCalculationStrategy {
    private final CostCalculationStrategy delegate;

    private final UserSpecificStopTimeProvider userSpecificStopTimeProvider;

    public FairInsertionCostCalculator(UserSpecificStopTimeProvider userSpecificStopTimeProvider) {
        this.delegate = new RejectSoftConstraintViolations();
        this.userSpecificStopTimeProvider = userSpecificStopTimeProvider;

    }

    @Override
    public double calcCost(DrtRequest request, InsertionGenerator.Insertion insertion, InsertionDetourTimeCalculator.DetourTimeInfo detourTimeInfo) {
        double timeLoss = delegate.calcCost(request, insertion, detourTimeInfo);
        return timeLoss / this.userSpecificStopTimeProvider.calcStopDuration(request.getPassengerIds());
    }
}
