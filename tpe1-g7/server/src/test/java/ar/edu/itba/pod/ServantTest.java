package ar.edu.itba.pod;

import ar.edu.itba.pod.models.RunwayCategory;
import ar.edu.itba.pod.server.Servant;
import ar.edu.itba.pod.server.models.Flight;
import ar.edu.itba.pod.server.models.Runway;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.rmi.RemoteException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class ServantTest {

    static private Servant servant;
    static private ExecutorService executorService;
    static final private int N_THREADS = 8;
    static private List<String> runwayNames = Arrays.asList("MANAGEMENT RUNWAY", "QUERY RUNWAY", "TRACKING RUNWAY", "DEPARTURE RUNWAY",
            "ALU RUNWAY", "LINUX RUNWAY");
    static private List<String> airlinesNames = Arrays.asList("MANAGEMENT AIRLINE", "QUERY AIRLINE", "TRACKING AIRLINE", "DEPARTURE AIRLINE");
    static final private long AWAIT_TERMINATION_TIMEOUT = 60L;
    static final private long TIMEOUT = 5L;
    static final private TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    static final private String RUNWAY_NAME = "POD RUNWAY";
    static final private String DESTINATION_AIRPORT_ID = "POD AIRPORT";
    static final private String AIRLINE_NAME = "POD AIRLINE";
    static final private int TOTAL_FLIGHTS = 2000;
    static final private int TOTAL_OPENED_RUNWAYS = 2000;
    static final private int TOTAL_RUNWAYS = 4;
    static final private Supplier<ExecutorService> supplyThreadPool = () -> Executors.newFixedThreadPool(N_THREADS);

    @Before
    public void init() {
        servant = new Servant();
        executorService = supplyThreadPool.get();
    }

    @Test
    public void testFlights() throws InterruptedException, RemoteException, ExecutionException, TimeoutException {
        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        final List<Callable<Object>> callables = new ArrayList<>();
        IntStream.range(0, TOTAL_FLIGHTS).forEach(n -> {
            callables.add(() -> {
                servant.requestRunway(String.valueOf(n), DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);
                return null;
            });
        });
        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        callables.clear();
        IntStream.range(0, TOTAL_FLIGHTS).forEach(n -> {
            callables.add(() -> {
                servant.issueDeparture();
                return null;
            });
        });

        futures.clear();
        ExecutorService auxExecutor = supplyThreadPool.get();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        Assert.assertEquals(TOTAL_FLIGHTS, servant.getAllDepartures().size());
    }

    @Test(expected = ExecutionException.class)
    public void testCloseRunways() throws RemoteException, InterruptedException, ExecutionException, TimeoutException {
        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        final List<Callable<Object>> callables = new ArrayList<>();
        IntStream.range(0, TOTAL_OPENED_RUNWAYS).forEach(n -> {
            callables.add(() -> {
                servant.closeRunway(RUNWAY_NAME);
                return null;
            });
        });

        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);
    }

    @Test
    public void testOpenRunways() throws RemoteException, InterruptedException {
        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        final List<Callable<Object>> callables = new ArrayList<>();
        IntStream.range(0, TOTAL_OPENED_RUNWAYS).forEach(n -> {
            callables.add(() -> {
                servant.openRunway(RUNWAY_NAME);
                return null;
            });
        });

        // asi chequeamos las excepciones con un throwing runnable
        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            Assert.assertThrows(ExecutionException.class, () -> future.get(TIMEOUT, TIME_UNIT));
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);
    }

    @Test
    public void testDepartures() throws RemoteException, InterruptedException, ExecutionException, TimeoutException {

        final int TOTAL_TEST_FLIGHTS = 2000;
        final int TOTAL_TEST_RUNWAYS = 4;

        for (int i = 0; i < TOTAL_TEST_RUNWAYS; ++i)
            servant.addRunway(runwayNames.get(i), RunwayCategory.values()[i]);
        final List<Callable<Object>> callables = new ArrayList<>();

        // el proposito es que sean multiplos de ser posible (en este test falla sino)
        final int flightsPerRunway = (int) Math.floor(((double) TOTAL_TEST_FLIGHTS) / TOTAL_TEST_RUNWAYS);

        // mapeo los vuelos 0 - 499 a la categoria D, 500 a 999 a la categoria C, 1000 a 1499 a la categoria B y 1500 a 1999 a la categoria A
        // esto pasa pues si pido primero por A, acomoda hacia arriba en base a su ocupacion, por lo que arranco desde la de menor categoria
        // de esta manera no se propagan y quedan acomodados como quiero
        for (int i = 0; i < TOTAL_TEST_RUNWAYS; ++i) {
            final int offset = flightsPerRunway * i;
            final int index = i;
            for (int n = 0; n < flightsPerRunway; ++n) {
                final int flightId = n + offset;
                callables.add(() -> {
                    servant.requestRunway(String.valueOf(flightId),
                            DESTINATION_AIRPORT_ID,
                            airlinesNames.get(index),
                            RunwayCategory.values()[TOTAL_TEST_RUNWAYS - index - 1]);
                    return null;
                });
            }
        }

        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        ExecutorService auxExecutor = supplyThreadPool.get();
        callables.clear();
        futures.clear();
        IntStream.range(0, TOTAL_TEST_FLIGHTS).forEach(n -> {
            callables.add(() -> {
                servant.issueDeparture();
                return null;
            });
        });

        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        Assert.assertEquals(TOTAL_TEST_FLIGHTS, servant.getAllDepartures().size());
        for (int i = 0; i < TOTAL_TEST_RUNWAYS; ++i) {
            Assert.assertEquals(flightsPerRunway, servant.getRunwayDepartures(runwayNames.get(i)).size());
            Assert.assertEquals(flightsPerRunway, servant.getAirlineDepartures(airlinesNames.get(i)).size());
        }
    }

    @Test
    public void testRearrangeAndDepartures() throws RemoteException, InterruptedException, ExecutionException, TimeoutException {

        final int TOTAL_TEST_FLIGHTS = 2000;
        final int TOTAL_TEST_RUNWAYS = 4;
        final int TOTAL_TEST_AIRLINES = 3;

        for (int i = 0; i < (TOTAL_TEST_RUNWAYS - 1); ++i)
            servant.addRunway(runwayNames.get(i), RunwayCategory.values()[i]);
        final List<Callable<Object>> callables = new ArrayList<>();

        // guardo los vuelos que "sobran", estos se acomodan de menor a mayor categoria en valor
        int extraFlights = TOTAL_TEST_FLIGHTS % (TOTAL_TEST_RUNWAYS - 1);

        // el proposito es que sean multiplos de ser posible, sino cambiar la resta al denominador
        int flightsPerRunway = (int) Math.floor(((double) TOTAL_TEST_FLIGHTS - extraFlights) / (TOTAL_TEST_RUNWAYS - 1));
        final int flightsPerAirline = flightsPerRunway;

        // aca propaga hacia arriba, entonces la cantidad de vuelos por pista X e Y donde categoria X < categoria Y (comparing
        // por string) y son consecutivos, la cantidad de vuelos en X ~ nY con n entero y >= 2
        // no nos preocupa esto, pues luego cierro todas las pistas menos la de mayor valor string en categoria (persiste la pista C)
        for (int i = 0; i < (TOTAL_TEST_RUNWAYS - 1); ++i) {
            final int offset = flightsPerRunway * i;
            final int index = i;
            for (int n = 0; n < flightsPerRunway; ++n) {
                final int flightId = n + offset;
                callables.add(() -> {
                    servant.requestRunway(String.valueOf(flightId),
                            DESTINATION_AIRPORT_ID,
                            airlinesNames.get(index),
                            RunwayCategory.values()[index]);
                    return null;
                });
            }
        }

        // agrego los vuelos extra
        for (int flightsToAdd = extraFlights, i = 0; flightsToAdd > 0; --flightsToAdd, ++i) {
            final int flightId = TOTAL_TEST_FLIGHTS - flightsToAdd;
            final int index = i;
            callables.add(() -> {
                servant.requestRunway(String.valueOf(flightId),
                        DESTINATION_AIRPORT_ID,
                        airlinesNames.get(index),
                        RunwayCategory.values()[index]);
                return null;
            });
        }


        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        // cierro las runways de categorias A y B => persiste la pista C y alli se acumulan todos los vuelos
        for (int i = 0; i < (TOTAL_TEST_RUNWAYS - 2); ++i)
            servant.closeRunway(runwayNames.get(i));

        // agrego las runways de categorias D, E y F, con sus respectivos nombres
        for (int i = 0; i < (TOTAL_TEST_RUNWAYS - 1); ++i)
            servant.addRunway(runwayNames.get(TOTAL_TEST_RUNWAYS + i - 1), RunwayCategory.values()[TOTAL_TEST_RUNWAYS + i - 1]);

        // acomodo en las nuevas pistas, tienen 4 runways para acomodarse (C, D, E y F) pues todos los vuelos son de categoria <= C
        servant.rearrangeDepartures();


        ExecutorService auxExecutor = supplyThreadPool.get();
        callables.clear();
        IntStream.range(0, TOTAL_TEST_FLIGHTS).forEach(n -> {
            callables.add(() -> {
                servant.issueDeparture();
                return null;
            });
        });

        futures.clear();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }

        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        Assert.assertEquals(TOTAL_TEST_FLIGHTS, servant.getAllDepartures().size());

        // cantidad de pistas previas a la C
        final int offset = 2;
        flightsPerRunway = TOTAL_TEST_FLIGHTS / TOTAL_TEST_RUNWAYS;
        for (int i = 0; i < TOTAL_TEST_RUNWAYS; ++i) {
            Assert.assertEquals(flightsPerRunway, servant.getRunwayDepartures(runwayNames.get(i + offset)).size());
        }

        // chequeo por aerolineas, las cuales son independientes de las pistas
        for (int i = 0; i < TOTAL_TEST_AIRLINES; ++i, --extraFlights) {
            Assert.assertEquals(flightsPerAirline + (extraFlights > 0 ? 1 : 0), servant.getAirlineDepartures(airlinesNames.get(i)).size());
        }
    }

    @Test
    public void testOpenRunway() throws InterruptedException, ExecutionException, TimeoutException {

        // agrego pistas, el default es que esten abiertas
        final List<Callable<Object>> callables = new ArrayList<>();
        for(int i = 0 ; i < TOTAL_RUNWAYS; ++i) {
            final int index = i;
            callables.add(() -> {
                servant.addRunway(runwayNames.get(index), RunwayCategory.A);
                Assert.assertTrue(servant.isRunwayOpen(runwayNames.get(index)));
                return null;
            });
        }

        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        callables.clear();

        // cierro todas las pistas
        for(int i = 0 ; i < TOTAL_RUNWAYS; ++i) {
            final int index = i;
            callables.add(() -> {
                servant.closeRunway(runwayNames.get(index));
                Assert.assertFalse(servant.isRunwayOpen(runwayNames.get(index)));
                return null;
            });
        }

        ExecutorService auxExecutor = supplyThreadPool.get();
        futures.clear();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }

        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        callables.clear();
        
        // nuevamente abro las pistas
        for(int i = 0 ; i < TOTAL_RUNWAYS; ++i) {
            final int index = i;
            callables.add(() -> {
                servant.openRunway(runwayNames.get(index));
                Assert.assertTrue(servant.isRunwayOpen(runwayNames.get(index)));
                return null;
            });
        }

        auxExecutor = supplyThreadPool.get();
        futures.clear();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }

        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

    }

}
