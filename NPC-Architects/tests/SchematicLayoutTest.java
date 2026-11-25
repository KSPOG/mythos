package com.npcarchitects;
import java.util.*;
public class SchematicLayoutTest {
    public static void main(String[] args) throws Exception {
        if(!Arrays.equals(SchematicLayout.decode(new byte[]{0,127,(byte)128,1,(byte)255,1},4),new int[]{0,127,128,255})) throw new AssertionError("VarInt decode");
        for(byte[] malformed:new byte[][]{{},{(byte)128},{0,0},{(byte)255,(byte)255,(byte)255,(byte)255,8}}) {
            try { SchematicLayout.decode(malformed,1); throw new AssertionError("Accepted invalid stream"); } catch(java.io.IOException expected) {}
        }
        for(int w=1;w<=64;w++) for(int d=1;d<=64;d++) for(int r:new int[]{0,90,180,270}) {
            int rw=r%180==0?w:d,rd=r%180==0?d:w;
            var seen=new BitSet(rw*rd);
            for(int x=0;x<w;x++) for(int z=0;z<d;z++) {
                int[] p=SchematicLayout.rotate(x,z,w,d,r);
                if(p[0]<0||p[0]>=rw||p[1]<0||p[1]>=rd||seen.get(p[0]*rd+p[1])) throw new AssertionError("Rotation");
                seen.set(p[0]*rd+p[1]);
            }
            if(seen.cardinality()!=w*d) throw new AssertionError("Missing cells");
        }
        System.out.println("Schematic layout and VarInt tests passed");
    }
}
