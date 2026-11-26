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
    // A fitted open plate follows a superellipse; the front arc is centered at -Z.
    // The inner lip is real geometry, so plates have thickness without solid inflated limbs.
    private void plate(int n,int mat,float cx,float cz,float top,float bottom,float wt,float wb,float dt,float db,float arc,float point,float thickness,float shape){
        int segments=Math.max(detail==2?4:6,(int)(n*arc/3.141593F)), rows=detail==0?16:detail==1?6:2;
        V[][] outer=new V[rows+1][segments+1],inner=new V[rows+1][segments+1],norm=new V[rows+1][segments+1];
        for(int r=0;r<=rows;r++)for(int k=0;k<=segments;k++){
            float v=r/(float)rows,u=k/(float)segments;
            double a=(u*2-1)*arc;
            float sn=(float)Math.sin(a),cs=(float)Math.cos(a);
            float sx=Math.copySign((float)Math.pow(Math.abs(sn),shape),sn),sz=Math.copySign((float)Math.pow(Math.abs(cs),shape),cs);
            float w=wt+(wb-wt)*v,d=dt+(db-dt)*v;
            float ridge=(float)Math.pow(Math.max(0,cs),8)*.15F*(float)Math.sin(Math.PI*v);
            float y=top+(bottom-top)*v+point*(float)Math.pow(Math.max(0,cs),4)*v*v;
            outer[r][k]=new V(cx+sx*w,y,cz-sz*d-ridge);
            inner[r][k]=new V(cx+sx*(w-thickness),y,cz-sz*(d-thickness)-ridge);
        }
        for(int r=0;r<=rows;r++)for(int k=0;k<=segments;k++){
            V a=outer[Math.max(0,r-1)][k],b=outer[Math.min(rows,r+1)][k],c=outer[r][Math.max(0,k-1)],d=outer[r][Math.min(segments,k+1)];
            float ax=b.x-a.x,ay=b.y-a.y,az=b.z-a.z,bx=d.x-c.x,by=d.y-c.y,bz=d.z-c.z;
            norm[r][k]=unit(ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx);
        }
        for(int r=0;r<rows;r++)for(int k=0;k<segments;k++){
            float u=k/(float)segments,U=(k+1)/(float)segments,v=r/(float)rows,Vv=(r+1)/(float)rows;
            int material=(r==0||r==rows-1||k==0||k==segments-1)&&detail<2?2:mat;
            curved(outer[r][k],outer[r+1][k],outer[r+1][k+1],norm[r][k],norm[r+1][k],norm[r+1][k+1],material,u,v,u,Vv,U,Vv);
            curved(outer[r][k],outer[r+1][k+1],outer[r][k+1],norm[r][k],norm[r+1][k+1],norm[r][k+1],material,u,v,U,Vv,U,v);
        }
        for(int k=0;k<segments;k++){
            for(int r:new int[]{0,rows}){
                tri(outer[r][k],inner[r][k],inner[r][k+1],2);tri(outer[r][k],inner[r][k+1],outer[r][k+1],2);
                tri(outer[r][k],inner[r][k+1],inner[r][k],2);tri(outer[r][k],outer[r][k+1],inner[r][k+1],2);
            }
        }
        for(int r=0;r<rows;r++)for(int k:new int[]{0,segments}){
            tri(outer[r][k],outer[r+1][k],inner[r+1][k],2);tri(outer[r][k],inner[r+1][k],inner[r][k],2);
            tri(outer[r][k],inner[r+1][k],outer[r+1][k],2);tri(outer[r][k],inner[r][k],inner[r+1][k],2);
        }
    }
    /** Tapered swept horn, with a curved spine and cross sections perpendicular to its tangent. */
    private void horn(int mat,float x,float y,float z,float dx,float height,float dz,float radius){
        int steps=detail==0?28:detail==1?10:4, sides=detail==0?16:detail==1?8:4;
        V[][] v=new V[steps+1][sides],norm=new V[steps+1][sides];
        for(int i=0;i<=steps;i++){
            float t=i/(float)steps;
            V tangent=unit(dx*(.35F+1.3F*t),-height*(1.35F-.7F*t),dz*(.4F+1.2F*t));
            V a=unit(-tangent.y,tangent.x,0);
            V b=unit(tangent.y*a.z-tangent.z*a.y,tangent.z*a.x-tangent.x*a.z,tangent.x*a.y-tangent.y*a.x);
            float cx=x+dx*(.35F*t+.65F*t*t),cy=y-height*(1.35F*t-.35F*t*t),cz=z+dz*(.4F*t+.6F*t*t);
            float rad=Math.max(.012F,radius*(float)Math.pow(1-t,.82)* (1+.055F*(float)Math.cos(t*42)));
            for(int k=0;k<sides;k++){
                double angle=2*Math.PI*k/sides;
                float c=(float)Math.cos(angle),sn=(float)Math.sin(angle);
                V normal=unit(a.x*c+b.x*sn,a.y*c+b.y*sn,a.z*c+b.z*sn);
                norm[i][k]=normal;
                v[i][k]=new V(cx+normal.x*rad,cy+normal.y*rad,cz+normal.z*rad);
            }
        }
        for(int i=0;i<steps;i++)for(int k=0;k<sides;k++){
            int j=(k+1)%sides;float u=k/(float)sides,U=(k+1)/(float)sides,w=i/(float)steps,W=(i+1)/(float)steps;
            int material=i==0&&detail<2?2:mat;
            curved(v[i][k],v[i][j],v[i+1][j],norm[i][k],norm[i][j],norm[i+1][j],material,u,w,U,w,U,W);
            curved(v[i][k],v[i+1][j],v[i+1][k],norm[i][k],norm[i+1][j],norm[i+1][k],material,u,w,U,W,u,W);
        }
        for(int k=1;k<sides-1;k++)tri(v[steps][0],v[steps][k],v[steps][k+1],mat);
    }
    /** Raised leaf, feather or blade with a curved silhouette, central ridge and bevel. */
    private void blade(int mat,float x,float y,float z,float dx,float dy,float dz,float width){
        int steps=detail==0?14:detail==1?6:3;
        V[][] v=new V[steps+1][3];
        float l=(float)Math.hypot(dx,dy);float px=-dy/Math.max(.01F,l),py=dx/Math.max(.01F,l);
        for(int i=0;i<=steps;i++){
            float t=i/(float)steps,ww=Math.max(.012F,width*(float)Math.sin(Math.PI*t));
            float cx=x+dx*t,cy=y+dy*t,cz=z+dz*t;
            v[i][0]=new V(cx-px*ww,cy-py*ww,cz);
            v[i][1]=new V(cx,cy,cz-.18F*(float)Math.sin(Math.PI*t));
            v[i][2]=new V(cx+px*ww,cy+py*ww,cz);
        }
        for(int i=0;i<steps;i++)for(int k=0;k<2;k++){
            V a=v[i][k],b=v[i+1][k],c=v[i+1][k+1],d=v[i][k+1];
            tri(a,b,c,mat);tri(a,c,d,mat);tri(a,c,b,mat);tri(a,d,c,mat);
        }
    }
    private void ornaments(int side,float x,float w,int t){
        switch(owner){
            case WARRIOR -> {
                float root=x+side*w*.57F;
                // Root intersects the upper pauldron, tip sweeps outward and upward.
                horn(t>=3?2:1,root,-1.85F,-w*.52F,side*(1.35F+t*.23F),1.9F+t*.38F,-.45F-t*.09F,.46F+t*.055F);
                if(t>=2)horn(2,root,-1.55F,w*.38F,side*(.9F+t*.17F),1.35F+t*.26F,.48F,.34F+t*.035F);
                if(t>=4)blade(2,x+side*w*.55F,.0F,-w*.73F,side*1.6F,-1.8F,-.2F,.42F);
            }
            case HEAVY_KNIGHT -> {
                for(int k=0;k<2+t/2;k++){
                    float z=-w*.58F+k*w*1.15F/(1+t/2);
                    if(detail<2||k==0)blade(0,x+side*w*.60F,-1.8F,z,side*.45F,-1.15F-t*.17F,0,.46F);
                }
                blade(2,x+side*w*.60F,.1F,-w*.76F,side*.80F,1.6F,-.1F,.43F);
            }
            case RANGER -> {
                for(int k=0;k<2+t/2;k++)blade(k%2==0?0:3,x+side*w*.25F,-1.75F+k*.35F,-w*.72F,side*(1.65F+k*.15F),1.65F+k*.25F,-.12F,.42F);
            }
            case ROGUE -> {
                blade(1,x+side*w*.40F,-1.8F,-w*.68F,side*(1.6F+t*.17F),1.1F,-.2F,.46F);
                if(t>=3)blade(2,x+side*w*.30F,-.8F,-w*.77F,side*1.5F,1.6F,-.15F,.26F);
            }
            case MAGE -> {
                gem(detail==0?16:6,2,x+side*w*.45F,-3.2F-t*.25F,-w*.54F,.46F+t*.05F,1.85F+t*.25F,.48F);
                if(t>=2)blade(2,x+side*w*.20F,-1.7F,-w*.76F,side*1.1F,2.2F,-.2F,.27F);
            }
            case CLERIC -> {
                for(int k=0;k<3+t/2;k++)blade(k%2==0?2:0,x+side*w*.30F,-1.7F+k*.28F,-w*.75F,side*(1.4F+k*.16F),1.8F+k*.2F,-.12F,.30F);
            }
            case NECROMANCER -> {
                horn(2,x+side*w*.55F,-1.7F,-w*.48F,side*(1.2F+t*.18F),1.5F+t*.3F,-.55F,.38F+t*.04F);
                if(t>=2)horn(2,x+side*w*.6F,-.8F,w*.2F,side*.9F,1.2F+t*.18F,.5F,.28F);
            }
            case HEXSLINGER -> {
                for(int k=0;k<2+t/2;k++)blade(k==0?2:1,x+side*w*.4F,-1.6F+k*.5F,-w*.73F,side*(1.2F+t*.12F),-.9F+k*.65F,-.25F,.40F);
            }
            case SUMMONER -> {
                for(int k=0;k<2+t/2;k++)gem(detail==0?12:6,k%2==0?2:3,x+side*(w*.4F+k*.25F),-3.0F-k*.18F,-w*.6F+k*.45F,.30F,1.8F+t*.24F,.35F);
            }
        }
    }
    private void stud(float x,float y,float z,float size){
        gem(detail==0?12:6,2,x,y-size*.5F,z,size,size,size*.6F);
    }
    static List<Piece> build(Definition d,int lod){
        ArmorPolygon m=new ArmorPolygon();m.detail=lod;m.owner=d.owner();m.tier=d.tier().ordinal();
        var p=ArmorDesign.of(d.owner(),d.tier());int t=d.tier().ordinal(),n=lod==0?64:lod==1?20:6;
        boolean tank=d.owner()==ClassSet.HEAVY_KNIGHT,steel=tank||d.owner()==ClassSet.WARRIOR||d.owner()==ClassSet.CLERIC;
        boolean cloth=d.owner()==ClassSet.MAGE||d.owner()==ClassSet.NECROMANCER||d.owner()==ClassSet.SUMMONER||d.owner()==ClassSet.HEXSLINGER;
        float shape=steel?.72F:.53F, extra=tank?.55F:steel?.24F:.10F;
        float shoulder=tank?3.05F:steel?2.60F:2.22F;
        switch(d.slot()){
            case HEAD -> {
                m.bone(0);
                // A shallow fitted crown and an open-face shell around the actual Minecraft head.
                m.hull(n,steel?0:3,new float[][]{{-9.0F,0,0,2.7F,2.7F},{-8.6F,0,0,4.1F,4.1F},{-7.6F,0,0,4.35F,4.3F}});
                for(int side:new int[]{-1,1}){
                    m.plate(n,steel?0:3,side*2.9F,.6F,-7.6F,-.8F,1.55F,1.25F,3.9F,3.15F,1.52F,.2F,.17F,shape);
                    if(t>=4&&steel)m.gem(12,2,side*3.7F,-10.9F,1.3F,.65F,2.9F,.65F);
                }
                m.hull(n,steel?1:3,new float[][]{{-7.8F,0,3.3F,3.95F,1.0F},{-4.0F,0,3.55F,4.0F,.85F},{-.8F,0,3.4F,3.8F,.6F}});
                switch(d.owner()){
                    case HEAVY_KNIGHT -> {
                        m.plate(n,0,0,0,-7.6F,-5.75F,4.45F,4.45F,4.55F,4.55F,1.56F,0,.24F,.40F);
                        m.plate(n,0,0,0,-4.5F,-.7F,4.44F,3.8F,4.55F,4.0F,1.48F,.2F,.24F,.40F);
                        m.gem(10,2,0,-6.0F,-4.55F,.22F,5.2F,.25F);
                    }
                    case WARRIOR -> {m.visor(n,2,-7.5F,4.45F,.68F,4.45F);m.gem(12,0,0,-7.2F,-4.3F,.32F,3.7F,.28F);}
                    case RANGER -> m.plate(n,3,0,0,-8.8F,-6.6F,4.4F,4.6F,4.4F,4.75F,1.55F,.1F,.12F,.7F);
                    case ROGUE -> {m.visor(n,3,-7.7F,4.45F,.65F,4.5F);m.plate(n,3,0,0,-3.6F,-.8F,4.15F,3.8F,4.3F,4.1F,1.45F,0,.1F,.6F);}
                    case CLERIC -> {m.visor(n,2,-7.4F,4.45F,.75F,4.5F);m.gem(12,2,0,-10.5F,-3.8F,.5F,3.4F,.45F);}
                    case MAGE -> {m.visor(n,2,-7.6F,4.4F,.35F,4.45F);m.gem(12,2,0,-11.0F,-3.7F,.75F,3.1F,.5F);}
                    case NECROMANCER -> {for(int side:new int[]{-1,1})m.gem(12,2,side*3.1F,-4.4F,-3.5F,.65F,3.5F,.55F);}
                    case HEXSLINGER -> {m.visor(n,1,-7.7F,4.45F,.7F,4.5F);m.gem(8,2,0,-8.1F,-4.4F,.65F,1.8F,.4F);}
                    case SUMMONER -> {m.visor(n,2,-7.5F,4.4F,.45F,4.4F);for(int side:new int[]{-1,1})m.gem(10,2,side*3.5F,-9.7F,0,.55F,2.3F,.65F);}
                }
                if(t>=2){
                    int crowns=t>=4?3:1;
                    for(int k=0;k<crowns;k++)m.gem(12,2,(k-(crowns-1)*.5F)*2.15F,-9.7F-t*.18F,.1F,.42F,1.5F+t*.18F,.55F);
                }
            }
            case CHEST -> {
                m.bone(1);
                // Fitted gambeson follows the rectangular torso rather than a barrel-shaped belly.
                m.plate(n,3,0,0,0,11.6F,4.22F,4.18F,2.22F,2.23F,3.14159F,0,.10F,.42F);
                if(steel){
                    m.plate(n,0,0,0,.25F,6.7F,4.15F+extra,3.9F+extra*.4F,2.45F+extra,2.62F+extra,1.63F,.7F,.20F,shape);
                    m.hull(n,1,new float[][]{{.4F,0,1.95F,3.9F,.65F},{5,0,2.0F,4.1F,.6F},{8,0,1.95F,3.9F,.55F}});
                    int lames=3+t/2;
                    for(int k=0;k<lames;k++){
                        float y=6.4F+k*4.3F/lames;
                        m.plate(n,0,0,0,y,y+1.45F,4.03F+extra*.3F,3.95F+extra*.3F,2.54F+extra*.3F,2.70F+extra*.3F,1.77F,.22F,.13F,shape);
                    }
                }else if(cloth){
                    for(int side:new int[]{-1,1})m.plate(n,0,side*1.95F,0,.4F,10.1F,2.1F,1.8F,2.42F,2.38F,1.55F,1.5F,.12F,.52F);
                    m.plate(n,3,0,0,9.4F,13.4F+p.length(),4.3F,4.9F,2.48F,2.85F,2.45F,1.0F,.10F,.55F);
                }else{
                    m.plate(n,0,0,0,.7F,7.3F,4.26F,3.85F,2.42F,2.48F,1.67F,.45F,.13F,.42F);
                    for(int i=0;i<3;i++)m.plate(n,1,0,0,6.9F+i*1.35F,8.1F+i*1.35F,4.16F,4.1F,2.45F,2.5F,1.75F,.2F,.10F,.42F);
                }
                m.plate(n,1,0,0,10.1F,11.25F,4.3F,4.3F,2.65F,2.65F,3.14159F,0,.15F,.42F);
                // One recognizable heraldic motif instead of identical rows of gems.
                switch(d.owner()){
                    case HEAVY_KNIGHT -> m.plate(n,2,0,-2.5F,1.4F,5.7F,1.75F,.4F,.6F,.5F,1.5F,.75F,.12F,.45F);
                    case WARRIOR -> {m.gem(8,2,0,1.5F,-2.95F-extra,.30F,4.8F,.25F);m.gem(8,2,0,2.8F,-3.03F-extra,1.55F,.75F,.22F);}
                    case RANGER,ROGUE -> {for(int i=0;i<3;i++)m.stud(-2.5F+i*1.6F,3+i*1.4F,-2.65F,.23F);}
                    case CLERIC -> {m.gem(16,2,0,1.9F,-3.0F,1.1F,2.5F,.55F);m.gem(10,2,0,1.0F,-3.0F,.20F,4.7F,.20F);}
                    case NECROMANCER -> {for(int i=0;i<4;i++)for(int side:new int[]{-1,1})m.plate(12,2,side*1.6F,-2.35F,1.5F+i*1.65F,2.0F+i*1.65F,1.55F,1.0F,.45F,.38F,1.35F,.2F,.08F,.75F);}
                    case MAGE,HEXSLINGER,SUMMONER -> m.gem(20,2,0,2.0F,-2.75F,.85F,2.8F,.50F);
                }
                for(int side:new int[]{-1,1}){
                    m.bone(side<0?2:3);float x=side*1.0F;
                    m.plate(n,3,x,0,-1.8F,9.9F,2.10F,2.10F,2.10F,2.10F,3.14159F,0,.10F,.44F);
                    float w=shoulder+t*(tank?.095F:.045F);
                    int layers=tank?3+t/2:steel?2+t/3:1;
                    for(int k=0;k<layers;k++){
                        float y=-2.15F+k*.92F;
                        m.plate(n,steel?0:cloth?3:0,x,0,y,y+1.7F,w-k*.15F,w-k*.12F,w*.82F-k*.10F,w*.80F-k*.08F,1.9F,.3F,.16F,shape);
                    }
                    m.ornaments(side,x,w,t);
                    // Backward-facing arm armor uses the same fitted underlayer; leave the elbow flexible.
                    m.plate(n,steel?0:1,x,0,5.0F,9.5F,2.29F+extra*.28F,2.13F,2.28F+extra*.25F,2.18F,1.7F,.30F,.15F,.48F);
                    if(lod<2){m.stud(x-1.25F,6.0F,-2.15F,.20F);m.stud(x+1.25F,6.0F,-2.15F,.20F);}
                }
            }
            case LEGS,FEET -> {
                for(int bone:new int[]{4,5}){
                    m.bone(bone);
                    if(d.slot()==Slot.LEGS){
                        m.plate(n,3,0,0,0,7.25F,2.12F,2.12F,2.12F,2.12F,3.14159F,0,.10F,.43F);
                        m.plate(n,steel?0:1,0,0,.2F,4.65F,2.26F+extra*.2F,2.16F,2.28F+extra*.2F,2.24F,1.8F,.45F,.13F,shape);
                        m.plate(n,steel?0:1,0,0,4.35F,6.55F,2.20F,1.98F,2.42F,2.24F,1.55F,.6F,.15F,.52F);
                    }else{
                        m.plate(n,steel?0:1,0,0,6.1F,10.4F,2.18F,2.15F,2.24F,2.30F,1.75F,.25F,.15F,.48F);
                        m.plate(n,1,0,-.45F,10.2F,12.0F,2.23F,2.23F,2.78F,2.85F,3.14159F,0,.15F,.45F);
                        for(int k=0;k<3;k++)m.plate(n,steel?0:1,0,-.35F,10.2F+k*.45F,10.9F+k*.45F,2.26F,2.26F,2.80F+k*.09F,2.84F+k*.09F,1.6F,0,.10F,.50F);
                    }
                }
            }
            default -> {}
        }
        return m.pieces.stream().map(q->new Piece(q.bone,List.copyOf(q.faces))).toList();
    }
}
