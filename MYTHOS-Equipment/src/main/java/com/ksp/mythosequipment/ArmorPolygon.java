package com.ksp.mythosequipment;

import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;

/** Immutable local-space triangle mesh, generated once per armor item and LOD. */
final class ArmorPolygon {
    record V(float x,float y,float z){}
    record Face(V a,V b,V c,float nx,float ny,float nz,int material,V na,V nb,V nc,float ua,float va,float ub,float vb,float uc,float vc){}
    record Piece(int bone,List<Face> faces){}
    private final List<Piece> pieces=new ArrayList<>();
    private List<Face> faces;
    private int detail;
    private int tier;
    private ClassSet owner;
    private void bone(int b){faces=new ArrayList<>();pieces.add(new Piece(b,faces));}
    private void tri(V a,V b,V c,int mat){
        float ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
        float x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx,l=(float)Math.sqrt(x*x+y*y+z*z);
        if(l>1e-6F){V n=new V(x/l,y/l,z/l);faces.add(new Face(a,b,c,n.x,n.y,n.z,mat,n,n,n,.1F,.1F,.9F,.1F,.9F,.9F));}
    }
    // Swept elliptical rings: y, center x, center z, radius x, radius z.
    private static V unit(float x,float y,float z){
        float l=(float)Math.sqrt(x*x+y*y+z*z);return l>1e-6F?new V(x/l,y/l,z/l):new V(0,-1,0);
    }
    private void curved(V a,V b,V c,V na,V nb,V nc,int mat,float ua,float va,float ub,float vb,float uc,float vc){
        float ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
        float x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx,l=(float)Math.sqrt(x*x+y*y+z*z);
        if(l>1e-6F)faces.add(new Face(a,b,c,x/l,y/l,z/l,mat,na,nb,nc,ua,va,ub,vb,uc,vc));
    }
    private static float interpolate(float a,float b,float c,float d,float t){
        float m0=(c-a)*.35F,m1=(d-b)*.35F,t2=t*t,t3=t2*t;
        return (2*t3-3*t2+1)*b+(t3-2*t2+t)*m0+(-2*t3+3*t2)*c+(t3-t2)*m1;
    }
    private void hull(int n,int mat,float[][] authored){
        // Refine curved surfaces along their height as well as circumference.
        // Small faceted ornaments intentionally retain hard edges.
        boolean smooth=n>=10;
        int subdivisions=smooth&&detail==0?4:smooth&&detail==1?2:1;
        float[][] rings=new float[(authored.length-1)*subdivisions+1][5];
        for(int r=0;r<rings.length;r++){
            int k=Math.min(r/subdivisions,authored.length-2);float t=(r-k*subdivisions)/(float)subdivisions;
            for(int j=0;j<5;j++){
                float a=authored[Math.max(0,k-1)][j],b=authored[k][j],c=authored[k+1][j],d=authored[Math.min(authored.length-1,k+2)][j];
                rings[r][j]=j==0?b+(c-b)*t:Math.max(j>=3?.015F:-100F,interpolate(a,b,c,d,t));
            }
        }
        V[][] v=new V[rings.length][n],norm=new V[rings.length][n];
        for(int r=0;r<rings.length;r++)for(int k=0;k<n;k++){
            double a=2*Math.PI*k/n;float[] q=rings[r];
            float u=k/(float)n,h=r/(float)(rings.length-1);
            float relief=0;
            if(detail<=1&&mat==0&&n>=24){
                double edge=Math.pow(Math.sin(Math.PI*h),.65);
                double motif=switch(owner){
                    case HEAVY_KNIGHT -> Math.pow(Math.abs(Math.cos(a*4)),8)*.12+Math.pow(Math.abs(Math.sin(h*Math.PI*5)),18)*.075;
                    case WARRIOR -> Math.pow(Math.abs(Math.cos(a*3+h*2)),10)*.10;
                    case RANGER,ROGUE -> Math.sin(a*7+h*9)*Math.sin(h*12)*.045;
                    case MAGE,HEXSLINGER,SUMMONER -> Math.cos(a*6+h*12)*.055;
                    case CLERIC -> Math.pow(Math.abs(Math.cos(a*5)),6)*.085;
                    case NECROMANCER -> Math.pow(Math.abs(Math.sin(a*5)),5)*.12;
                };
                if(tier>=4)motif+=.055*Math.pow(Math.abs(Math.sin(a*12+Math.sin(h*19))),8);
                relief=(float)(motif*edge);
            }
            v[r][k]=new V(q[1]+(q[3]+relief)*(float)Math.cos(a),q[0],q[2]+(q[4]+relief)*(float)Math.sin(a));
        }
        for(int r=0;r<rings.length;r++)for(int k=0;k<n;k++){
            V before=v[Math.max(0,r-1)][k],after=v[Math.min(rings.length-1,r+1)][k];
            V left=v[r][(k+n-1)%n],right=v[r][(k+1)%n];
            float ax=after.x-before.x,ay=after.y-before.y,az=after.z-before.z,bx=right.x-left.x,by=right.y-left.y,bz=right.z-left.z;
            norm[r][k]=unit(ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx);
        }
        for(int r=0;r<rings.length-1;r++)for(int k=0;k<n;k++){
            int j=(k+1)%n;float u=k/(float)n,w=(k+1)/(float)n,y=r/(float)(rings.length-1),z=(r+1)/(float)(rings.length-1);
            if(smooth){
                curved(v[r][k],v[r+1][k],v[r+1][j],norm[r][k],norm[r+1][k],norm[r+1][j],mat,u,y,u,z,w,z);
                curved(v[r][k],v[r+1][j],v[r][j],norm[r][k],norm[r+1][j],norm[r][j],mat,u,y,w,z,w,y);
            }else{tri(v[r][k],v[r+1][k],v[r+1][j],mat);tri(v[r][k],v[r+1][j],v[r][j],mat);}
        }
        for(int k=0;k<n;k++){int j=(k+1)%n;float[] a=rings[0],b=rings[rings.length-1];tri(new V(a[1],a[0],a[2]),v[0][k],v[0][j],mat);tri(new V(b[1],b[0],b[2]),v[rings.length-1][j],v[rings.length-1][k],mat);}
    }
    // Raised decorative metal piping following a sampled elliptical edge.
    private void rim(int n,int mat,float y,float cx,float cz,float rx,float rz,float thickness){
        int sides=detail==0?10:6;
        V[][] ring=new V[n][sides],normal=new V[n][sides];
        for(int i=0;i<n;i++){
            double a=2*Math.PI*i/n;float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
            for(int j=0;j<sides;j++){
                double b=2*Math.PI*j/sides;float cb=(float)Math.cos(b),sb=(float)Math.sin(b);
                normal[i][j]=unit(ca*cb,sb,sa*cb);
                ring[i][j]=new V(cx+(rx+thickness*cb)*ca,y+thickness*sb,cz+(rz+thickness*cb)*sa);
            }
        }
        for(int i=0;i<n;i++)for(int j=0;j<sides;j++){
            int k=(i+1)%n,l=(j+1)%sides;
            float u=i/(float)n,v=j/(float)sides,U=(i+1)/(float)n,Vv=(j+1)/(float)sides;
            curved(ring[i][j],ring[i][l],ring[k][l],normal[i][j],normal[i][l],normal[k][l],mat,u,v,u,Vv,U,Vv);
            curved(ring[i][j],ring[k][l],ring[k][j],normal[i][j],normal[k][l],normal[k][j],mat,u,v,U,Vv,U,v);
        }
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
        ArmorPolygon m=new ArmorPolygon();m.detail=lod;m.owner=d.owner();m.tier=d.tier().ordinal();var p=ArmorDesign.of(d.owner(),d.tier());int t=d.tier().ordinal(),n=lod==0?80:lod==1?28:6;float b=p.bulk();
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
                if(lod==0){m.rim(48,2,-7.8F,0,0,4.32F,4.23F,.10F);m.rim(48,1,-8.65F,0,0,4.13F,4.13F,.12F);}
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
                if(lod==0){
                    m.rim(48,2,10.8F,0,0,4.02F,2.79F,.12F);
                    m.rim(48,1,2.1F,0,0,4.37F*b,2.93F*b,.09F);
                }
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
                    if(lod==0){m.rim(40,2,1.2F,x+s*.3F,0,w*1.05F,w,.10F);m.rim(40,2,8.2F,x,0,2.34F*b,2.39F*b,.10F);}
                }
            }
            case LEGS,FEET -> {
                for(int bone:new int[]{4,5}){
                    m.bone(bone);
                    if(d.slot()==Slot.LEGS){
                        m.hull(n,0,new float[][]{{0,0,0,2.2F*b,2.25F*b},{3,0,0,2.45F*b,2.4F*b},{5.8F,0,0,2.25F,2.3F},{7.2F,0,0,2.0F,2.0F}});
                        m.gem(n,2,0,3.8F,-2.25F*b,1.1F+p.leg()*.14F,2.7F,1.0F);
                        if(lod==0)m.rim(40,2,3,0,0,2.45F*b,2.4F*b,.09F);
                    }else{
                        m.hull(n,0,new float[][]{{6,0,0,2.2F,2.3F},{9,0,-.2F,2.15F*b,2.4F*b},{11.4F,0,-.75F,2.35F*b,3.1F*b},{12,0,-.75F,2.35F*b,3.1F*b}});
                        m.hull(n,1,new float[][]{{11.4F,0,-.75F,2.4F*b,3.15F*b},{12.1F,0,-.75F,2.4F*b,3.15F*b}});
                        m.gem(n,2,0,8.3F,-2.4F*b,.75F+p.leg()*.1F,2.3F,.5F);
                        if(lod==0)m.rim(40,2,11.5F,0,-.75F,2.35F*b,3.1F*b,.10F);
                    }
                }
            }
            default -> {}
        }
        return m.pieces.stream().map(q->new Piece(q.bone,List.copyOf(q.faces))).toList();
    }
}
