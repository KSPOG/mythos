package com.npcarchitects;
import java.util.BitSet;
public final class ChunkWorkTest {
    public static void main(String[] args){
        for(int w:new int[]{1,7,16,17,31,32,65,320})for(int d:new int[]{1,9,16,19,33,320})for(int h:new int[]{1,3,7})for(boolean imported:new boolean[]{false,true}){
            BitSet seen=new BitSet(w*d*h);
            for(int x=0;x<w;x++)for(int z=0;z<d;z++)for(int y=h-1;y>=0;y--){
                int i=ChunkWork.clearIndex(x,y,z,w,d,h,imported);
                if(i<0||i>=w*d*h||seen.get(i))throw new AssertionError("Invalid index "+i);
                seen.set(i);
                if(y<h-1&&ChunkWork.clearIndex(x,y+1,z,w,d,h,imported)>=i)throw new AssertionError("Top-down order");
                if(imported){
                    long span=16L*d*h;int sx=(int)(i/span)*16,sw=Math.min(16,w-sx);
                    int local=(int)(i-(sx/16)*span);long rowSpan=16L*sw*h;
                    int sz=(int)(local/rowSpan)*16,sd=Math.min(16,d-sz),within=(int)(local-(sz/16)*rowSpan);
                    if(sx+(within%(sw*sd))/sd!=x||sz+(within%(sw*sd))%sd!=z||h-1-within/(sw*sd)!=y)throw new AssertionError("Legacy mapping mismatch");
                }
            }
            if(seen.cardinality()!=w*d*h)throw new AssertionError("Incomplete");
        }
        for(int x:new int[]{-100,-1,0,1,100})for(int z:new int[]{-100,-1,0,1,100}){long k=ChunkWork.key(x,z);if(ChunkWork.x(k)!=x||ChunkWork.z(k)!=z)throw new AssertionError("Chunk key");}
        System.out.println("ChunkWork legacy index, column order and signed chunk keys passed");
    }
}
