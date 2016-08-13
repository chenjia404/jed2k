package org.jed2k;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jed2k.exception.BaseErrorCode;
import org.jed2k.exception.JED2KException;
import org.jed2k.exception.ErrorCode;
import org.jed2k.protocol.Dispatchable;
import org.jed2k.protocol.Dispatcher;
import org.jed2k.protocol.PacketCombiner;
import org.jed2k.protocol.PacketHeader;
import org.jed2k.protocol.Serializable;

public abstract class Connection implements Dispatcher {
    private final Logger log = LoggerFactory.getLogger(ServerConnection.class);
    SocketChannel socket;
    private ByteBuffer bufferIncoming;
    private ByteBuffer bufferOutgoing;
    private LinkedList<Serializable> outgoingOrder = new LinkedList<Serializable>();
    private boolean writeInProgress = false;
    SelectionKey key = null;
    private final PacketCombiner packetCombainer;
    final Session session;
    private PacketHeader header = new PacketHeader();
    private ByteBuffer headerBuffer = ByteBuffer.allocate(PacketHeader.SIZE);
    private Statistics stat = new Statistics();
    long lastReceive = Time.currentTime();

    protected Connection(ByteBuffer bufferIncoming,
            ByteBuffer bufferOutgoing,
            PacketCombiner packetCombiner,
            Session session) throws IOException {
        this.bufferIncoming = bufferIncoming;
        this.bufferOutgoing = bufferOutgoing;
        this.bufferIncoming.order(ByteOrder.LITTLE_ENDIAN);
        this.bufferOutgoing.order(ByteOrder.LITTLE_ENDIAN);
        this.headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        this.packetCombainer = packetCombiner;
        this.session = session;
        socket = SocketChannel.open();
        socket.configureBlocking(false);
        key = socket.register(session.selector, SelectionKey.OP_CONNECT, this);
    }

    protected Connection(ByteBuffer bufferIncoming,
            ByteBuffer bufferOutgoing,
            PacketCombiner packetCombiner,
            Session session, SocketChannel socket) throws IOException {
        this.bufferIncoming = bufferIncoming;
        this.bufferOutgoing = bufferOutgoing;
        this.bufferIncoming.order(ByteOrder.LITTLE_ENDIAN);
        this.bufferOutgoing.order(ByteOrder.LITTLE_ENDIAN);
        this.headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        this.packetCombainer = packetCombiner;
        this.session = session;
        this.socket = socket;
        this.socket.configureBlocking(false);
        key = socket.register(session.selector, SelectionKey.OP_READ, this);
    }

    public void onConnectable() {
        try {
            socket.finishConnect();
            onConnect();
            lastReceive = Time.currentTime();
        } catch(IOException e) {
            log.error(e.getMessage());
            close(ErrorCode.IO_EXCEPTION);
        } catch(JED2KException e) {
            log.error(e.getMessage());
            close(e.getErrorCode());
        }
    }

    protected void processHeader() throws JED2KException {
        assert(!header.isDefined());
        assert(headerBuffer.remaining() != 0);

        int bytes;
        try {
            bytes = socket.read(headerBuffer);
        } catch(IOException e) {
            throw new JED2KException(ErrorCode.IO_EXCEPTION);
        }

        if (bytes == -1) throw new JED2KException(ErrorCode.END_OF_STREAM);

        if (headerBuffer.remaining() == 0) {
            headerBuffer.flip();
            assert(headerBuffer.remaining() == PacketHeader.SIZE);
            header.get(headerBuffer);
            headerBuffer.clear();
            log.debug("processHeader: {}", header.toString());
            // TODO - add adequate resizing algorithm when packet size greater than buffer size
            // but less than available limit for packet
            bufferIncoming.limit(packetCombainer.serviceSize(header));
            stat.receiveBytes(PacketHeader.SIZE, 0);
        }
    }

    protected void processBody() throws JED2KException {
        if(bufferIncoming.remaining() != 0) {
            int bytes;

            try {
                bytes = socket.read(bufferIncoming);
            } catch(IOException e) {
                throw new JED2KException(ErrorCode.IO_EXCEPTION);
            }

            if (bytes == -1) throw new JED2KException(ErrorCode.END_OF_STREAM);
        }

        if (bufferIncoming.remaining() == 0) {
            bufferIncoming.flip();
            stat.receiveBytes(bufferIncoming.remaining(), 0);
            Serializable packet = packetCombainer.unpack(header, bufferIncoming);
            bufferIncoming.clear();
            header.reset();
            if (packet != null && (packet instanceof Dispatchable)) {
                ((Dispatchable)packet).dispatch(this);
            }
        }
    }

    void onReadable() {
        try {
            if (!header.isDefined()) {
                processHeader();
            } else {
                processBody();
            }

            lastReceive = Time.currentTime();
        } catch(JED2KException e) {
            log.error(e.getMessage());
            close(e.getErrorCode());
        }
    }

    void onWriteable() {
        try {
            bufferOutgoing.clear();
            writeInProgress = !outgoingOrder.isEmpty();
            Iterator<Serializable> itr = outgoingOrder.iterator();
            while(itr.hasNext()) {
            	// try to serialize packet into buffer
                if (!packetCombainer.pack(itr.next(), bufferOutgoing)) break;
                itr.remove();
            }

            // if write in progress we have to have at least one packet in outgoing buffer
            // check write not in progress or outgoing buffer position not in begin of buffer
            assert(!writeInProgress || bufferOutgoing.position() != 0);

            if (writeInProgress) {
                bufferOutgoing.flip();
                stat.sendBytes(bufferOutgoing.remaining(), 0);
                socket.write(bufferOutgoing);
            } else {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
        catch(JED2KException e) {
            log.error(e.getMessage());
            close(e.getErrorCode());
        } catch (IOException e) {
            log.error(e.getMessage());
            close(ErrorCode.IO_EXCEPTION);
        }
    }

    void doRead() {
        key.interestOps(SelectionKey.OP_READ);
    }

    protected abstract void onConnect() throws JED2KException;
    protected abstract void onDisconnect(BaseErrorCode ec);

    void connect(final InetSocketAddress address) throws JED2KException {
        try {
            socket.connect(address);
        } catch(IOException e) {
           log.error(e.getMessage());
           close(ErrorCode.IO_EXCEPTION);
        }
    }

    void close(BaseErrorCode ec) {
        log.debug("close socket with code {}", ec);
        try {
            socket.close();
        } catch(IOException e) {
            log.error(e.getMessage());
        } finally {
            onDisconnect(ec);
            key.cancel();
        }
    }

    void write(Serializable packet) {
        log.debug("{} >> ", packet);
        outgoingOrder.add(packet);
        if (!writeInProgress) {
            key.interestOps(SelectionKey.OP_WRITE);
            onWriteable();
        }
    }

    void secondTick(long currentSessionTime) {
        stat.secondTick(currentSessionTime);
    }

    public Statistics statistics() {
        return stat;
    }
}
