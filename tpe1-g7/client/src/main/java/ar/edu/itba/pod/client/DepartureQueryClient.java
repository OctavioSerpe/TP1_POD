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
    private static final Logger logger = LoggerFactory.getLogger(DepartureQueryClient.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        final String serverAddress = System.getProperty("serverAddress");
        final String airline = System.getProperty("airline");
        final String runway = System.getProperty("runway");
        final String outPath = System.getProperty("outPath");

        String errorMessage = "";
        if (serverAddress == null) {
            errorMessage += "Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line";
        }
        if (outPath == null) {
            errorMessage += "\nMissing file path for query output. Please specify it with -DoutPath=fileName when running from the command line";
        }
        if (airline != null && runway != null) {
            errorMessage += "\nInvalid query. Please specify ONLY airline, runway name or neither";
        }

        if (errorMessage.length() > 0) {
            logger.error(errorMessage);
            return;
        }

        final File outFile = new File(outPath);
        try {
            if (!outFile.createNewFile()) {
                logger.error("Invalid query. Please specify a path to an output file that does not exists.");
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
        final DepartureQueryService service = (DepartureQueryService) Naming.lookup("//" + serverAddress + "/departure_query");

        final List<DepartureData> queryResult;
        try {
            if (airline != null) {
                queryResult = service.getAirlineDepartures(airline);
            } else if (runway != null) {
                queryResult = service.getRunwayDepartures(runway);
            } else {
                queryResult = service.getAllDepartures();
            }
        } catch (Exception e) {
            logger.error("An unknown error has occurred.");
            return;
        }

        final StringBuilder out = new StringBuilder();

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
