package com.npcarchitects;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, bottom-up, shell-only blueprints. */
public final class Blueprint {
    public record Cell(int x, int y, int z, String material) {}
    private Blueprint() {}
    public static List<Cell> house(int width, int depth) {
        if (width < 5 || width > 15 || depth < 5 || depth > 15) throw new IllegalArgumentException("Dimensions must be 5..15");
        var cells = new ArrayList<Cell>();
        for (int y=0; y<=4; y++) for (int x=0; x<width; x++) for (int z=0; z<depth; z++) {
            boolean edge = x==0 || x==width-1 || z==0 || z==depth-1;
            if (y==0) cells.add(new Cell(x,y,z,"floor"));
            else if (y==4) cells.add(new Cell(x,y,z,"roof"));
            else if (edge && !(z==0 && x==width/2 && y<=2)) {
                boolean window = y==2 && ((x==0 || x==width-1) ? z==depth/2 : x==width/2);
                cells.add(new Cell(x,y,z,window ? "glass" : "wall"));
            }
        }
        return List.copyOf(cells);
    }
}
