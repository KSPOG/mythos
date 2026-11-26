package com.ksp.mythosequipment;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import com.ksp.mythosequipment.EquipmentRules.*;

public final class EquipmentAssets {
    private static final String NS="mythosequipment";
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0],"assets",NS);
        var lang=new TreeMap<String,String>();
        lang.put("itemGroup.mythosequipment.gear","MYTHOS Equipment");
        for(var d:EquipmentRules.CATALOG){
            lang.put("item."+NS+"."+d.id(),d.name());
            Path tex=root.resolve("textures/item/"+d.id()+".png");
            Files.createDirectories(tex.getParent());
            ImageIO.write(d.kind()!=null?atlas(d):icon(d),"PNG",tex.toFile());
            String model=d.kind()!=null?model(d,false):"{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""+NS+":item/"+d.id()+"\"}}";
            write(root.resolve("models/item/"+d.id()+".json"),model);
            if(d.kind()==WeaponKind.BOW||d.kind()==WeaponKind.CROSSBOW||d.kind()==WeaponKind.SHIELD){
                write(root.resolve("models/item/"+d.id()+"_active.json"),model(d,true));
            }
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
    private static Color mix(Color a,Color b,double t){t=Math.max(0,Math.min(1,t));return new Color((int)(a.getRed()*(1-t)+b.getRed()*t),(int)(a.getGreen()*(1-t)+b.getGreen()*t),(int)(a.getBlue()*(1-t)+b.getBlue()*t));}
    private static Color shade(Color c,double t){return t<0?mix(c,Color.BLACK,-t):mix(c,Color.WHITE,t);}
    private static Color accent(Definition d){return shade(new Color(d.owner().color),((d.level()-1)%20)/100.0);}
    private static Color metal(ClassSet c,Tier t){return switch(c){case CLERIC->new Color(205,195,167);case ROGUE,NECROMANCER,HEXSLINGER->new Color(83,94,110);case RANGER->new Color(119,137,118);default->new Color(147,163,181);};}
    private static Color trim(ClassSet c,Tier t){return t==Tier.ASCENDANT?new Color(235,215,151):t==Tier.DRAGON?new Color(201,155,74):t==Tier.CHAMPION?new Color(184,159,113):t==Tier.VETERAN?new Color(168,145,105):new Color(115,125,136);}
    // 256px material atlas: polished steel, edge, trim, leather, cloth, crystal, wood, dark metal.
    private static BufferedImage atlas(Definition d){
        BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        Color[] colors={metal(d.owner(),d.tier()),new Color(210,221,232),trim(d.owner(),d.tier()),new Color(63,40,29),accent(d),shade(accent(d),.32),new Color(93,62,39),new Color(40,47,59)};
        for(int m=0;m<8;m++){
            int ox=(m%4)*64,oy=(m/4)*128;Color base=colors[m];
            for(int y=0;y<128;y++)for(int x=0;x<64;x++){
                double light=.14*Math.cos((x-26)*Math.PI/48)+.055*Math.sin(y*.045);
                if(m==3)light+=((y/9)%2==0?.04:-.07);
                if(m==4)light+=.025*Math.cos(x*1.8)*Math.sin(y*1.8);
                if(m==5)light+=.22*Math.cos((x+y)*.055);
                if(m==6)light+=.045*Math.sin(x*.9+Math.sin(y*.12));
                if(m==0||m==1||m==2||m==7)light+=.035*Math.cos(y*1.1);
                image.setRGB(ox+x,oy+y,shade(base,light).getRGB());
            }
        }
        return image;
    }
    private static final class Mesh {
        final List<String> parts=new ArrayList<>();
        void box(double x,double y,double z,double X,double Y,double Z,int material){box(x,y,z,X,Y,Z,material,null);}
        void box(double x,double y,double z,double X,double Y,double Z,int material,String rotation){
            double u=(material%4)*4+.08,v=(material/4)*8+.08;
            String uv=String.format(Locale.ROOT,"[%.3f,%.3f,%.3f,%.3f]",u,v,u+3.84,v+7.84);
            StringJoiner faces=new StringJoiner(",");
            for(String face:List.of("north","south","east","west","up","down"))faces.add("\""+face+"\":{\"uv\":"+uv+",\"texture\":\"#all\"}");
            parts.add(String.format(Locale.ROOT,"{\"from\":[%.3f,%.3f,%.3f],\"to\":[%.3f,%.3f,%.3f],%s\"faces\":{%s}}",x,y,z,X,Y,Z,rotation==null?"":rotation+",",faces));
        }
        void rod(double y,double Y,double w,double depth,int mat){box(8-w/2,y,8-depth/2,8+w/2,Y,8+depth/2,mat);}
        void bands(double y,double Y,double w,int n){for(int i=0;i<n;i++){double a=y+(Y-y)*i/n;rod(a,a+.16,w,w,2);}}
        void gem(double y,double size){
            // Faceted crystal using nested tapered sections and contrasting edge strips.
            for(int i=0;i<9;i++){double w=size*(.22+.78*Math.sin(Math.PI*(i+.5)/9));double a=y+i*size/9;rod(a,a+size/9,w,w,5);box(8-w/2,a,8-w/2-.05,8-w/2+.10,a+size/9,8+w/2+.05,1);}
        }
        void blade(double start,double end,double width,double depth,int segments,boolean curved){
            for(int i=0;i<segments;i++){
                double t=(double)i/segments;double w=width*(t>.73?Math.max(.08,(1-t)/.27):1-.12*t);
                double x=8+(curved?.9*t*t:0),a=start+(end-start)*t,b=start+(end-start)*(i+1)/segments;
                box(x-w/2,a,8-depth/2,x+w/2,b,8+depth/2,0);
                box(x-w/2-.16,a,7.92,x-w/2+.06,b,8.08,1);
                box(x+w/2-.06,a,7.92,x+w/2+.16,b,8.08,1);
                if(t<.78)box(x-.10,a,8-depth/2-.04,x+.10,b,8-depth/2+.02,7);
            }
        }
        void grip(double start,double end,double width){
            rod(start,end,width,width,3);bands(start+.1,end-.1,width+.1,9);
            rod(start-.35,start,width+.65,width+.65,2);rod(end,end+.25,width+.4,width+.4,2);
        }
        void guard(double y,double width,int tier){
            box(8-width/2,y-.18,7.4,8+width/2,y+.20,8.6,2);
            for(int i=0;i<5;i++){double a=.22*i;double x=width/2-.24*i;box(8-x-.25,y+a,7.45,8-x+.25,y+a+.24,8.55,2);box(8+x-.25,y+a,7.45,8+x+.25,y+a+.24,8.55,2);}
            rod(y-.28,y+.5,1.25,1.25,7);
            if(tier>0)gem(y+.08,.65);
        }
    }
    private static String model(Definition d,boolean active){
        Mesh m=new Mesh();int tier=d.tier().ordinal();int variant=Math.floorMod(d.id().hashCode(),5);
        switch(d.kind()){
            case SWORD,GREATSWORD,SHORTSWORD,DAGGER -> {
                boolean big=d.kind()==WeaponKind.GREATSWORD,dag=d.kind()==WeaponKind.DAGGER,shorter=d.kind()==WeaponKind.SHORTSWORD;
                double start=big?-.5:dag?3.0:1.5,gripEnd=big?4.5:dag?6:4.8,tip=big?22:dag?13:shorter?15:18;
                m.grip(start,gripEnd,big?.95:.65);m.guard(gripEnd,big?6:dag?2.5:4.3,tier);
                m.blade(gripEnd+.4,tip,big?3.2:dag?1.15:2.05+variant*.12,big?.65:.4,big?32:24,dag&&variant%2==0);
                m.gem(start-.7,.72+tier*.12);
            }
            case AXE,GREATAXE -> {
                boolean big=d.kind()==WeaponKind.GREATAXE;double end=big?19:15;
                m.grip(1,6,.85);m.rod(5.8,end,1.1,1.1,6);m.bands(7,end,1.25,8);
                for(int i=0;i<24;i++){double t=(i+.5)/24,y=end-6+i*.25,w=(big?6.4:4.6)*(.48+.52*Math.sin(t*Math.PI));m.box(8.3,y,7.55,8+w,y+.25,8.45,0);m.box(8+w-.19,y,7.85,8+w+.07,y+.25,8.15,1);if(big||variant%2==0){m.box(8-w,y,7.55,7.7,y+.25,8.45,0);m.box(8-w-.07,y,7.85,8-w+.19,y+.25,8.15,1);}}
                m.rod(end-3,end+1,1.65,1.65,2);m.gem(end-2,.9);
            }
            case HAMMER,GREAT_HAMMER -> {
                boolean big=d.kind()==WeaponKind.GREAT_HAMMER;double y=big?18:14,w=big?5.2:3.8;
                m.grip(0,6,.95);m.rod(5.7,y,1.3,1.3,6);m.bands(7,y,1.45,8);
                m.box(8-w,y-2.1,5.65,8+w,y+2.1,10.35,0);
                for(int i=0;i<8;i++){double z=5.65+i*.59;m.box(8-w-.23,y-1.95,z,8-w+.28,y+1.95,z+.48,1);m.box(8+w-.28,y-1.95,z,8+w+.23,y+1.95,z+.48,1);}
                for(int side:new int[]{-1,1}){double x=8+side*(w-.65);m.box(x-.22,y-2.25,5.5,x+.22,y+2.25,10.5,2);}
                m.rod(y-2.4,y+2.4,2.1,2.1,7);m.gem(y+2.35,.7+tier*.3);
            }
            case MACE -> {
                m.grip(1.2,5.7,.8);m.rod(5.5,15.5,1.05,1.05,6);m.bands(6,12,1.18,6);
                for(int i=0;i<16;i++){double a=12+i*.28,w=2.7*Math.sin((i+.5)*Math.PI/16)+.5;m.rod(a,a+.28,w,w,7);m.box(8-w/2-.75,a,7.7,8+w/2+.75,a+.25,8.3,0);m.box(7.7,a,8-w/2-.75,8.3,a+.25,8+w/2+.75,0);}
                m.rod(11.6,12.0,2.1,2.1,2);m.gem(16.5,.85+tier*.2);
            }
            case STAFF,WAND,CASTER -> {
                boolean wand=d.kind()==WeaponKind.WAND,caster=d.kind()==WeaponKind.CASTER;double top=wand?13:caster?11:21;
                m.grip(wand?4:0,wand?7:6,wand?.48:.72);
                m.rod(wand?6.8:5.8,top,wand?.5:.85,wand?.5:.85,6);m.bands(wand?7:7,top,wand?.63:1.0,wand?5:14);
                double size=wand?1.45:2.8+tier*.35;m.gem(top-.35,size);
                for(int i=0;i<14;i++){double a=top-1+i*.19;double w=.65+Math.sin(i*Math.PI/14)*(wand?1.0:1.8);m.box(8-w,a,7.6,8-w+.23,a+.25,8.4,2);m.box(8+w-.23,a,7.6,8+w,a+.25,8.4,2);if(!wand){m.box(7.65,a,8-w,8.35,a+.25,8-w+.22,2);m.box(7.65,a,8+w-.22,8.35,a+.25,8+w,2);}}
                if(caster){m.box(6.8,6,7.25,9.2,12,8.75,7);m.box(7.2,11.5,7.1,8.8,16,8.9,0);m.gem(15.4,1.55);}
            }
            case BOW -> {
                // Bow lies in the XY plane; grip at (8,8,8), string behind grip on +X.
                // Pull moves the string farther behind the grip, never through the riser.
                m.grip(6.2,9.8,.9);
                for(int side:new int[]{-1,1}){
                    for(int i=0;i<30;i++){
                        double t=i/30.0,u=(i+1)/30.0;
                        double x=8+3.1*Math.sin(t*Math.PI/2)-1.2*Math.pow(t,6);
                        double X=8+3.1*Math.sin(u*Math.PI/2)-1.2*Math.pow(u,6);
                        double y=8+side*(1.5+7.8*t),Y=8+side*(1.5+7.8*u);
                        double w=.56-.32*t;
                        m.box(Math.min(x,X)-w/2,Math.min(y,Y),7.5+t*.2,Math.max(x,X)+w/2,Math.max(y,Y)+.035,8.5-t*.2,6);
                        if(i%3==0)m.box(x-.32,y-.09,7.36,x+.32,y+.09,7.58,2);
                    }
                    for(int i=0;i<32;i++){
                        double t=i/32.0,u=(i+1)/32.0;
                        double x=9.9+(active?2.4:0)*t,X=9.9+(active?2.4:0)*u;
                        double y=8+side*9.3*(1-t),Y=8+side*9.3*(1-u);
                        m.box(Math.min(x,X)-.055,Math.min(y,Y)-.02,7.94,Math.max(x,X)+.055,Math.max(y,Y)+.02,8.06,1);
                    }
                }
                m.box(7.3,6,7.35,8.7,6.45,8.65,2);m.box(7.3,9.55,7.35,8.7,10,8.65,2);
            }
            case CROSSBOW -> {
                // Horizontal crossbow: X spans limbs, Z runs along the bolt rail.
                m.box(7,6.9,0,9,8.5,16.5,6);
                m.box(7.35,8.5,2,8.65,9.0,16.5,7);
                m.box(7.65,8.95,1.5,8.35,9.13,16.5,1);
                m.box(7.25,4.6,10.2,8.75,7,12.6,3);
                m.box(7.2,6.2,13.4,8.8,8.7,17,7);
                m.box(6.5,7.2,2.5,9.5,9,4.5,2);
                for(int side:new int[]{-1,1})for(int i=0;i<24;i++){
                    double t=i/24.0,u=(i+1)/24.0;
                    double x=8+side*(1.1+7*t),X=8+side*(1.1+7*u);
                    double z=3+3*Math.pow(t,1.65),Z=3+3*Math.pow(u,1.65);
                    double w=.75-.4*t;
                    m.box(Math.min(x,X),7.5,Math.min(z,Z)-w,Math.max(x,X)+.025,8.5,Math.max(z,Z)+w,6);
                    m.box(Math.min(x,X),8.45,Math.min(z,Z)-w,Math.max(x,X)+.025,8.62,Math.max(z,Z)+w,2);
                }
                for(int side:new int[]{-1,1})for(int i=0;i<32;i++){
                    double t=i/32.0,u=(i+1)/32.0;
                    double x=8+side*8.1*t,X=8+side*8.1*u;
                    double z=(active?12:6)+(6-(active?12:6))*t,Z=(active?12:6)+(6-(active?12:6))*u;
                    m.box(Math.min(x,X)-.05,8.64,Math.min(z,Z)-.05,Math.max(x,X)+.05,8.76,Math.max(z,Z)+.05,1);
                }
                if(active){m.box(7.9,9.14,0,8.1,9.34,12,6);m.box(7.65,9.05,-.8,8.35,9.45,.8,1);}
            }
            case GRIMOIRE -> {
                m.box(3,3,6.5,13,14,9.5,3);
                m.box(3.5,3.5,6.7,12.6,13.5,9.3,1);
                m.box(2.8,2.8,6.1,13.3,14.2,6.8,7);
                m.box(2.8,2.8,9.2,13.3,14.2,9.9,7);
                m.box(2.5,3,6.1,3.6,14,9.9,3);
                for(int i=0;i<12;i++)m.box(12.5,3.7+i*.8,6.8,12.8,3.9+i*.8,9.1,2);
                for(double x:new double[]{3,11.7})for(double y:new double[]{3,12.6})m.box(x,y,5.9,x+1.3,y+1.3,6.3,2);
                m.box(7.7,5,5.8,8.3,12,6.15,2);m.box(5,8.2,5.8,11,8.8,6.15,2);
                for(int i=0;i<12;i++){double a=i*Math.PI/6;double x=8+2.5*Math.cos(a),y=8.5+2.5*Math.sin(a);m.box(x-.25,y-.25,5.65,x+.25,y+.25,6.1,5);}
            }
            case ORB -> {
                m.gem(5,6.5);
                for(int i=0;i<24;i++){double a=i*Math.PI/12;double x=8+3.6*Math.cos(a),z=8+3.6*Math.sin(a);m.box(x-.28,8-.3,z-.28,x+.28,8+.3,z+.28,2);}
                m.rod(3.5,5.2,2.5,2.5,7);
            }
            case LANTERN -> {
                m.rod(3,4,5.4,5.4,2);m.rod(11,12,5.4,5.4,2);m.gem(4,5.8);
                for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})m.box(8+x*2.4-.22,4,8+z*2.4-.22,8+x*2.4+.22,11,8+z*2.4+.22,7);
                m.box(6,12,7.5,6.5,14,8.5,2);m.box(9.5,12,7.5,10,14,8.5,2);m.box(6,13.5,7.5,10,14,8.5,2);
            }
            case TOTEM -> {
                m.rod(2,13,3.2,2.7,6);m.bands(3,12,3.5,6);m.gem(12,2.4);
                for(int i=0;i<6;i++){double y=5+i*.9;double w=3.2-i*.21;m.box(8-w,y,7,8+w,y+.5,9,2);}
                m.box(6,9,6.2,7.4,10.1,7,5);m.box(8.6,9,6.2,10,10.1,7,5);
            }
            case SHIELD -> {
                for(int i=0;i<36;i++){double t=(i+.5)/36,y=0+i*.45;double w=6.4*(t<.27?.18+.82*t/.27:1-.12*Math.max(0,t-.85)/.15);
                    m.box(8-w,y,7.25,8+w,y+.45,8.65,7);m.box(8-w+.32,y,7.0,8+w-.32,y+.45,7.27,4);
                    m.box(8-w-.1,y,6.85,8-w+.34,y+.45,8.75,2);m.box(8+w-.34,y,6.85,8+w+.1,y+.45,8.75,2);
                    if(i%5==0){m.box(8-w+.07,y,6.68,8-w+.24,y+.25,6.88,1);m.box(8+w-.24,y,6.68,8+w-.07,y+.25,6.88,1);}
                }
                m.box(2,16.1,6.9,14,16.5,8.7,2);m.box(7.4,5,8.65,8.6,11,9.35,3);
                for(int i=0;i<16;i++){double t=i/15.0,w=2.6*Math.sin(Math.PI*t)+.12;m.box(8-w,5+i*.37,6.45,8+w,5+i*.37+.38,6.98,2);}
                m.box(7.8,3.5,6.35,8.2,13.5,6.7,1);
            }
        }
        String texture=NS+":item/"+d.id();
        String overrides="";
        if(!active){
            String pred=switch(d.kind()){case SHIELD->"\"blocking\":1";case BOW->"\"pulling\":1";case CROSSBOW->"\"pulling\":1";default->null;};
            if(pred!=null)overrides=",\"overrides\":[{\"predicate\":{"+pred+"},\"model\":\""+NS+":item/"+d.id()+"_active\"}"+(d.kind()==WeaponKind.CROSSBOW?",{\"predicate\":{\"charged\":1},\"model\":\""+NS+":item/"+d.id()+"_active\"}":"")+"]";
        }
        boolean shield=d.kind()==WeaponKind.SHIELD;
        String third=shield?"[0,90,0]":d.kind()==WeaponKind.BOW?"[-80,260,-40]":d.kind()==WeaponKind.CROSSBOW?"[-90,0,0]":"[0,-90,35]";
        String first=shield?(active?"[0,45,10]":"[0,90,0]"):d.kind()==WeaponKind.BOW?"[0,-90,25]":d.kind()==WeaponKind.CROSSBOW?"[0,0,0]":"[0,-90,25]";
        double scale=d.kind().twoHanded?.74:.85;
        String display=String.format(Locale.ROOT,"\"display\":{\"gui\":{\"rotation\":[25,-35,35],\"translation\":[0,-1,0],\"scale\":[0.90,0.90,0.90]},\"thirdperson_righthand\":{\"rotation\":%s,\"translation\":[0,3,1],\"scale\":[%.2f,%.2f,%.2f]},\"thirdperson_lefthand\":{\"rotation\":%s,\"translation\":[0,3,1],\"scale\":[%.2f,%.2f,%.2f]},\"firstperson_righthand\":{\"rotation\":%s,\"translation\":[1,2,1],\"scale\":[%.2f,%.2f,%.2f]},\"firstperson_lefthand\":{\"rotation\":%s,\"translation\":[1,2,1],\"scale\":[%.2f,%.2f,%.2f]},\"ground\":{\"translation\":[0,3,0],\"scale\":[0.35,0.35,0.35]},\"fixed\":{\"scale\":[0.65,0.65,0.65]}}",third,scale,scale,scale,third,scale,scale,scale,first,scale,scale,scale,first,scale,scale,scale);
        return "{\"ambientocclusion\":false,\"textures\":{\"all\":\""+texture+"\",\"particle\":\""+texture+"\"},\"elements\":["+String.join(",",m.parts)+"],"+display+overrides+"}";
    }
    private static BufferedImage icon(Definition d){
        BufferedImage im=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);Graphics2D g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Color c=accent(d),metal=metal(d.owner(),d.tier()),edge=trim(d.owner(),d.tier());
        g.scale(4,4);
        int[][] xs={{5,9,23,27,27,21,21,11,11,5},{3,10,14,18,22,29,27,23,23,9,9,5},{7,25,25,19,16,13,7},{4,13,14,16,16,2}};
        int[][] ys={{12,4,4,12,25,25,16,16,25,25},{7,4,8,8,4,7,17,15,29,29,15,17},{5,5,29,29,16,29,29},{8,8,19,23,29,29}};
        int i=switch(d.slot()){case HEAD->0;case CHEST->1;case LEGS->2;default->3;};
        g.setPaint(new GradientPaint(3,3,shade(metal,.4),26,29,shade(metal,-.45)));g.fillPolygon(xs[i],ys[i],xs[i].length);
        g.setColor(edge);g.setStroke(new BasicStroke(.65f));g.drawPolygon(xs[i],ys[i],xs[i].length);
        g.setColor(c);g.fillRoundRect(12,10,8,10,2,2);g.setColor(edge);g.drawRoundRect(12,10,8,10,2,2);
        if(i==3){g.translate(15,0);g.setPaint(new GradientPaint(3,3,shade(metal,.4),14,29,shade(metal,-.45)));g.fillPolygon(xs[i],ys[i],xs[i].length);g.setColor(edge);g.drawPolygon(xs[i],ys[i],xs[i].length);}
        g.dispose();return im;
    }
    private static BufferedImage armor(ClassSet c,Tier t,int layer){
        // Dedicated 128x64 model UV atlas, eight physical pixels per model texel.
        BufferedImage im=new BufferedImage(1024,512,BufferedImage.TYPE_INT_ARGB);
        Color steel=metal(c,t),gold=trim(c,t),cloth=new Color(c.color);
        boolean light=c==ClassSet.MAGE||c==ClassSet.NECROMANCER||c==ClassSet.RANGER||c==ClassSet.ROGUE||c==ClassSet.HEXSLINGER||c==ClassSet.SUMMONER;
        Color[] materials={light?shade(cloth,-.32):steel,shade(steel,-.34),gold,shade(gold,.17),shade(cloth,.22),shade(cloth,-.44)};
        for(int y=0;y<512;y++)for(int x=0;x<1024;x++){
            int u=x/8,v=y/8,band=Math.min(5,u/16);
            Color base=materials[band];
            double wave=.13*Math.cos(x*.035)+.08*Math.sin(y*.05);
            double grain=.025*Math.sin(x*2.13+y*1.67)+.017*Math.cos(x*.82-y*.93);
            if(band==5)grain+=.06*Math.sin(x*1.6)*Math.cos(y*1.6);
            if(v>=32){
                int px=x%128,py=(y-256)%64;
                if(px<3||px>124||py<3||py>60)wave-=.28;
                if(px==4||px==123||py==4||py==59)wave+=.32;
                if(band==2||band==3)wave+=.09*Math.cos((px+py)*.2);
            }
            im.setRGB(x,y,shade(base,wave+grain).getRGB());
        }
        Graphics2D g=im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int block=0;block<6;block++){
            int x=block*128,y=256;
            g.setColor(shade(gold,-.36));g.setStroke(new BasicStroke(2f));
            for(int k=0;k<4+t.ordinal();k++){
                int yy=y+12+k*21;
                g.drawLine(x+10,yy,x+60,yy+8);g.drawLine(x+60,yy+8,x+117,yy);
            }
            if(t.ordinal()>=2){
                g.setColor(shade(gold,.24));
                for(int k=0;k<4;k++){int xx=x+10+k*34;g.fillOval(xx,y+7,4,4);g.fillOval(xx,y+53,4,4);}
            }
            if(block==4){
                g.setColor(shade(cloth,.45));
                for(int k=0;k<3;k++){int yy=y+24+k*48;g.drawPolygon(new int[]{x+64,x+83,x+64,x+45},new int[]{yy-16,yy,yy+16,yy},4);}
            }
        }
        g.dispose();return im;
    }
    private static void cube(Graphics2D g,int x,int y,int w,int h,int d,Color steel,Color trim,Color cloth,boolean light,boolean head,int tier){
        face(g,x+d,y,w,d,steel,trim,cloth,light,false,tier);face(g,x+d+w,y,w,d,steel,trim,cloth,light,false,tier);
        face(g,x,y+d,d,h,steel,trim,cloth,light,false,tier);
        face(g,x+d,y+d,w,h,steel,trim,cloth,light,true,tier);
        face(g,x+d+w,y+d,d,h,steel,trim,cloth,light,false,tier);
        face(g,x+2*d+w,y+d,w,h,steel,trim,cloth,light,false,tier);
        if(head){var old=g.getComposite();g.setComposite(AlphaComposite.Clear);g.fillRect(x+d+1,y+d+2,w-2,h-3);g.setComposite(old);g.setColor(trim);g.fillRect(x+d,y+d+1,w,1);}
    }
    private static void face(Graphics2D g,int x,int y,int w,int h,Color steel,Color trim,Color cloth,boolean light,boolean front,int tier){
        Graphics2D p=(Graphics2D)g.create();p.clipRect(x,y,w,h);
        Color base=light?shade(cloth,-.38):steel;
        p.setPaint(new GradientPaint(x,y,shade(base,.22),x+w,y+h,shade(base,-.37)));p.fillRect(x,y,w,h);
        p.setColor(shade(base,-.24));p.setStroke(new BasicStroke(.15f));p.drawRect(x,y,w,h);
        p.setColor(trim);p.setStroke(new BasicStroke(.24f));p.drawRect(x+1,y+1,w-2,h-2);
        if(front&&w>=8){p.setColor(shade(cloth,-.1));p.fillPolygon(new int[]{x+1,x+w-1,x+w/2},new int[]{y+2,y+2,y+h-2},3);p.setColor(trim);p.drawPolygon(new int[]{x+1,x+w-1,x+w/2},new int[]{y+2,y+2,y+h-2},3);}
        for(int i=1;i<=tier+1;i++){double yy=y+h*i/(tier+2.0);p.setColor(shade(base,-.22));p.draw(new java.awt.geom.Line2D.Double(x+.25,yy,x+w-.25,yy));p.setColor(shade(base,.28));p.draw(new java.awt.geom.Line2D.Double(x+.25,yy+.18,x+w-.25,yy+.18));}
        p.setColor(trim);for(int a:new int[]{x,x+w-1})for(int b:new int[]{y,y+h-1})p.fill(new java.awt.geom.Ellipse2D.Double(a+.28,b+.28,.42,.42));
        p.dispose();
    }
}
