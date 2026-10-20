package com.mavan.or.newpost.optimization;

import com.mavan.or.newpost.example.NewPostExample;
import com.mavan.or.newpost.model.DiscountBand;
import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.scheduling.PlannedTerminalJob;
import com.mavan.or.newpost.scheduling.PlannedTrip;
import com.mavan.or.newpost.scheduling.TerminalLoad;
import com.mavan.or.newpost.scheduling.TripLoad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ExactPlanServiceTest {
    private static final double EPS = 1e-6;

    private final ExactPlanService service = new ExactPlanService();

    @Test
    void dubaiPlanIsProvenGlobalOptimum() {
        NewPostProblem problem = NewPostExample.documentedExample();
        ExactPlanResult plan = service.solve(problem, ExactPlanService.Options.defaults());

        assertThat(plan.status()).isEqualTo(ExactPlanResult.Status.OPTIMAL);
        assertThat(plan.globallyOptimal()).isTrue();
        assertThat(plan.totalCost()).isCloseTo(3958.0, within(EPS));
        assertThat(plan.lowerBound()).isCloseTo(plan.totalCost(), within(EPS));
        assertThat(plan.gapPercent()).isCloseTo(0.0, within(EPS));
        assertThat(plan.transportCost()).isCloseTo(1308.0, within(EPS));
        assertThat(plan.terminalCost()).isCloseTo(2650.0, within(EPS));
        assertThat(plan.transportCost() + plan.terminalCost() + plan.rentalCost() + plan.activationCost())
                .isCloseTo(plan.totalCost(), within(EPS));
        assertThat(plan.totalTonnage()).isCloseTo(56.0, within(EPS));
        assertThat(plan.costPerTon()).isCloseTo(plan.totalCost() / 56.0, within(EPS));
        assertThat(plan.tripCount()).isEqualTo(plan.trips().size());
        assertThat(plan.completionHour()).isLessThanOrEqualTo(problem.getPlanningHorizonHours());
        assertThat(plan.explanation()).hasSize(11);
    }

    @Test
    void costsAddUpTripByTripAndTerminalByTerminal() {
        NewPostProblem problem = NewPostExample.documentedExample();
        ExactPlanResult plan = service.solve(problem, ExactPlanService.Options.defaults());

        double transport = 0.0;
        for (PlannedTrip trip : plan.trips()) {
            VehicleType vehicle = problem.getVehicleTypes().get(trip.vehicleTypeIndex());
            double tariffTimesKm = vehicle.getTariffPerKm() * problem.getDistance()[trip.from()][trip.to()];
            assertThat(trip.transportCost()).isCloseTo(tariffTimesKm, within(EPS));
            assertThat(trip.loadedTonnage()).isLessThanOrEqualTo(trip.usableCapacity() + EPS);
            assertThat(trip.loads().stream().mapToDouble(TripLoad::tonnage).sum()).isCloseTo(trip.loadedTonnage(), within(EPS));
            transport += trip.transportCost();
        }
        assertThat(transport).isCloseTo(plan.transportCost(), within(EPS));

        double[] expectedTonnage = {35.0, 52.0, 35.0};
        double[] expectedCost = {630.0, 1040.0, 980.0};
        double terminalSum = 0.0;
        for (TerminalLoad load : plan.terminalLoads()) {
            double jobs = plan.terminalJobs().stream().filter(j -> j.city() == load.city()).mapToDouble(PlannedTerminalJob::tonnage).sum();
            assertThat(load.totalTonnage()).isCloseTo(jobs, within(EPS));
            assertThat(load.outboundTonnage() + load.transitTonnage() + load.inboundTonnage()).isCloseTo(load.totalTonnage(), within(EPS));
            assertThat(load.cost()).isCloseTo(load.totalTonnage() * problem.getTerminalHandlingCostPerTon()[load.city()], within(EPS));
            assertThat(load.peakRatePerHour()).isLessThanOrEqualTo(load.capacityPerHour() + EPS);
            assertThat(load.totalTonnage()).isCloseTo(expectedTonnage[load.city()], within(EPS));
            assertThat(load.cost()).isCloseTo(expectedCost[load.city()], within(EPS));
            terminalSum += load.cost();
        }
        assertThat(terminalSum).isCloseTo(plan.terminalCost(), within(EPS));
        assertThat(plan.terminalLoads().get(1).transitTonnage()).isCloseTo(10.0, within(EPS));

        // Each route pays handling at every terminal on its path.
        for (RoutePlan route : plan.routes()) {
            double perTon = route.path().stream().mapToDouble(city -> problem.getTerminalHandlingCostPerTon()[city]).sum();
            assertThat(route.handlingCost()).isCloseTo(route.tonnage() * perTon, within(EPS));
        }
        double delivered = plan.routes().stream().mapToDouble(RoutePlan::tonnage).sum();
        assertThat(delivered).isCloseTo(56.0, within(EPS));
    }

    @Test
    void scheduleRespectsProcessingOrderAndTerminalQueues() {
        NewPostProblem problem = NewPostExample.documentedExample();
        ExactPlanResult plan = service.solve(problem, ExactPlanService.Options.defaults());
        List<PlannedTerminalJob> jobs = plan.terminalJobs();

        for (PlannedTrip trip : plan.trips()) {
            double travel = problem.getDistance()[trip.from()][trip.to()] / problem.getSpeedKmPerHour();
            assertThat(trip.arrivalHour() - trip.departureHour()).isCloseTo(travel, within(EPS));
            for (TripLoad load : trip.loads()) {
                PlannedTerminalJob before = job(jobs, load.shipmentIndex(), load.legIndex());
                PlannedTerminalJob after = job(jobs, load.shipmentIndex(), load.legIndex() + 1);
                assertThat(before.city()).isEqualTo(trip.from());
                assertThat(after.city()).isEqualTo(trip.to());
                assertThat(before.endHour()).isLessThanOrEqualTo(trip.departureHour() + EPS);
                assertThat(after.startHour()).isGreaterThanOrEqualTo(trip.arrivalHour() - EPS);
            }
        }
        for (PlannedTerminalJob job : jobs) {
            double rate = problem.getTerminalCapacityPerHour()[job.city()];
            assertThat(job.endHour() - job.startHour()).isCloseTo(job.tonnage() / rate, within(EPS));
            for (PlannedTerminalJob other : jobs) {
                if (other == job || other.city() != job.city()) continue;
                boolean disjoint = other.endHour() <= job.startHour() + EPS || job.endHour() <= other.startHour() + EPS;
                assertThat(disjoint).as("terminal %d processes one job at a time", job.city()).isTrue();
            }
        }
    }

    @Test
    void directOnlyMatchesIndependentPerArcCalculation() {
        NewPostProblem problem = NewPostExample.documentedExample();
        ExactPlanResult direct = service.solve(problem.toBuilder().directOnly(true).build(), ExactPlanService.Options.defaults());

        double expected = 0.0;
        int n = problem.locationCount();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double demand = problem.getDemand()[i][j];
                if (i == j || demand <= EPS) continue;
                expected += cheapestTruckMix(problem, demand, problem.getDistance()[i][j]);
                expected += demand * (problem.getTerminalHandlingCostPerTon()[i] + problem.getTerminalHandlingCostPerTon()[j]);
            }
        }
        assertThat(direct.status()).isEqualTo(ExactPlanResult.Status.OPTIMAL);
        assertThat(direct.totalCost()).isCloseTo(expected, within(EPS));
        assertThat(direct.totalCost()).isCloseTo(4127.2, within(EPS));
        assertThat(direct.transshipmentTonnage()).isCloseTo(0.0, within(EPS));
        assertThat(direct.routes()).allSatisfy(route -> assertThat(route.path()).hasSize(2));
    }

    @Test
    void scenariosReportSavingsAgainstDirectTrucks() {
        ExactPlanService.Scenarios result = service.solveWithScenarios(NewPostExample.documentedExample(), ExactPlanService.Options.defaults());
        assertThat(result.scenarios()).containsKeys("directOnly", "optimizedNetwork");
        assertThat(result.savingsVsDirect()).isCloseTo(4127.2 - 3958.0, within(EPS));
        assertThat(result.savingsPercent()).isCloseTo((4127.2 - 3958.0) / 4127.2 * 100.0, within(EPS));
    }

    @Test
    void withoutConsolidationEveryTruckCarriesOneShipmentPair() {
        NewPostProblem problem = NewPostExample.documentedExample();
        ExactPlanService.Options defaults = ExactPlanService.Options.defaults();
        ExactPlanResult separate = service.solve(problem, new ExactPlanService.Options(false, defaults.staticNodeLimit(),
                defaults.globalSettings(), defaults.currency()));

        assertThat(separate.hasPlan()).isTrue();
        assertThat(separate.totalCost()).isGreaterThanOrEqualTo(3958.0 - EPS);
        for (PlannedTrip trip : separate.trips()) {
            long pairs = trip.loads().stream().map(load -> load.origin() * 100 + load.destination()).distinct().count();
            assertThat(pairs).isEqualTo(1);
        }
    }

    @Test
    void handlingIsChargedAtTheTransitTerminal() {
        // A -> C costs 1000 by road, A -> B -> C only 2, but handling at B costs 1000 per tonne.
        NewPostProblem problem = handlingCounterexample();
        ExactPlanResult plan = service.solve(problem, ExactPlanService.Options.defaults());

        assertThat(plan.status()).isEqualTo(ExactPlanResult.Status.OPTIMAL);
        assertThat(plan.totalCost()).isCloseTo(1040.0, within(EPS));
        assertThat(plan.terminalCost()).isCloseTo(40.0, within(EPS));
        assertThat(plan.routes()).singleElement().satisfies(route -> assertThat(route.path()).containsExactly(0, 2));
    }

    @Test
    void transitProcessingTimeForcesTheDirectRoute() {
        ExactPlanResult plan = service.solve(slowHubProblem(), ExactPlanService.Options.defaults());

        assertThat(plan.status()).isEqualTo(ExactPlanResult.Status.OPTIMAL);
        assertThat(plan.method()).contains("Time-expanded");
        assertThat(plan.lowerBound()).isNotNull();
        assertThat(plan.routes()).allSatisfy(route -> assertThat(route.path()).containsExactly(0, 2));
        assertThat(plan.totalCost()).isCloseTo(3.0 + 4.0 * 10.0, within(EPS));
        assertThat(plan.warnings()).anyMatch(w -> w.contains("cannot be scheduled"));
    }

    @Test
    void volumeDiscountsAreRejected() {
        NewPostProblem problem = NewPostExample.documentedExample().toBuilder()
                .discountBands(List.of(new DiscountBand(10.0, 5.0))).build();
        assertThatThrownBy(() -> service.solve(problem, ExactPlanService.Options.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static NewPostProblem handlingCounterexample() {
        return NewPostProblem.builder().locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 0, 4}, {0, 0, 0}, {0, 0, 0}})
                .distance(new double[][]{{0, 1, 1000}, {1, 0, 1}, {1000, 1, 0}})
                .terminalCapacity(new double[]{100, 100, 100})
                .terminalHandlingCost(new double[]{10, 1000, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8).planningHorizonHours(30).schedulePeriodDurationHours(1).build();
    }

    /** Via B is cheaper, but B processes 0.5 t/h: 4 t of transit cargo need 8 h, the horizon is 5 h. */
    static NewPostProblem slowHubProblem() {
        return NewPostProblem.builder().locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 0, 4}, {0, 0, 0}, {0, 0, 0}})
                .distance(new double[][]{{0, 1, 3}, {1, 0, 1}, {3, 1, 0}})
                .terminalCapacity(new double[]{100, 0.5, 100})
                .terminalHandlingCost(new double[]{10, 0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8).planningHorizonHours(5).schedulePeriodDurationHours(1).build();
    }

    private static PlannedTerminalJob job(List<PlannedTerminalJob> jobs, int shipment, int visit) {
        return jobs.stream().filter(j -> j.shipmentIndex() == shipment && j.visitIndex() == visit).findFirst().orElseThrow();
    }

    private static double cheapestTruckMix(NewPostProblem problem, double tonnage, double distance) {
        List<VehicleType> vehicles = problem.getVehicleTypes();
        double[] usable = vehicles.stream().mapToDouble(v -> v.usableCapacity(problem.getLoadingFactor())).toArray();
        double[] cost = vehicles.stream().mapToDouble(v -> v.getTariffPerKm() * distance).toArray();
        int max = (int) Math.ceil(tonnage / usable[2]);
        double best = Double.POSITIVE_INFINITY;
        for (int a = 0; a <= max; a++) {
            for (int b = 0; b <= max; b++) {
                for (int c = 0; c <= max; c++) {
                    if (a * usable[0] + b * usable[1] + c * usable[2] + EPS < tonnage) continue;
                    best = Math.min(best, a * cost[0] + b * cost[1] + c * cost[2]);
                }
            }
        }
        return best;
    }
}
