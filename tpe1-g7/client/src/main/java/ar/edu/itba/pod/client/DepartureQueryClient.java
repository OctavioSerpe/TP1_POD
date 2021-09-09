package ar.edu.itba.pod.client;

import ar.edu.itba.pod.DepartureQueryService;
import ar.edu.itba.pod.ManagementService;
import ar.edu.itba.pod.models.DepartureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;

public class DepartureQueryClient {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        String serverAddress = System.getProperty("serverAddress");
        String airline = System.getProperty("airline");
        String runway = System.getProperty("runway");
        String outPath = System.getProperty("outPath");

        // TODO: Cambiar validaciones a estilo de FlightTrackingClient
        if (serverAddress == null) {
            throw new IllegalArgumentException("Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line");
        } else if (outPath == null) {
            throw new IllegalArgumentException("Missing file path for query output. Please specify it with -DoutPath=fileName when running from the command line");
        } else if (airline != null && runway != null) {
            throw new IllegalArgumentException("Invalid query. Please specify ONLY airline, runway name or neither");
        }

        File outFile = new File(outPath);
        try {
            if (!outFile.createNewFile()){
                // TODO: Preguntar si desea pisar el archivo o no
            }
        } catch (IOException e) {
            logger.error("Error while creating file. Make sure the given path for the file is valid and writeable");
            return;
        }

        if (!outFile.canWrite()) {
            logger.error("Error: file is not writeable. Make sure the given path for the file is writeable");
            return;
        }

        logger.info("tpe1-g7 departure query Starting ...");
        DepartureQueryService service = (DepartureQueryService) Naming.lookup("//" + serverAddress + "/airport");

        List<DepartureData> queryResult;

        if (airline != null) {
            queryResult = service.getAirlineDepartures(airline);
        } else if (runway != null) {
            queryResult = service.getRunwayDepartures(runway);
        } else {
            queryResult = service.getAllDepartures();
        }

        StringBuilder out = new StringBuilder();

        out.append("TakeOffOrders;RunwayName;FlightCode;DestinyAirport;AirlineName\n");
        queryResult.forEach(departure -> out.append(String.format("%d;%s;%s;%s;%s\n",
                departure.getFlightsBeforeDeparture(),
                departure.getRunwayName(),
                departure.getFlightId(),
                departure.getDestinationAirportId(),
                departure.getAirline())));

        try {
            Files.write(outFile.toPath(), out.toString().getBytes());
        } catch (IOException e) {
            logger.error("Error while writing to file. Aborting..." + e.getMessage());
        }
    }
}
