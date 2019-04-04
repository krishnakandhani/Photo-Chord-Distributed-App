package edu.scu.Threads;

import edu.scu.Node;
import edu.scu.Utils.Util;

import java.net.InetSocketAddress;
import java.util.Random;

/**
 * Fixfingers thread that periodically access a random entry in finger table
 * and fix it.
 *
 * @author Raghav Bhandari
 * @author Krishna Kandhani
 * @author Abhiman Kolte
 * @author Dhruv Mevada
 */

public class FixFingerThread extends Thread {

    Random random;
    boolean alive;
    private Node local;

    public FixFingerThread(Node node) {
        local = node;
        alive = true;
        random = new Random();
    }

    @Override
    public void run() {
        while (alive) {
            int i = random.nextInt(5);
            if(i == 0) { i +=1 ;
            }
//            Logger.log("Checking node #" + i);
            InetSocketAddress ithfinger = local.find_successor(Util.ithStart(local.getId(), i));
            local.updateFingers(i, ithfinger);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void toDie() {
        alive = false;
    }

}