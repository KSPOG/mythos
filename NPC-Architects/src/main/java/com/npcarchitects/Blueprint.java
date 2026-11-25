package com.npcarchitects;

import java.util.ArrayList;
import java.util.List;

/** Deterministic bottom-up shell, rotated within a positive-coordinate footprint. */
public final class Blueprint {
    public record SignSide(List<String> lines,String color,boolean glowing) {
        public SignSide {
            if(lines==null||lines.size()!=4||lines.stream().anyMatch(t->t==null||t.length()>384||t.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("Invalid sign lines");
            lines=List.copyOf(lines);
            if(color==null||!List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black").contains(color))throw new IllegalArgumentException("Invalid sign color");
        }
    }
    public record SignData(SignSide front,SignSide back,boolean waxed) {
        public SignData { if(front==null||back==null)throw new IllegalArgumentException("Missing sign side"); }
    }
    public record Cell(int x,int y,int z,String material,SignData sign) {
        public Cell(int x,int y,int z,String material){this(x,y,z,material,null);}
    }
    private Blueprint() {}
    public static List<Cell> house(int width,int depth) { return house(width,depth,5,0); }
    public static List<Cell> house(int width,int depth,int height,int rotation) {
        if(width<5||width>15||depth<5||depth>15||height<4||height>12||rotation<0||rotation>270||rotation%90!=0) throw new IllegalArgumentException("Invalid dimensions or rotation");
        var cells=new ArrayList<Cell>();
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) for(int z=0;z<depth;z++) {
            boolean edge=x==0||x==width-1||z==0||z==depth-1;
            String material;
            if(y==0) material="floor";
            else if(y==height-1) material="roof";
            else if(edge&&!(z==0&&x==width/2&&y<=2)) material=y==2&&((x==0||x==width-1)?z==depth/2:x==width/2)?"glass":"wall";
            else continue;
            int rx=x,rz=z;
            switch(rotation) {
                case 90 -> {rx=depth-1-z;rz=x;}
                case 180 -> {rx=width-1-x;rz=depth-1-z;}
                case 270 -> {rx=z;rz=width-1-x;}
            }
            cells.add(new Cell(rx,y,rz,material));
        }
        return List.copyOf(cells);
    }
}
