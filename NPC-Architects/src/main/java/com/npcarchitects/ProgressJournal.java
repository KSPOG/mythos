package com.npcarchitects;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.zip.CRC32;
/** Single-writer framed append log. Incomplete trailing frames are discarded; corruption fails closed. */
final class ProgressJournal implements AutoCloseable {
    private static final int MAX_FRAME=64*1024*1024;
    private final FileChannel channel;
    ProgressJournal(Path path)throws IOException{
        channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
    }
    void replay(Consumer<String> apply)throws IOException{
        channel.position(0);
        long valid=0;
        ByteBuffer header=ByteBuffer.allocate(8);
        while(channel.position()<channel.size()){
            header.clear();
            if(!readFully(header)){channel.truncate(valid);break;}
            header.flip();int length=header.getInt(),expected=header.getInt();
            if(length<0||length>MAX_FRAME)throw new IOException("Invalid journal frame length");
            ByteBuffer data=ByteBuffer.allocate(length);
            if(!readFully(data)){channel.truncate(valid);break;}
            byte[] bytes=data.array();CRC32 crc=new CRC32();crc.update(bytes);
            if((int)crc.getValue()!=expected)throw new IOException("Journal checksum mismatch");
            apply.accept(new String(bytes,StandardCharsets.UTF_8));valid=channel.position();
        }
        channel.position(channel.size());
    }
    private boolean readFully(ByteBuffer buffer)throws IOException{
        while(buffer.hasRemaining())if(channel.read(buffer)<0)return false;return true;
    }
    void append(String payload)throws IOException{
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_FRAME)throw new IOException("Journal frame too large");
        CRC32 crc=new CRC32();crc.update(bytes);
        ByteBuffer header=ByteBuffer.allocate(8).putInt(bytes.length).putInt((int)crc.getValue());header.flip();
        channel.position(channel.size());while(header.hasRemaining())channel.write(header);
        ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())channel.write(data);
    }
    void force()throws IOException{channel.force(false);}
    void reset()throws IOException{channel.truncate(0);channel.position(0);channel.force(true);}
    public void close()throws IOException{channel.close();}
}
