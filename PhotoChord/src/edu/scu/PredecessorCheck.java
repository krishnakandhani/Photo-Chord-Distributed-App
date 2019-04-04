package edu.scu;

import edu.scu.Utils.Util;

import java.net.InetSocketAddress;

public class PredecessorCheck extends Thread {
    private Node _local;
    private boolean _alive;

    public PredecessorCheck(Node _local) {
        this._local = _local;
        _alive = true;
    }

    @Override
    public void run() {
        while (_alive) {
            InetSocketAddress predecessor = _local.getPredecessor();
            if (predecessor != null) {
                String response = Util.sendRequest(predecessor, "KEEP");
                if (response == null || !response.equals("ALIVE")) {
                    _local.clearPredecessor();
                }

            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void toDie() {
        _alive = false;
    }
}
