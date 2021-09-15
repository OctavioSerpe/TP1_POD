package ar.edu.itba.pod.client;

import ar.edu.itba.pod.ManagementService;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import ar.edu.itba.pod.models.ReassignmentLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import static ar.edu.itba.pod.client.utils.RunwayCategoryUtils.getRunwayCategory;

public class ManagementClient {
    private static final Logger logger = LoggerFactory.getLogger(ManagementClient.class);
    private static final String MISSING_RUNWAY_NAME = "Missing runway name. Please specify it with -Drunway=runwayName when running from the command line";

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        final String serverAddress = System.getProperty("serverAddress");
        final String action = System.getProperty("action");
        final String runway = System.getProperty("runway");
        final String minCategoryStr = System.getProperty("category");

        String errorMessage = "";
        if (serverAddress == null) {
            errorMessage += "Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line";
        }
        if (action == null) {
            errorMessage += "\nMissing action to do. Please specify it with -Daction=action when running from the command line";
        }

        if (errorMessage.length() > 0) {
            logger.error(errorMessage);
            return;
        }

        logger.info("tpe1-g7 management Starting ...");
        final ManagementService service = (ManagementService) Naming.lookup("//" + serverAddress + "/management");

        switch (action) {
            case "reorder":
                ReassignmentLog log = service.rearrangeDepartures();
                log.getFailed().forEach(f -> logger.info(String.format("Cannot assign Flight %s.", f)));
                logger.info(String.format("%d flights assigned.", log.getAssignedCount()));
                break;
            case "takeOff":
                service.issueDeparture();
                logger.info("Flights in runways departed.");
                break;
            case "add":
                if (runway == null) {
                    errorMessage += MISSING_RUNWAY_NAME;
                }

                if (minCategoryStr == null) {
                    errorMessage += "\nMissing category for new runway. Please specify it with -Dcategory=minCategory when running from the command line";
                }

                if (errorMessage.length() > 0) {
                    logger.error(errorMessage);
                    return;
                }

                try {
                    service.addRunway(runway, getRunwayCategory(minCategoryStr));
                    logger.info("Runway " + runway + " is open.");
                } catch (RunwayAlreadyExistsException e) {
                    logger.info("Runway " + runway + " already exists.");
                }
                break;
            case "open":
            case "close":
                if (runway == null) {
                    logger.error(MISSING_RUNWAY_NAME);
                    return;
                }

                switchRunwayState(runway, service, action.equals("open"));
                break;
            case "status":
                if (runway == null) {
                    logger.error(MISSING_RUNWAY_NAME);
                    return;
                }

                try {
                    logger.info("Runway " + runway + " is " + (service.isRunwayOpen(runway) ? "open." : "closed."));
                } catch (NoSuchRunwayException e) {
                    logger.error("Runway " + runway + " not found.");
                }
                break;
            default:
                logger.error("Invalid action");
        }
    }

    private static void switchRunwayState(String runway, ManagementService service, Boolean openRunway) throws RemoteException {
        try {
            if (openRunway) {
                service.openRunway(runway);
            } else {
                service.closeRunway(runway);
            }
            logger.info("Runway " + runway + " is " + (openRunway ? "open." : "closed."));
        } catch (NoSuchRunwayException e) {
            logger.info("Runway " + runway + " not found.");
        } catch (IllegalStateException e) {
            logger.info("Runway " + runway + " is already " + (openRunway ? "open." : "closed."));
        }
    }

}
