package ar.edu.itba.pod.client;

import ar.edu.itba.pod.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class ManagementClient {
    private static Logger logger = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) throws MalformedURLException, NotBoundException, RemoteException {

        logger.info("tpe1-g7 management Starting ...");
        ManagementService handle = (ManagementService) Naming.lookup("//localhost:1099/airport");
        System.out.println(handle.isRunwayOpen("TEEEEEEEEEEEEEEEEEEEEST"));
    }

}
