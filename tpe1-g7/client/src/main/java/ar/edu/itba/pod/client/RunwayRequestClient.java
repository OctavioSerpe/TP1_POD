package ar.edu.itba.pod.client;

import ar.edu.itba.pod.RunwayRequestService;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;

import static ar.edu.itba.pod.client.utils.RunwayCategoryUtils.getRunwayCategory;

public class RunwayRequestClient {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        String serverAddress = System.getProperty("serverAddress");
        String inPath = System.getProperty("inPath");

        if (serverAddress == null) {
            throw new IllegalArgumentException("Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line");
        } else if (inPath == null) {
            throw new IllegalArgumentException("Missing file path for query input. Please specify it with -DinPath=fileName when running from the command line");
        }

        File inFile = new File(inPath);
        if (!inFile.canRead()) {
            logger.error("Error: file is not readable. Make sure the given path for the file is readable");
            return;
        }
        logger.info("tpe1-g7 runway request client Starting ...");
        RunwayRequestService service = (RunwayRequestService) Naming.lookup("//" + serverAddress + "/runway_request");

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(inPath));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        long assignedFlightsCount = 0;
        lines.remove(0);
        for (String currentLine : lines) {
            String[] flightData = currentLine.split(";");
            try {
                service.requestRunway(
                        flightData[0],
                        flightData[1],
                        flightData[2],
                        getRunwayCategory(flightData[3])
                );
                assignedFlightsCount++;
            } catch (NoSuchRunwayException e) {
                System.out.printf("Cannot assign Flight %s.\n", flightData[0]);
            } catch (RemoteException e) {
                e.printStackTrace();
                // TODO ????
            }
        }

        System.out.println(assignedFlightsCount + " flights assigned.");
    }
}
