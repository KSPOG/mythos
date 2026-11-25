package com.npcarchitects;
import java.io.IOException;
final class SchematicLayout {
    private SchematicLayout() {}
    static int[] decode(byte[] bytes,int count) throws IOException {
        int[] result=new int[count]; int cursor=0;
        for(int i=0;i<count;i++) {
            int value=0;
            for(int shift=0;;shift+=7) {
                if(cursor>=bytes.length||shift>28) throw new IOException("Truncated or oversized palette VarInt");
                int b=bytes[cursor++]&255;
                if(shift==28&&(b&0xf8)!=0) throw new IOException("Palette VarInt overflow");
                value|=(b&127)<<shift;
                if((b&128)==0) break;
            }
            result[i]=value;
        }
        if(cursor!=bytes.length) throw new IOException("Unexpected trailing block data");
        return result;
    }
    static int[] rotate(int x,int z,int w,int d,int degrees) {
        return switch(degrees) {
            case 0->new int[]{x,z};case 90->new int[]{d-1-z,x};
            case 180->new int[]{w-1-x,d-1-z};case 270->new int[]{z,w-1-x};
            default->throw new IllegalArgumentException("Invalid rotation");
        };
    }
}
