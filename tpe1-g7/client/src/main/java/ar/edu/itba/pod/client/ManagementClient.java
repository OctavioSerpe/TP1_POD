package ar.edu.itba.pod.client;

import ar.edu.itba.pod.ManagementService;
import ar.edu.itba.pod.exceptions.NoSuchRunwayException;
import ar.edu.itba.pod.exceptions.RunwayAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import static ar.edu.itba.pod.client.utils.RunwayCategoryUtils.getRunwayCategory;

public class ManagementClient {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        String serverAddress = System.getProperty("serverAddress");
        String action = System.getProperty("action");
        String runway = System.getProperty("runway");
        String minCategoryStr = System.getProperty("category");

        if (serverAddress == null) {
            throw new IllegalArgumentException("Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line");
        } else if (action == null) {
            throw new IllegalArgumentException("Missing action to do. Please specify it with -Daction=action when running from the command line");
        }

        logger.info("tpe1-g7 management Starting ...");
        ManagementService service = (ManagementService) Naming.lookup("//" + serverAddress + "/airport");

        switch (action) {
            case "reorder":
                //TODO: IMPLEMENTAR
                service.rearrangeDepartures();
                break;
            case "takeOff":
                service.issueDeparture();
                System.out.println("Flights in runways departed.");
                break;
            case "add":
                checkRunwayIsNotNull(runway);
                if (minCategoryStr == null)
                    throw new IllegalArgumentException("Missing category for new runway. Please specify it with -Dcategory=minCategory when running from the command line");

                try {
                    service.addRunway(runway, getRunwayCategory(minCategoryStr));
                    System.out.println("Runway " + runway + " is open.");
                } catch (RunwayAlreadyExistsException e) {
                    System.out.println("Runway " + runway + " already exists.");
                }
                break;
            case "open":
            case "close":
                checkRunwayIsNotNull(runway);
                switchRunwayState(runway, service, action.equals("open"));
                break;
            case "status":
                checkRunwayIsNotNull(runway);
                try {
                    System.out.println("Runway " + runway + " is " + (service.isRunwayOpen(runway) ? "open." : "closed."));
                } catch (NoSuchRunwayException e) {
                    System.out.println("Runway " + runway + " not found.");
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid action");
        }
    }

    private static void switchRunwayState(String runway, ManagementService service, Boolean openRunway) throws RemoteException {
        try {
            if (openRunway) {
                service.openRunway(runway);
            } else {
                service.closeRunway(runway);
            }
            System.out.println("Runway " + runway + " is " + (openRunway ? "open." : "closed."));
        } catch (NoSuchRunwayException e) {
            System.out.println("Runway " + runway + " not found.");
        } catch (IllegalStateException e) {
            System.out.println("Runway " + runway + " is already " + (openRunway ? "open." : "closed."));
        }
    }

    private static void checkRunwayIsNotNull(String runway) {
        if (runway == null)
            throw new IllegalArgumentException("Missing runway name. Please specify it with -Drunway=runwayName when running from the command line");
    }
}
