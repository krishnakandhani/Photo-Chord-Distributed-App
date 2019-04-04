package edu.scu;

import edu.scu.Threads.FixFingerThread;
import edu.scu.Threads.ListenerThread;
import edu.scu.Threads.StabilizeThread;
import edu.scu.Utils.Util;
import edu.scu.Utils.Logger;

import java.net.InetSocketAddress;
import java.util.HashMap;

import static edu.scu.Utils.Constants.sizeOfFingerTable;

/**
 * @author Raghav Bhandari
 * @author Krishna Kandhani
 * @author Abhiman Kolte
 * @author Dhruv Mevada
 */
public class Node {
    private long localId;
    private InetSocketAddress localAddress;
    private InetSocketAddress predecessor;
    private HashMap<Integer, InetSocketAddress> finger;

    private ListenerThread listener;
    private StabilizeThread stabilize;
    private FixFingerThread fixFingers;
    private PredecessorCheck askPredecessor;

    public Node(InetSocketAddress address)
    {

        localAddress = address;
        localId = Util.hashSocketAddress(localAddress);

        // initialize an empty finger table
        finger = new HashMap<Integer, InetSocketAddress>();
        for (int i = 0; i < sizeOfFingerTable; i++)
        {
            updateIthFinger(i, null);
        }

        // initialize predecessor
        predecessor = null;

        // initialize threads
        listener = new ListenerThread(this);
        stabilize = new StabilizeThread(this);
        fixFingers = new FixFingerThread(this);
        askPredecessor = new PredecessorCheck(this);
    }

    /**
     * Creates a new chord ring or joins an existing ring.
     *
     * @param contact
     * @return true if successfully create a ring
     * or join a ring via contact
     */
    public boolean join(InetSocketAddress contact)
    {

        // if contact is other node (join ring), try to contact that node
        if (contact != null && !contact.equals(localAddress))
        {
            InetSocketAddress successor = Util.requestAddress(contact, "FINDSUCC_" + localId);

            if (successor == null)
            {
                Logger.log("\nCannot find node you are trying to contact, exiting.\n");
                return false;
            }

            updateIthFinger(1, successor);
        }

        // start all threads
        listener.start();
        stabilize.start();
        fixFingers.start();
        askPredecessor.start();

        return true;
    }

    /**
     * Notify successor that this node should be its predecessor
     *
     * @param successor
     * @return successor's response
     */
    public String notify(InetSocketAddress successor) {
        if (successor != null && !successor.equals(localAddress))
            return Util.sendRequest(successor, "IAMPRE_" + localAddress.getAddress().toString() + ":" + localAddress.getPort());
        else
            return null;
    }

    /**
     * Being notified by another node, set it as my predecessor if it is.
     *
     * @param newpre
     */
    public void notified(InetSocketAddress newpre) {
        if (predecessor == null || predecessor.equals(localAddress)) {
            this.setPredecessor(newpre);
        } else {
            long oldpre_id = Util.hashSocketAddress(predecessor);
            long local_relative_id = Util.computeRelativeId(localId, oldpre_id);
            long newpre_relative_id = Util.computeRelativeId(Util.hashSocketAddress(newpre), oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id)
                this.setPredecessor(newpre);
        }
    }

    /**
     * Ask current node to find id's successor.
     *
     * @param id
     * @return id's successor's socket address
     */
    public InetSocketAddress find_successor(long id) {

        // initialize return value as this node's successor (might be null)
        InetSocketAddress ret = this.getSuccessor();

        // find predecessor
        InetSocketAddress pre = find_predecessor(id);

        // if other node found, ask it for its successor
        if (!pre.equals(localAddress))
            ret = Util.requestAddress(pre, "YOURSUCC");

        // if ret is still null, set it as local node, return
        if (ret == null)
            ret = localAddress;

        return ret;
    }

    /**
     * Ask current node to find id's predecessor
     */
    private InetSocketAddress find_predecessor(long findid) {
        InetSocketAddress n = this.localAddress;
        InetSocketAddress n_successor = this.getSuccessor();
        InetSocketAddress most_recently_alive = this.localAddress;
        long n_successor_relative_id = 0;
        if (n_successor != null)
            n_successor_relative_id = Util.computeRelativeId(Util.hashSocketAddress(n_successor), Util.hashSocketAddress(n));
        long findid_relative_id = Util.computeRelativeId(findid, Util.hashSocketAddress(n));

        while (!(findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id)) {

            // temporarily save current node
            InetSocketAddress pre_n = n;

            // if current node is local node, find my closest
            if (n.equals(this.localAddress)) {
                n = this.closest_preceding_finger(findid);
            }

            // else current node is remote node, sent request to it for its closest
            else {
                InetSocketAddress result = Util.requestAddress(n, "CLOSEST_" + findid);

                // if fail to get response, set n to most recently
                if (result == null) {
                    n = most_recently_alive;
                    n_successor = Util.requestAddress(n, "YOURSUCC");
                    if (n_successor == null) {
                        Logger.log("It's not possible.");
                        return localAddress;
                    }
                    continue;
                }

                // if n's closest is itself, return n
                else if (result.equals(n))
                    return result;

                    // else n's closest is other node "result"
                else {
                    // set n as most recently alive
                    most_recently_alive = n;
                    // ask "result" for its successor
                    n_successor = Util.requestAddress(result, "YOURSUCC");
                    // if we can get its response, then "result" must be our next n
                    if (n_successor != null) {
                        n = result;
                    }
                    // else n sticks, ask n's successor
                    else {
                        n_successor = Util.requestAddress(n, "YOURSUCC");
                    }
                }

                // compute relative ids for while loop judgement
                n_successor_relative_id = Util.computeRelativeId(Util.hashSocketAddress(n_successor), Util.hashSocketAddress(n));
                findid_relative_id = Util.computeRelativeId(findid, Util.hashSocketAddress(n));
            }
            if (pre_n.equals(n))
                break;
        }
        return n;
    }

    /**
     * Return closest finger preceding node.
     *
     * @param findid
     * @return closest finger preceding node's socket address
     */
    public InetSocketAddress closest_preceding_finger(long findid) {
        long findid_relative = Util.computeRelativeId(findid, localId);

        // check from last item in finger table
        for (int i = sizeOfFingerTable; i > 0; i--) {
            InetSocketAddress ith_finger = finger.get(i);
            if (ith_finger == null) {
                continue;
            }
            long ith_finger_id = Util.hashSocketAddress(ith_finger);
            long ith_finger_relative_id = Util.computeRelativeId(ith_finger_id, localId);

            // if its relative id is the closest, check if its alive
            if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative) {
                String response = Util.sendRequest(ith_finger, "KEEP");

                //it is alive, return it
                if (response != null && response.equals("ALIVE")) {
                    return ith_finger;
                }

                // else, remove its existence from finger table
                else {
                    updateFingers(-2, ith_finger);
                }
            }
        }
        return localAddress;
    }

    /**
     * Update the finger table based on parameters.
     * Synchronize, all threads trying to modify
     * finger table only through this method.
     *
     * @param i:    index or command code
     * @param value
     */
    public synchronized void updateFingers(int i, InetSocketAddress value) {

        // valid index in [1, sizeOfFingerTable], just update the ith finger
        if (i > 0 && i <= sizeOfFingerTable) {
            updateIthFinger(i, value);
        }

        // caller wants to delete
        else if (i == -1) {
            deleteSuccessor();
        }

        // caller wants to delete a finger in table
        else if (i == -2) {
            deleteCertainFinger(value);

        }

        // caller wants to fill successor
        else if (i == -3) {
            fillSuccessor();
        }

    }

    /**
     * Update ith finger in finger table using new value
     *
     * @param i:    index
     * @param value
     */
    private void updateIthFinger(int i, InetSocketAddress value) {
        finger.put(i, value);

        // if the updated one is successor, notify the new successor
        if (i == 1 && value != null && !value.equals(localAddress)) {
            notify(value);
        }
    }

    /**
     * Delete successor and all following fingers equal to successor
     */
    private void deleteSuccessor() {
        InetSocketAddress successor = getSuccessor();

        //nothing to delete, just return
        if (successor == null)
            return;

        // find the last existence of successor in the finger table
        int i = sizeOfFingerTable;
        for (i = sizeOfFingerTable; i > 0; i--) {
            InetSocketAddress ithfinger = finger.get(i);
            if (ithfinger != null && ithfinger.equals(successor))
                break;
        }

        // delete it, from the last existence to the first one
        for (int j = i; j >= 1; j--) {
            updateIthFinger(j, null);
        }

        // if predecessor is successor, delete it
        if (predecessor != null && predecessor.equals(successor))
            setPredecessor(null);

        // try to fill successor
        fillSuccessor();
        successor = getSuccessor();

        // if successor is still null or local node,
        // and the predecessor is another node, keep asking
        // it's predecessor until find local node's new successor
        if ((successor == null || successor.equals(successor)) && predecessor != null && !predecessor.equals(localAddress)) {
            InetSocketAddress p = predecessor;
            InetSocketAddress p_pre = null;
            while (true) {
                p_pre = Util.requestAddress(p, "YOURPRE");
                if (p_pre == null)
                    break;

                // if p's predecessor is node is just deleted,
                // or itself (nothing found in p), or local address,
                // p is current node's new successor, break
                if (p_pre.equals(p) || p_pre.equals(localAddress) || p_pre.equals(successor)) {
                    break;
                }

                // else, keep asking
                else {
                    p = p_pre;
                }
            }

            // update successor
            updateIthFinger(1, p);
        }
    }

    /**
     * Delete a node from the finger table, here "delete" means deleting all existence of this node
     *
     * @param f
     */
    private void deleteCertainFinger(InetSocketAddress f) {
        for (int i = sizeOfFingerTable; i > 0; i--) {
            InetSocketAddress ithfinger = finger.get(i);
            if (ithfinger != null && ithfinger.equals(f))
                finger.put(i, null);
        }
    }

    /**
     * Try to fill successor with candidates in finger table or even predecessor
     */
    private void fillSuccessor() {
        InetSocketAddress successor = this.getSuccessor();
        if (successor == null || successor.equals(localAddress)) {
            for (int i = 2; i <= sizeOfFingerTable; i++) {
                InetSocketAddress ithfinger = finger.get(i);
                if (ithfinger != null && !ithfinger.equals(localAddress)) {
                    for (int j = i - 1; j >= 1; j--) {
                        updateIthFinger(j, ithfinger);
                    }
                    break;
                }
            }
        }
        successor = getSuccessor();
        if ((successor == null || successor.equals(localAddress)) && predecessor != null && !predecessor.equals(localAddress)) {
            updateIthFinger(1, predecessor);
        }

    }


    /**
     * Clear predecessor.
     */
    public void clearPredecessor() {
        setPredecessor(null);
    }

    /**
     * Getters
     *
     * @return the variable caller wants
     */

    public long getId() {
        return localId;
    }

    public InetSocketAddress getAddress() {
        return localAddress;
    }

    public InetSocketAddress getPredecessor() {
        return predecessor;
    }

    /**
     * Set predecessor using a new value.
     *
     * @param pre
     */
    private synchronized void setPredecessor(InetSocketAddress pre) {
        predecessor = pre;
    }

    public InetSocketAddress getSuccessor() {
        if (finger != null && finger.size() > 0) {
            return finger.get(1);
        }
        return null;
    }

    /**
     * Print functions
     */

    public void printNeighbors() {
        Logger.log("\nYou are listening on port " + localAddress.getPort() + "."
                + "\nYour position is " + localAddress + ".");
        InetSocketAddress successor = finger.get(1);

        // if it cannot find both predecessor and successor
        if ((predecessor == null || predecessor.equals(localAddress)) && (successor == null || successor.equals(localAddress))) {
            Logger.log("Your predecessor is yourself.");
            Logger.log("Your successor is yourself.");

        }

        // else, it can find either predecessor or successor
        else {
            if (predecessor != null) {
                Logger.log("Your predecessor is node " + predecessor.getAddress().toString() + ", "
                        + "port " + predecessor.getPort() + ", position " + predecessor + ".");
            } else {
                Logger.log("Your predecessor is updating.");
            }

            if (successor != null) {
                Logger.log("Your successor is node " + successor.getAddress().toString() + ", "
                        + "port " + successor.getPort() + ", position " + successor + ".");
            } else {
                Logger.log("Your successor is updating.");
            }
        }
    }

    public void printDataStructure() {
        Logger.log("\n==============================================================");
        Logger.log("\nLOCAL:\t\t\t\t" + localAddress.toString() + "\t" + (localAddress));
        if (predecessor != null)
            Logger.log("\nPREDECESSOR:\t\t\t" + predecessor.toString() + "\t" + (predecessor));
        else
            Logger.log("\nPREDECESSOR:\t\t\tNULL");
        Logger.log("\nFINGER TABLE:\n");
        for (int i = 1; i < sizeOfFingerTable + 1; i++) {
            long ithstart = Util.ithStart(Util.hashSocketAddress(localAddress), i);
            InetSocketAddress f = finger.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append(i + "\t" + ithstart + "\t\t");
            if (f != null)
                sb.append(f.toString() + "\t" + f);

            else
                sb.append("NULL");
            Logger.log(sb.toString());
        }
        Logger.log("\n==============================================================\n");
    }

    /**
     * Stop all threads in this node.
     */
    public void stopAllThreads()
    {
        if (listener != null)
            listener.toDie();
        if (fixFingers != null)
            fixFingers.toDie();
        if (stabilize != null)
            stabilize.toDie();
        if (askPredecessor != null)
            askPredecessor.toDie();
    }

}
