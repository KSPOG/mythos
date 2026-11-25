package com.npcarchitects;

import java.util.Set;

/** Stable preferred work zones; coordinates are relative to the rotated footprint. */
final class WorkZones {
    static final Set<String> MODES=Set.of("adaptive","quadrants","strips","chunks");
    private WorkZones(){}
    static int zone(String mode,int x,int z,int width,int depth,int count){
        if(count<=1)return 0;
        x=Math.max(0,Math.min(width-1,x));z=Math.max(0,Math.min(depth-1,z));
        if("strips".equals(mode))return width>=depth?(int)((long)x*count/width):(int)((long)z*count/depth);
        if("chunks".equals(mode)){
            long cols=(width+15L)/16,rows=(depth+15L)/16;
            return (int)(((x/16)*rows+z/16)%count);
        }
        // Aspect-aware rectangular grid, or equal-area recursive bisection.
        if("quadrants".equals(mode)){
            int columns=Math.max(1,Math.min(count,(int)Math.ceil(Math.sqrt(count*width/(double)depth))));
            int rowCount=(count+columns-1)/columns;
            int row=(int)((long)z*rowCount/depth);
            int n=Math.min(columns,count-row*columns);
            return row*columns+(int)((long)x*n/width);
        }
        int first=0,n=count;
        while(n>1){
            int left=n/2;
            boolean splitX=width>=depth;
            if(width==1&&depth==1)return first;
            if(splitX&&width==1)splitX=false;
            if(!splitX&&depth==1)splitX=true;
            if(splitX){
                int cut=Math.max(1,Math.min(width-1,(int)((long)width*left/n)));
                if(x<cut){width=cut;n=left;}else{x-=cut;width-=cut;first+=left;n-=left;}
            }else{
                int cut=Math.max(1,Math.min(depth-1,(int)((long)depth*left/n)));
                if(z<cut){depth=cut;n=left;}else{z-=cut;depth-=cut;first+=left;n-=left;}
            }
        }
        return first;
    }
}
