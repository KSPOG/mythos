package com.ksp.mythosequipment;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;

public final class EquipmentAssets {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0],"assets","mythosequipment");
        var lang=new TreeMap<String,String>();
        lang.put("itemGroup.mythosequipment.gear","MYTHOS Equipment");
        for(var d:EquipmentRules.CATALOG){
            lang.put("item.mythosequipment."+d.id(),d.name());
            Path tex=root.resolve("textures/item/"+d.id()+".png");
            Files.createDirectories(tex.getParent());ImageIO.write(icon(d),"PNG",tex.toFile());
            write(root.resolve("models/item/"+d.id()+".json"),"{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mythosequipment:item/"+d.id()+"\"}}");
        }
        for(var c:ClassSet.values())for(var t:Tier.values()){
            String name=t.name().toLowerCase(Locale.ROOT)+"_"+c.id;
            for(int layer=1;layer<=2;layer++){
                Path tex=root.resolve("textures/models/armor/"+name+"_layer_"+layer+".png");
                Files.createDirectories(tex.getParent());ImageIO.write(armor(c,t,layer),"PNG",tex.toFile());
            }
        }
        StringJoiner json=new StringJoiner(",\n","{\n","\n}");
        lang.forEach((k,v)->json.add("\""+k+"\":\""+v+"\""));write(root.resolve("lang/en_us.json"),json.toString());
    }
    private static void write(Path path,String value)throws Exception{Files.createDirectories(path.getParent());Files.writeString(path,value);}
    private static Color color(ClassSet c,Tier t){Color b=new Color(c.color);return t==Tier.DRAGON?b.brighter():t==Tier.VETERAN?b:b.darker();}
    private static void shape(Graphics2D g,Color c,int[] xs,int[] ys){g.setColor(c.darker().darker());g.fillPolygon(xs,ys,xs.length);g.setColor(c);g.translate(1,1);g.fillPolygon(xs,ys,xs.length);g.translate(-1,-1);}
    private static BufferedImage icon(Definition d){
        BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();Color c=color(d.owner(),d.tier());
        Color metal=d.tier()==Tier.DRAGON?new Color(235,193,92):new Color(190,200,215);
        switch(d.slot()){
            case WEAPON -> {
                switch(d.owner()){
                    case RANGER -> {g.setColor(c);g.setStroke(new BasicStroke(4));g.drawArc(4,3,23,25,-90,180);g.setColor(metal);g.setStroke(new BasicStroke(1));g.drawLine(15,3,15,28);g.drawLine(4,24,28,8);}
                    case HEAVY_KNIGHT -> {g.setColor(new Color(80,48,35));g.fillRect(14,12,5,17);shape(g,c,new int[]{4,27,27,4},new int[]{4,4,13,13});g.setColor(metal);g.fillRect(6,5,19,3);}
                    case MAGE,CLERIC,NECROMANCER,HEXSLINGER -> {g.setColor(new Color(76,53,48));g.setStroke(new BasicStroke(4));g.drawLine(5,28,23,9);shape(g,c,new int[]{16,22,29,27,20},new int[]{4,0,6,13,15});g.setColor(metal);g.fillOval(20,4,5,5);}
                    default -> {shape(g,metal,new int[]{8,23,29,25,10},new int[]{20,3,2,9,24});g.setColor(c);g.fillRect(7,20,12,4);g.setColor(new Color(84,54,41));g.fillRect(8,24,4,6);}
                }
            }
            case HEAD -> {shape(g,c,new int[]{5,9,22,27,27,21,21,11,11,5},new int[]{12,5,5,12,25,25,17,17,25,25});g.setColor(metal);g.fillRect(10,9,12,3);}
            case CHEST -> {shape(g,c,new int[]{3,10,13,19,22,29,27,23,23,9,9,5},new int[]{7,4,9,9,4,7,17,15,29,29,15,17});g.setColor(metal);g.fillRect(12,13,8,9);}
            case LEGS -> {shape(g,c,new int[]{7,25,25,19,16,13,7},new int[]{5,5,28,28,16,28,28});g.setColor(metal);g.fillRect(8,8,16,3);}
            case FEET -> {shape(g,c,new int[]{5,13,13,15,15,3},new int[]{9,9,20,23,28,28});shape(g,c,new int[]{19,27,27,29,29,17},new int[]{9,9,20,23,28,28});g.setColor(metal);g.fillRect(6,10,6,3);g.fillRect(20,10,6,3);}
        }
        g.dispose();return image;
    }
    private static BufferedImage armor(ClassSet c,Tier t,int layer){
        BufferedImage image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();Color base=color(c,t),edge=t==Tier.DRAGON?new Color(210,157,65):base.brighter();
        // Standard humanoid armor UV islands; transparent face and unused space.
        int[][] regions=layer==1?new int[][]{{8,0,16,8},{0,8,8,8},{16,8,16,8},{16,16,24,16},{40,16,16,16},{0,16,16,16}}:new int[][]{{16,16,24,16},{0,16,16,16}};
        for(int[] r:regions){g.setColor(base);g.fillRect(r[0],r[1],r[2],r[3]);g.setColor(edge);g.drawRect(r[0],r[1],r[2]-1,r[3]-1);g.setColor(base.darker());for(int y=r[1]+3;y<r[1]+r[3]-1;y+=4)g.drawLine(r[0]+1,y,r[0]+r[2]-2,y);}
        g.dispose();return image;
    }
}
