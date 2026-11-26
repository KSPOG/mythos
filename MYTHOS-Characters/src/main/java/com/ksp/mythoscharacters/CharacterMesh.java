package com.ksp.mythoscharacters;
import java.util.*;
/** Rounded anatomical shells with vanilla skin face UVs and stable humanoid pivots. */
final class CharacterMesh {
    record V(float x,float y,float z){}
    record Vertex(V position,V normal,float u,float v){}
    record Quad(Vertex a,Vertex b,Vertex c,Vertex d){}
    record Part(int bone,boolean overlay,List<Quad> quads){}
    // Each box uses the vanilla 64x64 skin atlas origin and dimensions.
    private record Shape(int bone,float x,float y,float z,int w,int h,int d,int u,int v,int ou,int ov,float radius){}
    static List<Part> build(boolean slim,int lod){
        int n=switch(lod){case 0->24;case 1->12;case 2->6;default->3;};
        int arm=slim?3:4;
        Shape[] shapes={
            new Shape(0,0,-4,0,8,8,8,0,0,32,0,1.25F),
            new Shape(1,0,6,0,8,12,4,16,16,16,32,.83F),
            new Shape(2,-(arm/2F-1),4,0,arm,12,4,40,16,40,32,.85F),
            new Shape(3,arm/2F-1,4,0,arm,12,4,32,48,48,48,.85F),
            new Shape(4,0,6,0,4,12,4,0,16,0,32,.88F),
            new Shape(5,0,6,0,4,12,4,16,48,0,48,.88F)};
        List<Part> out=new ArrayList<>();
        for(Shape shape:shapes){
            out.add(new Part(shape.bone,false,mesh(shape,n,false)));
            out.add(new Part(shape.bone,true,mesh(shape,n,true)));
        }
        return List.copyOf(out);
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static V unit(float x,float y,float z){float l=(float)Math.sqrt(x*x+y*y+z*z);return new V(x/l,y/l,z/l);}
    private static Vertex vertex(Shape s,int face,float u,float v,boolean overlay){
        float ex=s.w/2F,ey=s.h/2F,ez=s.d/2F;
        float a=2*u-1,b=2*v-1;
        float x=0,y=0,z=0;float tu=0,tv=0,tw=0,th=0;
        switch(face){
            case 0->{x=a*ex;y=b*ey;z=-ez;tu=s.d;tv=s.d;tw=s.w;th=s.h;}
            case 1->{x=-a*ex;y=b*ey;z=ez;tu=s.d*2+s.w;tv=s.d;tw=s.w;th=s.h;}
            case 2->{x=-ex;y=b*ey;z=-a*ez;tu=0;tv=s.d;tw=s.d;th=s.h;}
            case 3->{x=ex;y=b*ey;z=a*ez;tu=s.d+s.w;tv=s.d;tw=s.d;th=s.h;}
            case 4->{x=a*ex;y=-ey;z=-b*ez;tu=s.d;tv=0;tw=s.w;th=s.d;}
            case 5->{x=a*ex;y=ey;z=b*ez;tu=s.d+s.w;tv=0;tw=s.w;th=s.d;}
        }
        float r=s.radius;
        float cx=clamp(x,-ex+r,ex-r),cy=clamp(y,-ey+r,ey-r),cz=clamp(z,-ez+r,ez-r);
        V normal=unit(x-cx,y-cy,z-cz);
        float inflate=overlay?(s.bone==0?.26F:.16F):0;
        x=cx+normal.x*(r+inflate);y=cy+normal.y*(r+inflate);z=cz+normal.z*(r+inflate);
        float h=(y+ey)/(2*ey);
        // Modest taper keeps armor and held-item pivots aligned.
        float taper=s.bone==0?1:s.bone==1?(.98F-.07F*(float)Math.sin(Math.PI*h)):(.96F-.11F*h+.04F*(float)Math.sin(Math.PI*h));
        x*=taper;z*=taper;
        V position=new V(x+s.x,y+s.y,z+s.z);
        return new Vertex(position,normal,((overlay?s.ou:s.u)+tu+tw*u)/64F,((overlay?s.ov:s.v)+tv+th*v)/64F);
    }
    private static List<Quad> mesh(Shape s,int n,boolean overlay){
        List<Quad> result=new ArrayList<>(6*n*n);
        for(int face=0;face<6;face++)for(int i=0;i<n;i++)for(int j=0;j<n;j++){
            float u=i/(float)n,U=(i+1)/(float)n,v=j/(float)n,Vv=(j+1)/(float)n;
            var a=vertex(s,face,u,v,overlay);var b=vertex(s,face,U,v,overlay);var c=vertex(s,face,U,Vv,overlay);var d=vertex(s,face,u,Vv,overlay);
            // Check winding against the analytical outward normal.
            V p=a.position,q=b.position,r=c.position;
            float ax=q.x-p.x,ay=q.y-p.y,az=q.z-p.z,bx=r.x-p.x,by=r.y-p.y,bz=r.z-p.z;
            V normal=a.normal;
            float dot=(ay*bz-az*by)*normal.x+(az*bx-ax*bz)*normal.y+(ax*by-ay*bx)*normal.z;
            result.add(dot>=0?new Quad(a,b,c,d):new Quad(d,c,b,a));
        }
        return List.copyOf(result);
    }
}
