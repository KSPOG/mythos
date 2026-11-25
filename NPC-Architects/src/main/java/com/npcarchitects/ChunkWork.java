package com.npcarchitects;
final class ChunkWork {
    private ChunkWork(){}
    static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    static int x(long key){return (int)(key>>32);}
    static int z(long key){return (int)key;}
    static int clearIndex(int x,int y,int z,int w,int d,int h,boolean imported){
        int layer=h-1-y;
        if(!imported)return layer*w*d+x*d+z;
        int sx=x/16*16,sz=z/16*16,sw=Math.min(16,w-sx),sd=Math.min(16,d-sz);
        return (int)((long)sx*d*h+(long)sz*sw*h+(long)layer*sw*sd+(x-sx)*sd+z-sz);
    }
}
