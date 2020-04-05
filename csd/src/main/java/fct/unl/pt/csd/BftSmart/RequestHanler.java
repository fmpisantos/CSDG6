package fct.unl.pt.csd.BftSmart;

import bftsmart.tom.MessageContext;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import org.slf4j.Logger;

import java.io.*;
import java.util.Map;

public class RequestHanler<K,V> extends DefaultSingleRecoverable {

    private Map<K, V> replicaMap;
    private Logger logger;

    @Override
    public void installSnapshot(byte[] bytes) {

    }

    @Override
    public byte[] getSnapshot() {
        return new byte[0];
    }

    @Override
    public byte[] appExecuteOrdered(byte[] command, MessageContext messageContext) {
        byte[] reply = null;
        K key = null;
        V value = null;
        boolean hasReply = false;
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
             ObjectInput objIn = new ObjectInputStream(byteIn);
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
            MapRequestType reqType = (MapRequestType)objIn.readObject();
            switch (reqType) {
                case PUT:
                    key = (K)objIn.readObject();
                    value = (V)objIn.readObject();

                    V oldValue = replicaMap.put(key, value);
                    if (oldValue != null) {
                        objOut.writeObject(oldValue);
                        hasReply = true;
                    }
                    break;
                case GET:
                    key = (K)objIn.readObject();
                    value = replicaMap.get(key);
                    if (value != null) {
                        objOut.writeObject(value);
                        hasReply = true;
                    }
                    break;
                case REMOVE:
                    key = (K)objIn.readObject();
                    value = replicaMap.remove(key);
                    if (value != null) {
                        objOut.writeObject(value);
                        hasReply = true;
                    }
                    break;
                case SIZE:
                    int size = replicaMap.size();
                    objOut.writeInt(size);
                    hasReply = true;
                    break;
                case KEYSET:
                    keySet(objOut);
                    hasReply = true;
                    break;
            }
            if (hasReply) {
                objOut.flush();
                byteOut.flush();
                reply = byteOut.toByteArray();
            } else {
                reply = new byte[0];
            }

        } catch (IOException | ClassNotFoundException | IOException e) {
            logger.log(Level.SEVERE, "Ocurred during map operation execution", e);
        }
        return reply;
    }

    @Override
    public byte[] appExecuteUnordered(byte[] bytes, MessageContext messageContext) {
        byte[] reply = null;
        K key = null;
        V value = null;
        boolean hasReply = false;

        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
             ObjectInput objIn = new ObjectInputStream(byteIn);
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
            MapRequestType reqType = (MapRequestType)objIn.readObject();
            switch (reqType) {
                case GET:
                    key = (K)objIn.readObject();
                    value = replicaMap.get(key);
                    if (value != null) {
                        objOut.writeObject(value);
                        hasReply = true;
                    }
                    break;
                case SIZE:
                    int size = replicaMap.size();
                    objOut.writeInt(size);
                    hasReply = true;
                    break;
                case KEYSET:
                    keySet(objOut);
                    hasReply = true;
                    break;
                default:
                    logger.log(Level.WARNING, "in appExecuteUnordered only read operations are supported");
            }
            if (hasReply) {
                objOut.flush();
                byteOut.flush();
                reply = byteOut.toByteArray();
            } else {
                reply = new byte[0];
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Ocurred during map operation execution", e);
        }

        return reply;
    }

    private void keySet(ObjectOutput out) throws IOException, ClassNotFoundException {
        Set<K> keySet = replicaMap.keySet();
        int size = replicaMap.size();
        out.writeInt(size);
        for (K key : keySet)
            out.writeObject(key);
    }
}
