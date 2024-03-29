package ar.edu.itba.pod.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    public static void main(String[] args) throws RemoteException {
        logger.info("tpe1-g7 Server starting ...");

        final Servant servant = new Servant();
        final Registry registry = LocateRegistry.getRegistry("localhost", 0);
        UnicastRemoteObject.exportObject(servant,0);
        registry.rebind("departure_query", servant);
        registry.rebind("flight_tracking", servant);
        registry.rebind("management", servant);
        registry.rebind("runway_request", servant);

        logger.info("tpe1-g7 Server started.");
    }
}
