package com.ksp.mythosequipment;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;
import com.ksp.mythosequipment.EquipmentRules.*;

/** Offline orthographic preview from the exact cached mesh source used in game. */
final class ArmorPreview {
    private record Projected(double x,double y,double z){}
    private record Triangle(Projected a,Projected b,Projected c,int rgb){}
    private static Projected project(ArmorPolygon.V v,int bone){
        float x=v.x(),y=v.y(),z=v.z();
        if(bone==2){x-=5;y+=2;}else if(bone==3){x+=5;y+=2;}
        else if(bone==4){x-=1.9F;y+=12;}else if(bone==5){x+=1.9F;y+=12;}
        double xx=x*.906+z*.423,zz=-x*.423+z*.906;
        return new Projected(xx,y*.985+zz*.174,zz*.985-y*.174);
    }
    static BufferedImage render(List<Definition> defs,int size){
        var triangles=new ArrayList<Triangle>();
        for(var d:defs){
            var p=ArmorDesign.of(d.owner(),d.tier());
            int[] palette={p.primary(),p.secondary(),p.trim(),p.cloth()};
            for(var part:ArmorPolygon.build(d,0))for(var f:part.faces()){
                double nx=(f.na().x()+f.nb().x()+f.nc().x())/3.0,ny=(f.na().y()+f.nb().y()+f.nc().y())/3.0,nz=(f.na().z()+f.nb().z()+f.nc().z())/3.0;
                double lit=Math.max(.27,Math.min(1.2,.72-.3*nz-.2*ny+.16*nx));
                int rgb=palette[f.material()],r=(int)Math.min(255,((rgb>>16)&255)*lit),g=(int)Math.min(255,((rgb>>8)&255)*lit),b=(int)Math.min(255,(rgb&255)*lit);
                triangles.add(new Triangle(project(f.a(),part.bone()),project(f.b(),part.bone()),project(f.c(),part.bone()),(r<<16)|(g<<8)|b));
            }
        }
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
        for(var t:triangles)for(var v:List.of(t.a,t.b,t.c)){minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);}
        double scale=(size-12)/Math.max(maxX-minX,maxY-minY),cx=(minX+maxX)*.5,cy=(minY+maxY)*.5;
        triangles.sort(Comparator.comparingDouble((Triangle t)->(t.a.z+t.b.z+t.c.z)/3).reversed());
        var im=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);var g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(var t:triangles){
            int[] xs=new int[3],ys=new int[3];int k=0;
            for(var v:List.of(t.a,t.b,t.c)){xs[k]=(int)Math.round(size*.5+(v.x-cx)*scale);ys[k]=(int)Math.round(size*.5+(v.y-cy)*scale);k++;}
            g.setColor(new Color(t.rgb));g.fillPolygon(xs,ys,3);
        }
        g.dispose();return im;
    }
    public static void main(String[] args)throws Exception{
        Path dir=Path.of(args[0]);Files.createDirectories(dir);
        int cell=240;
        BufferedImage sheet=new BufferedImage(cell*6,cell*9,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x20242b));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());g.setFont(new Font("SansSerif",Font.BOLD,13));
        for(var c:ClassSet.values())for(var t:Tier.values()){
            var defs=EquipmentRules.CATALOG.stream().filter(d->d.owner()==c&&d.tier()==t&&d.kind()==null).toList();
            var im=render(defs,220);ImageIO.write(im,"PNG",dir.resolve(c.id+"_"+t.level+".png").toFile());
            int x=t.ordinal()*cell,y=c.ordinal()*cell;g.drawImage(im,x+10,y, null);g.setColor(Color.WHITE);g.drawString(EquipmentRules.profile(c,t).name(),x+8,y+231);
        }
        g.dispose();ImageIO.write(sheet,"PNG",dir.resolve("all-54-armor-sets.png").toFile());
    }
}
