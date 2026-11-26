package com.ksp.mythosequipment;

import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;

/** Immutable local-space triangle mesh, generated once per armor item and LOD. */
final class ArmorPolygon {
    record V(float x,float y,float z){}
    record Face(V a,V b,V c,float nx,float ny,float nz,int material){}
    record Piece(int bone,List<Face> faces){}
    private final List<Piece> pieces=new ArrayList<>();
    private List<Face> faces;
    private void bone(int b){faces=new ArrayList<>();pieces.add(new Piece(b,faces));}
    private void tri(V a,V b,V c,int mat){
        float ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
        float x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx,l=(float)Math.sqrt(x*x+y*y+z*z);
        if(l>1e-6F)faces.add(new Face(a,b,c,x/l,y/l,z/l,mat));
    }
    // Swept elliptical rings: y, center x, center z, radius x, radius z.
    private void hull(int n,int mat,float[][] rings){
        V[][] v=new V[rings.length][n];
        for(int r=0;r<rings.length;r++)for(int k=0;k<n;k++){
            double a=2*Math.PI*k/n;float[] q=rings[r];
            v[r][k]=new V(q[1]+q[3]*(float)Math.cos(a),q[0],q[2]+q[4]*(float)Math.sin(a));
        }
        for(int r=0;r<rings.length-1;r++)for(int k=0;k<n;k++){
            int j=(k+1)%n;tri(v[r][k],v[r+1][k],v[r+1][j],mat);tri(v[r][k],v[r+1][j],v[r][j],mat);
        }
        for(int k=0;k<n;k++){int j=(k+1)%n;float[] a=rings[0],b=rings[rings.length-1];tri(new V(a[1],a[0],a[2]),v[0][k],v[0][j],mat);tri(new V(b[1],b[0],b[2]),v[rings.length-1][j],v[rings.length-1][k],mat);}
    }
    private void visor(int n,int mat,float y,float width,float height,float depth){
        // Curved brow with a real open face beneath it.
        for(int k=0;k<n;k++){
            double a=Math.PI+k*Math.PI/n,b=Math.PI+(k+1)*Math.PI/n;
            V v=new V((float)Math.cos(a)*width,y,(float)Math.sin(a)*depth);
            V w=new V((float)Math.cos(b)*width,y,(float)Math.sin(b)*depth);
            V x=new V(w.x,y+height,w.z),z=new V(v.x,y+height,v.z);
            tri(v,z,x,mat);tri(v,x,w,mat);tri(v,x,z,mat);tri(v,w,x,mat);
        }
    }
    private void gem(int n,int mat,float x,float y,float z,float rx,float h,float rz){
        hull(n,mat,new float[][]{{y,x,z,.03F,.03F},{y+h*.3F,x,z,rx,rz},{y+h*.72F,x,z,rx*.8F,rz*.8F},{y+h,x,z,.03F,.03F}});
    }
    static List<Piece> build(Definition d,int lod){
        ArmorPolygon m=new ArmorPolygon();var p=ArmorDesign.of(d.owner(),d.tier());int t=d.tier().ordinal(),n=lod==0?16:lod==1?10:6;float b=p.bulk();
        switch(d.slot()){
            case HEAD -> {
                m.bone(0);
                // Open front visor: back and side shell, not a closed sphere over the face.
                m.hull(n,0,new float[][]{{-9.5F,0,0,1.7F,1.8F},{-8.6F,0,0,4.2F,4.2F},{-6.8F,0,0,4.4F,4.25F}});
                for(int s:new int[]{-1,1}){
                    m.hull(n,0,new float[][]{{-7,s*3.2F,.5F,1.25F,3.5F},{-4,s*3.5F,.8F,1.1F,3.2F},{-.5F,s*3.4F,1.4F,.65F,2.4F}});
                    if(p.crest()>=2)m.gem(n,2,s*3.4F,-10.2F-t*.35F,0,.7F,3.2F+t*.4F,1.1F);
                }
                m.hull(n,1,new float[][]{{-7,0,3,3.8F,1.2F},{-1,0,3.2F,3.7F,.8F}});
                if(p.crest()==1||p.crest()==3||p.crest()==5)m.gem(n,2,0,-12-t*.35F,0,.65F,3.7F,3.2F);
                switch(d.owner()){
                    case HEAVY_KNIGHT -> {
                        m.visor(n,1,-6.9F,4.6F,1.45F+t*.12F,4.6F);
                        m.hull(4,1,new float[][]{{-2.3F,0,-3.6F,3.5F,.9F},{-.4F,0,-3.3F,3.2F,.75F}});
                        for(int i=0;i<2+t;i++)m.gem(4,2,-3.1F+i*6.2F/(1+t),-9.4F,1.7F,.4F,1.1F,.6F);
                    }
                    case WARRIOR -> m.visor(n,2,-7,4.45F,.65F,4.55F);
                    case RANGER,ROGUE -> {m.visor(n,0,-7.8F,4.65F,1.3F,4.8F); if(d.owner()==ClassSet.ROGUE)m.visor(n,1,-2.8F,3.95F,2.3F,4.3F);}
                    case CLERIC -> {m.visor(n,2,-7.1F,4.5F,.6F,4.5F);m.gem(6,2,0,-11.2F,-2.3F,.7F,2.5F,.8F);}
                    case MAGE,HEXSLINGER -> m.gem(n,3,0,-9.1F,-3.9F,.8F,2.0F,.7F);
                    case NECROMANCER -> {for(int side:new int[]{-1,1})m.gem(6,2,side*2.5F,-4.8F,-3.7F,.75F,3.5F,.65F);}
                    case SUMMONER -> {for(int side:new int[]{-1,1})m.gem(6,3,side*3.9F,-9.0F,-1.5F,.9F,3.1F,.9F);}
                }
                if(p.crest()==4||p.crest()==5)for(int i=0;i<3;i++)m.gem(n,3,(i-1)*2.1F,-10.5F-i*.25F,-2,.6F,2.2F,1);
            }
            case CHEST -> {
                m.bone(1);
                m.hull(n,0,new float[][]{{0,0,0,3.5F,2.5F},{2,0,0,4.35F*b,2.9F*b},{5,0,0,4.4F*b,3.0F*b},{9,0,0,3.6F,2.45F},{11.5F,0,0,3.7F,2.5F}});
                m.hull(n,1,new float[][]{{9.5F,0,0,3.9F,2.7F},{11.3F,0,0,4.0F,2.8F}});
                int rows=2+p.chest()%3;
                for(int i=0;i<rows;i++)m.gem(n,2,0,1.2F+i*2.1F,-2.9F*b,1.0F+p.chest()*.32F,1.7F,.5F);
                switch(d.owner()){
                    case HEAVY_KNIGHT -> {m.hull(8,1,new float[][]{{.5F,0,-2.6F,3.2F,.6F},{3,0,-3.2F,3.8F,.8F},{6.8F,0,-3.0F,2.8F,.7F},{8.7F,0,-2.6F,.25F,.3F}});}
                    case WARRIOR -> {for(int i=0;i<2+t;i++)m.gem(6,2,0,1+i*1.1F,-3.2F,2.1F-i*.14F,1,.4F);}
                    case RANGER,ROGUE -> {for(int i=0;i<4;i++)m.gem(4,1,(i-1.5F)*1.3F,3+i*.9F,-2.8F,1.1F,1.4F,.6F);}
                    case CLERIC -> {m.gem(8,2,0,1.3F,-3.1F,1.8F,4.4F,.8F);m.gem(6,3,0,3,-3.9F,1.0F,1.8F,.35F);}
                    case NECROMANCER -> {for(int i=0;i<4;i++)for(int side:new int[]{-1,1})m.gem(6,2,side*2.2F,1.2F+i*1.9F,-3.1F,1.6F,.8F,.5F);}
                    case MAGE,HEXSLINGER,SUMMONER -> {m.gem(8,3,0,2,-3.5F,1.55F+t*.12F,3.8F,.8F);for(int side:new int[]{-1,1})m.gem(6,2,side*2.5F,2.5F,-2.9F,.65F,3.5F,.5F);}
                }
                for(int s:new int[]{-1,1})m.hull(n,3,new float[][]{{10,s*2.1F,0,1.8F,2.6F},{12+p.length(),s*2.4F,0,1.5F,2.1F},{13+p.length(),s*2.5F,0,.3F,1.5F}});
                for(int s:new int[]{-1,1}){
                    m.bone(s<0?2:3);float x=s*1.0F,w=2.45F*b+p.shoulder()*.19F;
                    m.hull(n,0,new float[][]{{-3-p.shoulder()*.35F,x,0,.5F,.8F},{-1.7F,x,0,w,w*.92F},{1.3F,x+s*.3F,0,w*1.05F,w},{3.7F,x,0,2.1F*b,2.1F*b}});
                    if(d.owner()==ClassSet.HEAVY_KNIGHT){
                        m.hull(8,1,new float[][]{{-3.7F,x,0,w*.7F,w*.7F},{-2.3F,x,0,w*1.12F,w},{-.4F,x,0,w*1.13F,w}});
                        for(int k=0;k<2+t/2;k++)m.gem(4,2,x+s*w*.72F,-4.3F,k*1.15F-1.5F,.45F,2.0F,.45F);
                    }else if(d.owner()==ClassSet.RANGER||d.owner()==ClassSet.ROGUE){
                        m.gem(6,1,x+s*w*.6F,-2.8F,-.7F,.85F,4.2F,1.8F);
                    }else if(d.owner()==ClassSet.CLERIC||d.owner()==ClassSet.SUMMONER){
                        for(int k=0;k<2+t/2;k++)m.gem(6,2,x+s*(w*.4F+k*.18F),-3.2F+k*.5F,k*.6F-1,.5F,3.8F,1.0F);
                    }
                    if(p.shoulder()>=2)m.gem(n,2,x+s*w*.65F,-4.3F-t*.35F,0,.7F,3.2F,1.5F);
                    m.hull(n,3,new float[][]{{3.4F,x,0,2.0F,2.0F},{6.2F,x,0,2.15F,2.1F}});
                    m.hull(n,0,new float[][]{{5.9F,x,0,2.4F*b,2.25F*b},{8,x,0,2.35F*b,2.4F*b},{10,x,0,1.95F,2.1F}});
                    m.gem(n,2,x,6.2F,-2.3F*b,.8F,2.7F,.55F);
                }
            }
            case LEGS,FEET -> {
                for(int bone:new int[]{4,5}){
                    m.bone(bone);
                    if(d.slot()==Slot.LEGS){
                        m.hull(n,0,new float[][]{{0,0,0,2.2F*b,2.25F*b},{3,0,0,2.45F*b,2.4F*b},{5.8F,0,0,2.25F,2.3F},{7.2F,0,0,2.0F,2.0F}});
                        m.gem(n,2,0,3.8F,-2.25F*b,1.1F+p.leg()*.14F,2.7F,1.0F);
                    }else{
                        m.hull(n,0,new float[][]{{6,0,0,2.2F,2.3F},{9,0,-.2F,2.15F*b,2.4F*b},{11.4F,0,-.75F,2.35F*b,3.1F*b},{12,0,-.75F,2.35F*b,3.1F*b}});
                        m.hull(n,1,new float[][]{{11.4F,0,-.75F,2.4F*b,3.15F*b},{12.1F,0,-.75F,2.4F*b,3.15F*b}});
                        m.gem(n,2,0,8.3F,-2.4F*b,.75F+p.leg()*.1F,2.3F,.5F);
                    }
                }
            }
            default -> {}
        }
        return m.pieces.stream().map(q->new Piece(q.bone,List.copyOf(q.faces))).toList();
    }
}
