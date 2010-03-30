package org.shoal.ha.store.impl.command;

import org.shoal.ha.store.api.DataStoreContext;
import org.shoal.ha.store.api.DataStoreException;
import org.shoal.ha.store.impl.util.CommandResponse;
import org.shoal.ha.store.impl.util.ReplicationOutputStream;
import org.shoal.ha.store.impl.util.Utility;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Mahesh Kannan
 * 
 */
public abstract class Command<K, V> {

    private byte opcode;

    private DataStoreContext<K, V> dsc;

    private CommandManager<K, V> cm;

    private String targetName;

    private boolean markedForResponseRequired;

    private CommandResponse cr;

    private long tokenId;

    protected Object result;

    private static final byte[] RESP_NOT_REQUIRED = new byte[] {0};

    private static final byte[] RESP_REQUIRED = new byte[] {1};

    protected Command(byte opcode) {
        this.opcode = opcode;
    }

    public void initialize(DataStoreContext<K, V> rs) {
        this.dsc = rs;
        this.cm = rs.getCommandManager();
    }

    protected DataStoreContext<K, V> getReplicationService() {
        return dsc;
    }

    protected CommandManager<K, V> getCommandManager() {
        return cm;
    }

    public String getTargetName() {
        return targetName;
    }

    public byte getOpcode() {
        return opcode;
    }

    protected void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    protected void mapTarget(K hashKey) {
        setTargetName(dsc.getKeyMapper().getMappedInstance(dsc.getGroupName(), hashKey));
    }

    public final boolean isMarkedForResponseRequired() {
        return markedForResponseRequired;
    }

    protected final void markResponseRequired(Class type) {
        markedForResponseRequired = true;
        cr = dsc.getResponseMediator().createCommandResponse(type);
    }

    protected long getTokenId() {
        return tokenId;
    }

    protected Object getResult(long millis) {
        Object result = null;
        try {
            return cr.getFuture().get(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException inEx) {
            System.out.println("Error: InterruptedException while waiting for result");
        } catch (TimeoutException timeoutEx) {
            System.out.println("Error: Timedout while waiting for result");
        } catch (ExecutionException exeEx) {

        }

        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public void setTokenId(long tokenId) {
        this.tokenId = tokenId;
    }

    public final void writeCommandState(ReplicationOutputStream bos)
        throws IOException {
        try {
            bos.write(new byte[] {getOpcode()});
            bos.write(markedForResponseRequired ? RESP_REQUIRED : RESP_NOT_REQUIRED);
            if (markedForResponseRequired) {
                bos.write(Utility.longToBytes(cr.getTokenId()));
            }
            writeCommandPayload(dsc, bos);
        } catch (IOException ex) {
            //TODO
        }
    }

    final void readCommandState(byte[] data, int offset)
        throws IOException, DataStoreException {
        if (data[offset+1] != 0) {
            markedForResponseRequired = true;
            tokenId = Utility.bytesToLong(data, offset+2);
            offset += 10;
            System.out.println("Just received a command that requires a response for: " + tokenId);
        } else {
            offset += 2;
        }
        readCommandPayload(dsc, data, offset);
    }

    protected abstract Command<K, V> createNewInstance();

    protected abstract void writeCommandPayload(DataStoreContext<K, V> t, ReplicationOutputStream ros)
            throws IOException;

    protected abstract void readCommandPayload(DataStoreContext<K, V> t, byte[] data, int offset)
            throws IOException, DataStoreException;

    protected abstract void execute();

}