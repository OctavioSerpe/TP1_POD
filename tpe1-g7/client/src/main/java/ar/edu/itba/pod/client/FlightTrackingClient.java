package ar.edu.itba.pod.client;

import ar.edu.itba.pod.FlightTrackingCallbackHandler;
import ar.edu.itba.pod.FlightTrackingService;
import ar.edu.itba.pod.client.handlers.LoggerFlightTrackingCallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class FlightTrackingClient {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        String serverAddress = System.getProperty("serverAddress");
        String airline = System.getProperty("airlineName");
        String flightId = System.getProperty("flightCode");

        String errorMessage = "";
        if (serverAddress == null) {
            errorMessage += "Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line";
        }
        if (airline == null) {
            errorMessage += "\nMissing airline name. Please specify it with -DairlineName=airlineName when running from the command line";
        }
        if (flightId == null) {
            errorMessage += "\nMissing flight code. Please specify it with -DflightCode=flightCode when running from the command line";
        }

        if (errorMessage.length() > 0) {
            System.out.println(errorMessage);
            return;
        }

        logger.info("tpe1-g7 tracking client Starting ...");
        final FlightTrackingService service = (FlightTrackingService) Naming.lookup("//" + serverAddress + "/airport");
        final FlightTrackingCallbackHandler handler = new LoggerFlightTrackingCallbackHandler();

        UnicastRemoteObject.exportObject(handler, 0);

        service.subscribe(flightId, airline, handler);
    }

}
