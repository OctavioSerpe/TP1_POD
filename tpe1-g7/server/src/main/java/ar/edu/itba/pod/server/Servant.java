package ar.edu.itba.pod.server;

import ar.edu.itba.pod.ManagementService;

import java.rmi.RemoteException;

public class Servant implements ManagementService {



    @Override
    public void addRunway(String name, String category) throws RemoteException {

    }

    @Override
    public boolean isRunwayOpen(String runway) throws RemoteException {
        return false;
    }

    @Override
    public void openRunway(String runway) throws RemoteException {

    }

    @Override
    public void closeRunway(String runway) throws RemoteException {

    }

    @Override
    public void issueDeparture() throws RemoteException {

    }

    @Override
    public void rearrangeDepartures() throws RemoteException {

    }
}
