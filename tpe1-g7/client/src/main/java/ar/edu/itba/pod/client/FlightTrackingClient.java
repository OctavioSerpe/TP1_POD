package ar.edu.itba.pod.client;

import ar.edu.itba.pod.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class FlightTrackingClient {
    private static Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {
        String serverAddress = System.getProperty("serverAddress");

        if (serverAddress == null) {
            throw new IllegalArgumentException("Missing server address and port. Please specify them with -DserverAddress=xx.xx.xx.xx:yyyy when running from the command line");
        }

        logger.info("tpe1-g7 tracking client Starting ...");
        ManagementService handle = (ManagementService) Naming.lookup("//localhost:1099/airport");
        System.out.println(handle.isRunwayOpen("TEEEEEEEEEEEEEEEEEEEEST"));
    }

}
