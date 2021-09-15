package ar.edu.itba.pod;

import ar.edu.itba.pod.exceptions.NoSuchFlightException;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import ar.edu.itba.pod.models.DepartureData;
import ar.edu.itba.pod.models.RunwayCategory;
import ar.edu.itba.pod.server.Servant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.lang.reflect.Field;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.mockito.Mockito.*;

public class ServantTest {

    static final private int N_THREADS = 40;
    static final private long AWAIT_TERMINATION_TIMEOUT = 60L;
    static final private long TIMEOUT = 5L;
    static final private TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    static final private String RUNWAY_NAME = "POD RUNWAY";
    static final private String DESTINATION_AIRPORT_ID = "POD AIRPORT";
    static final private String AIRLINE_NAME = "POD AIRLINE";
    static final private String FLIGHT_ID = "POD";
    static final private int TOTAL_FLIGHTS = 2000;
    static final private int TOTAL_OPENED_RUNWAYS = 2000;
    static final private int TOTAL_RUNWAYS = 4;
    static final private Supplier<ExecutorService> executorServiceSupplier = () -> Executors.newFixedThreadPool(N_THREADS);
    static private Servant servant;
    static private ExecutorService executorService;
    static private List<String> runwayNames = Arrays.asList("MANAGEMENT RUNWAY", "QUERY RUNWAY", "TRACKING RUNWAY", "DEPARTURE RUNWAY",
            "ALU RUNWAY", "LINUX RUNWAY");
    static private List<String> airlinesNames = Arrays.asList("MANAGEMENT AIRLINE", "QUERY AIRLINE", "TRACKING AIRLINE", "DEPARTURE AIRLINE");

    @Before
    public void init() {
        servant = new Servant();
        executorService = executorServiceSupplier.get();
    }

    /*
     * Se emiten vuelos concurrentemente y luego se evalua si la informacion devuelta
     * es la esperada
     */
    @Test
    public void testFlightsAndDepartureData() throws InterruptedException, RemoteException, ExecutionException, TimeoutException {
        final LocalDateTime departedOn = LocalDateTime.now();
        LocalDateTime departedOnCopy = departedOn;
        int flightsDeparted = 0;

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
        ExecutorService auxExecutor = executorServiceSupplier.get();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }
        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        final List<DepartureData> departureData = servant.getAllDepartures();
        Assert.assertEquals(TOTAL_FLIGHTS, departureData.size());
        Assert.assertTrue(departureData.stream().allMatch(f -> f.getAirline().equals(AIRLINE_NAME) &&
                f.getDestinationAirportId().equals(DESTINATION_AIRPORT_ID) &&
                f.getRunwayName().equals(RUNWAY_NAME)));

        // comparo tanto la fecha de salida como los vuelos previos, dado que los devuelve ordenados
        for (DepartureData data : departureData) {
            Assert.assertTrue(departedOnCopy.isEqual(data.getDepartedOn()) ||
                    departedOnCopy.isBefore(data.getDepartedOn()));
            Assert.assertEquals(flightsDeparted++, data.getFlightsBeforeDeparture());
            departedOnCopy = data.getDepartedOn();
        }

        departureData.clear();

        departureData.addAll(servant.getAirlineDepartures(AIRLINE_NAME));
        Assert.assertEquals(TOTAL_FLIGHTS, departureData.size());
        Assert.assertTrue(departureData.stream().allMatch(f -> f.getAirline().equals(AIRLINE_NAME) &&
                f.getDestinationAirportId().equals(DESTINATION_AIRPORT_ID) &&
                f.getRunwayName().equals(RUNWAY_NAME)));

        // reseteo
        departedOnCopy = departedOn;
        flightsDeparted = 0;
        for (DepartureData data : departureData) {
            Assert.assertTrue(departedOnCopy.isEqual(data.getDepartedOn()) ||
                    departedOnCopy.isBefore(data.getDepartedOn()));
            Assert.assertEquals(flightsDeparted++, data.getFlightsBeforeDeparture());
            departedOnCopy = data.getDepartedOn();
        }

        departureData.clear();

        departureData.addAll(servant.getRunwayDepartures(RUNWAY_NAME));
        Assert.assertEquals(TOTAL_FLIGHTS, departureData.size());
        Assert.assertTrue(departureData.stream().allMatch(f -> f.getAirline().equals(AIRLINE_NAME) &&
                f.getDestinationAirportId().equals(DESTINATION_AIRPORT_ID) &&
                f.getRunwayName().equals(RUNWAY_NAME)));

        departedOnCopy = departedOn;
        flightsDeparted = 0;
        for (DepartureData data : departureData) {
            Assert.assertTrue(departedOnCopy.isEqual(data.getDepartedOn()) ||
                    departedOnCopy.isBefore(data.getDepartedOn()));
            Assert.assertEquals(flightsDeparted++, data.getFlightsBeforeDeparture());
            departedOnCopy = data.getDepartedOn();
        }
    }

    /*
     * El test agrega una pista y la cierra de manera concurrente, por lo que se fuerza una excepcion de tipo
     * NoSuchRunway, que transmitida al Future se transforma en una ExecutionException
     */
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

    /*
     * El test agrega una pista y la abre de manera concurrente, por lo que se fuerza una excepcion de tipo
     * NoSuchRunway, que transmitida al Future se transforma en una ExecutionException
     */
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

        final List<Future<Object>> futures = executorService.invokeAll(callables);
        for (Future<Object> future : futures) {
            Assert.assertThrows(ExecutionException.class, () -> future.get(TIMEOUT, TIME_UNIT));
        }
        executorService.shutdown();
        executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);
    }

    /*
     * El test agrega pistas, pide pista, emite vuelos y luego verifica si la informacion devuelta es la correcta
     * respecto a los vuelos, pistas y aerolineas
     */
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

        ExecutorService auxExecutor = executorServiceSupplier.get();
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

    /*
     * El test agrega pistas, pide pista (emitiendo vuelos), luego cierra cierta cantidad de pistas y abre otras de menor categoria,
     * despues reordena los vuelos y los emite para finalmente verificar que la informacion devuelta sea la correcta,
     * tanto por el aeropuerto, sus pistas y las aerolineas
     */
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


        ExecutorService auxExecutor = executorServiceSupplier.get();
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
        // si hay vuelos extra (modulo da > 0 al dividir todos los vuelos entre todas las pistas), tengo que contemplarlos
        // en el orden (ascendente) que se asignaron a las aerolineas
        for (int i = 0; i < TOTAL_TEST_AIRLINES; ++i, --extraFlights) {
            Assert.assertEquals(flightsPerAirline + (extraFlights > 0 ? 1 : 0), servant.getAirlineDepartures(airlinesNames.get(i)).size());
        }
    }

    /*
     * El test agrega pistas, verifica que esten abiertas, luego las cierra y verifica que esten cerradas,
     * finalmente las abre y verifica nuevamente que esten abiertas
     */
    @Test
    public void testOpenAndCloseRunways() throws InterruptedException, ExecutionException, TimeoutException {

        // agrego pistas, el default es que esten abiertas
        final List<Callable<Object>> callables = new ArrayList<>();
        for (int i = 0; i < TOTAL_RUNWAYS; ++i) {
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
        for (int i = 0; i < TOTAL_RUNWAYS; ++i) {
            final int index = i;
            callables.add(() -> {
                servant.closeRunway(runwayNames.get(index));
                Assert.assertFalse(servant.isRunwayOpen(runwayNames.get(index)));
                return null;
            });
        }

        ExecutorService auxExecutor = executorServiceSupplier.get();
        futures.clear();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }

        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        callables.clear();

        // nuevamente abro las pistas
        for (int i = 0; i < TOTAL_RUNWAYS; ++i) {
            final int index = i;
            callables.add(() -> {
                servant.openRunway(runwayNames.get(index));
                Assert.assertTrue(servant.isRunwayOpen(runwayNames.get(index)));
                return null;
            });
        }

        auxExecutor = executorServiceSupplier.get();
        futures.clear();
        futures.addAll(auxExecutor.invokeAll(callables));
        for (Future<Object> future : futures) {
            future.get(TIMEOUT, TIME_UNIT);
        }

        auxExecutor.shutdown();
        auxExecutor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

    }

    /*
     * El test verifica que se arroje la excepcion RunwayAlreadyExistsException al agregar pistas duplicadas
     */
    @Test
    public void testAddRunwayExceptions() throws RemoteException {
        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        Assert.assertThrows(RunwayAlreadyExistsException.class, () -> servant.addRunway(RUNWAY_NAME, RunwayCategory.B));
    }

    /*
     * El test verifica que se arroje la excepcion NoSuchRunwayException al preguntar si una pista
     * inexistente se encuentra abierta
     */
    @Test
    public void testIsRunwayOpenExceptions() {
        Assert.assertThrows(NoSuchRunwayException.class, () -> servant.isRunwayOpen(RUNWAY_NAME));
    }

    /*
     * El test verifica que se arroje la excepcion NoSuchRunwayException al abrir una pista inexistente
     * y IllegalStateException al abrir una pista ya abierta (inicialmente empiezan todas abiertas)
     */
    @Test
    public void testOpenRunwayExceptions() throws RemoteException {
        final ThrowingRunnable throwingRunnable = () -> servant.openRunway(RUNWAY_NAME);
        Assert.assertThrows(NoSuchRunwayException.class, throwingRunnable);
        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        Assert.assertThrows(IllegalStateException.class, throwingRunnable);
    }

    /*
     * El test verifica que se arroje la excepcion NoSuchRunwayException al cerrar una pista inexistente
     * y IllegalStateException al cerrar una pista ya cerrada
     */
    @Test
    public void testCloseRunwayExceptions() throws RemoteException {
        final ThrowingRunnable throwingRunnable = () -> servant.closeRunway(RUNWAY_NAME);
        Assert.assertThrows(NoSuchRunwayException.class, throwingRunnable);
        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        servant.closeRunway(RUNWAY_NAME);
        Assert.assertThrows(IllegalStateException.class, throwingRunnable);
    }

    /*
     * El test verifica que se arroje la excepcion NoSuchRunwayException al solicitar pista cuando no las hay
     * y luego cuando las hay pero los vuelos solicitados son de una categoria inferior o las mismas pistas
     * se encuentran cerradas
     */
    @Test
    public void testRequestRunwayExceptions() throws RemoteException {
        final ThrowingRunnable throwingRunnableCategoryA = () ->
                servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);
        final ThrowingRunnable throwingRunnableCategoryB = () ->
                servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.B);

        Assert.assertThrows(NoSuchRunwayException.class, throwingRunnableCategoryA);

        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        Assert.assertThrows(NoSuchRunwayException.class, throwingRunnableCategoryB);

        servant.closeRunway(RUNWAY_NAME);
        Assert.assertThrows(NoSuchRunwayException.class, throwingRunnableCategoryA);
    }

    /*
     * El test verifica que se arroje la excepcion NoSuchRunwayException cuando se pida la informacion
     * de una pista inexistente
     */
    @Test
    public void testGetRunwayDeparturesExceptions() {
        Assert.assertThrows(NoSuchRunwayException.class, () -> servant.getRunwayDepartures(RUNWAY_NAME));
    }

    /*
     * El test busca forzar una reasignación de pista y comprobar el llamado
     * al callback que corresponde
     */
    @Test
    public void testCallbackOnRunwayAssignment() throws RemoteException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        final FlightTrackingCallbackHandler handler = mock(FlightTrackingCallbackHandler.class);

        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);

        servant.subscribe(FLIGHT_ID, AIRLINE_NAME, handler);

        servant.addRunway(RUNWAY_NAME + "2", RunwayCategory.A);
        servant.closeRunway(RUNWAY_NAME);

        servant.rearrangeDepartures();

        final Field executorField = Servant.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        final ExecutorService executor = (ExecutorService) executorField.get(servant);

        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        verify(handler, times(2)).onRunwayAssignment(anyString(), anyString(), anyString(), anyLong());
    }

    /*
     * El test busca forzar un cambio en la cola para un cierto vuelo y verificar que se llame
     * al callback que corresponde
     */
    @Test
    public void testCallbackOnQueuePositionUpdate() throws RemoteException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        final FlightTrackingCallbackHandler handler = mock(FlightTrackingCallbackHandler.class);

        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);
        servant.requestRunway(FLIGHT_ID + "2", DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);

        servant.subscribe(FLIGHT_ID + "2", AIRLINE_NAME, handler);

        servant.issueDeparture();

        final Field executorField = Servant.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        final ExecutorService executor = (ExecutorService) executorField.get(servant);

        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        verify(handler, times(1)).onRunwayAssignment(anyString(), anyString(), anyString(), anyLong());
        verify(handler, times(1)).onQueuePositionUpdate(anyString(), anyString(), anyString(), anyLong());
    }

    /*
     * El test verifica que se llame al callback de onDeparture() en caso de que
     * el vuelo de interés despegue
     */
    @Test
    public void testCallbackOnDeparture() throws RemoteException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        final FlightTrackingCallbackHandler handler = mock(FlightTrackingCallbackHandler.class);

        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);

        servant.subscribe(FLIGHT_ID, AIRLINE_NAME, handler);

        servant.issueDeparture();

        final Field executorField = Servant.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        final ExecutorService executor = (ExecutorService) executorField.get(servant);

        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        verify(handler, times(1)).onRunwayAssignment(anyString(), anyString(), anyString(), anyLong());
        verify(handler, times(1)).onDeparture(anyString(), anyString(), anyString());
    }

    /*
     * El test verifica que se llame al callback de endProcess() en caso de que
     * el vuelo de interés despegue, para finalizar la comunicación con el cliente
     */
    @Test
    public void testProcessEndOnDeparture() throws RemoteException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        final FlightTrackingCallbackHandler handler = mock(FlightTrackingCallbackHandler.class);

        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);
        servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);

        servant.subscribe(FLIGHT_ID, AIRLINE_NAME, handler);

        servant.issueDeparture();

        final Field executorField = Servant.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        final ExecutorService executor = (ExecutorService) executorField.get(servant);

        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TIME_UNIT);

        verify(handler, times(1)).onRunwayAssignment(anyString(), anyString(), anyString(), anyLong());
        verify(handler, times(1)).endProcess();
    }

    /*
     * El test verifica que se arroje la excepcion NoSuchFlightException a la hora de suscribirse
     * a los eventos de una aerolinea y/o vuelo inexistente/es
     */
    @Test
    public void testSubscribeExceptions() throws RemoteException {
        final FlightTrackingCallbackHandler handler = mock(FlightTrackingCallbackHandler.class);

        servant.addRunway(RUNWAY_NAME, RunwayCategory.A);

        final ThrowingRunnable throwingRunnableSubscribe = () -> servant.subscribe(FLIGHT_ID, AIRLINE_NAME, handler);

        // no existe ni el vuelo ni la aerolinea (nunca se pidio pista)
        Assert.assertThrows(NoSuchFlightException.class, throwingRunnableSubscribe);

        // agrego un vuelo para otra aerolinea
        servant.requestRunway(FLIGHT_ID, DESTINATION_AIRPORT_ID, AIRLINE_NAME + "2", RunwayCategory.A);
        // no existe la aerolinea solicitada
        Assert.assertThrows(NoSuchFlightException.class, throwingRunnableSubscribe);

        // despego el vuelo
        servant.issueDeparture();

        // agrego otro vuelo otra aerolinea
        servant.requestRunway(FLIGHT_ID + "2", DESTINATION_AIRPORT_ID, AIRLINE_NAME, RunwayCategory.A);
        // no existe el codigo de vuelo solicitado
        Assert.assertThrows(NoSuchFlightException.class, throwingRunnableSubscribe);

        // despego el vuelo
        servant.issueDeparture();

        // agrego tanto vuelo como aerolineas nuevas
        servant.requestRunway(FLIGHT_ID + "2", DESTINATION_AIRPORT_ID, AIRLINE_NAME + "2", RunwayCategory.A);
        // no existe el vuelo ni la aerolinea
        Assert.assertThrows(NoSuchFlightException.class, throwingRunnableSubscribe);
    }
}
