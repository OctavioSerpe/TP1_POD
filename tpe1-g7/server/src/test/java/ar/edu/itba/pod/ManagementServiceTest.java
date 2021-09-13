package ar.edu.itba.pod;

import ar.edu.itba.pod.models.RunwayCategory;
import ar.edu.itba.pod.server.Servant;
import ar.edu.itba.pod.server.models.Flight;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.rmi.RemoteException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class ManagementServiceTest {

    static private Servant servant;
    static private ExecutorService executorService;
    static final private int N_THREADS = 40;
    static private List<String> runwayNames;
    static final private long TIMEOUT = 60L;
    static final private TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @BeforeClass
    public static void init() {
        servant = new Servant();
        executorService = Executors.newCachedThreadPool();
        runwayNames = Arrays.asList("MANA", "OCTA", "GONZA", "LATITUD");
    }

    @Test
    public void test() throws InterruptedException {
        final List<Callable<Object>> callables = new ArrayList<>();
        runwayNames.forEach(r -> callables.add(() -> {
            try {
                servant.addRunway(r, RunwayCategory.A);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return null;
        }));
        executorService.invokeAll(callables);
        executorService.shutdown();
        executorService.awaitTermination(60L, TimeUnit.SECONDS);
    }

    @Test
    public void testFlights() throws InterruptedException, RemoteException {
        servant.addRunway("MANA RUNWAY", RunwayCategory.A);
        final List<Callable<Object>> callables = new ArrayList<>();
        IntStream.range(0, 2000).forEach(n -> {
            callables.add(() -> {
                servant.requestRunway(String.valueOf(n), "AEROPUERTO MANA", "MANA DE FRUTILLA", RunwayCategory.A);
                return null;
            });
        });
        executorService.invokeAll(callables);
        executorService.shutdown();
        executorService.awaitTermination(TIMEOUT, TIME_UNIT);

        callables.clear();
        IntStream.range(0, 2000).forEach(n -> {
            callables.add(() -> {
                servant.issueDeparture();
                return null;
            });
        });
        ExecutorService auxExecutor = Executors.newCachedThreadPool();
        auxExecutor.invokeAll(callables);
        auxExecutor.shutdown();
        auxExecutor.awaitTermination(TIMEOUT, TIME_UNIT);

        Assert.assertEquals(2000, servant.getAllDepartures().size());
    }

    @Test
    public void testCloseRunways() throws RemoteException, InterruptedException {
        // FIXME: testear con expected de la exception
        servant.addRunway("MANA RUNWAY", RunwayCategory.A);
        final List<Callable<Object>> callables = new ArrayList<>();
        IntStream.range(0, 10000).forEach(n -> {
            callables.add(() -> {
                servant.closeRunway("MANA RUNWAY");
                return null;
            });
        });
        executorService.invokeAll(callables);
        executorService.shutdown();
        executorService.awaitTermination(TIMEOUT, TIME_UNIT);
    }

}
