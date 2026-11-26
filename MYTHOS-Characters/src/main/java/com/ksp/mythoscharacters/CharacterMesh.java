package com.ksp.mythoscharacters;
import java.util.*;

/** Anatomical lofts in vanilla bone space; face and skin UVs retain the existing atlas. */
final class CharacterMesh {
    record V(float x,float y,float z){
        V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        V mul(float f){return new V(x*f,y*f,z*f);}
        V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        float dot(V b){return x*b.x+y*b.y+z*b.z;}
        V unit(){float l=(float)Math.sqrt(dot(this));return l<1e-7F?new V(0,-1,0):mul(1/l);}
    }
    record Vertex(V position,V normal,float u,float v){}
    record Quad(Vertex a,Vertex b,Vertex c,Vertex d){}
    record Part(int bone,boolean overlay,List<Quad> quads){}
    // Each ring specifies height, center X/Z, horizontal radius and depth.
    private record Ring(float y,float x,float z,float rx,float rz){}
    private record Atlas(int u,int v,int w,int h,int d){}
    private record Shape(int bone,Atlas base,Atlas overlay,Ring[] rings,int kind){}
    private static Ring r(float y,float x,float z,float rx,float rz){return new Ring(y,x,z,rx,rz);}
    private static Atlas a(int u,int v,int w,int h,int d){return new Atlas(u,v,w,h,d);}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static float spline(float a,float b,float c,float d,float t){
        float t2=t*t,t3=t2*t;
        return .5F*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t2+(-a+3*b-3*c+d)*t3);
    }
    private static Ring sample(Ring[] rings,float t){
        float x=t*(rings.length-1);int k=Math.min(rings.length-2,(int)x);float f=x-k;
        Ring a=rings[Math.max(0,k-1)],b=rings[k],c=rings[k+1],d=rings[Math.min(rings.length-1,k+2)];
        return r(mix(b.y,c.y,f),spline(a.x,b.x,c.x,d.x,f),spline(a.z,b.z,c.z,d.z,f),
            Math.max(.018F,spline(a.rx,b.rx,c.rx,d.rx,f)),Math.max(.018F,spline(a.rz,b.rz,c.rz,d.rz,f)));
    }
    private static float gauss(float x,float y,float cx,float cy,float sx,float sy){
        float a=(x-cx)/sx,b=(y-cy)/sy;return (float)Math.exp(-.5F*(a*a+b*b));
    }
    // Front angle = zero. Smooth facial relief is part of the skull, not floating primitives.
    private static V position(Shape s,float u,float t,boolean overlay){
        Ring q=sample(s.rings,t);
        double angle=u*Math.PI*2;float sn=(float)Math.sin(angle),cs=(float)Math.cos(angle);
        float x=sn*q.rx,z=-cs*q.rz;
        if(s.kind==0&&cs>0){
            float y=q.y;
            float nose=.72F*gauss(x,y,0,-4.6F,.38F,.78F)+.37F*gauss(x,y,0,-5.4F,.24F,1.0F);
            float nostrils=.16F*(gauss(x,y,-.35F,-4.35F,.2F,.25F)+gauss(x,y,.35F,-4.35F,.2F,.25F));
            float brow=.21F*(gauss(x,y,-.93F,-5.85F,.65F,.28F)+gauss(x,y,.93F,-5.85F,.65F,.28F));
            float sockets=.26F*(gauss(x,y,-.92F,-5.38F,.52F,.32F)+gauss(x,y,.92F,-5.38F,.52F,.32F));
            float cheeks=.23F*(gauss(x,y,-1.3F,-4.55F,.5F,.55F)+gauss(x,y,1.3F,-4.55F,.5F,.55F));
            float lips=.18F*gauss(x,y,0,-3.63F,.65F,.20F)-.07F*gauss(x,y,0,-3.59F,.6F,.05F);
            float chin=.18F*gauss(x,y,0,-2.9F,.6F,.35F);
            z-=(nose+nostrils+brow-sockets+cheeks+lips+chin)*cs*cs;
        }else if(s.kind==1&&cs>0){
            z-=.22F*(gauss(x,q.y,-1.42F,2.5F,.95F,1.25F)+gauss(x,q.y,1.42F,2.5F,.95F,1.25F))*cs;
        }
        float inflate=overlay?(s.bone==0?.10F:.11F):0;
        return new V(q.x+x+sn*inflate,q.y,q.z+z-cs*inflate);
    }
    private static Vertex vertex(Shape s,float u,float t,boolean overlay){
        V p=position(s,u,t,overlay);
        float e=.0005F;
        V du=position(s,u+e,t,overlay).sub(position(s,u-e,t,overlay));
        V dt=position(s,u,Math.min(1,t+e),overlay).sub(position(s,u,Math.max(0,t-e),overlay));
        V normal=dt.cross(du).unit();
        // Continuous mapping around the original front/right/back/left skin rectangles.
        Atlas a=overlay?s.overlay:s.base;
        float theta=u*4+.5F;
        int face=Math.floorMod((int)Math.floor(theta),4);
        float f=theta-(float)Math.floor(theta);
        float x=switch(face){case 0->a.d+f*a.w;case 1->a.d+a.w+f*a.d;case 2->2*a.d+a.w+f*a.w;default->f*a.d;};
        return new Vertex(p,normal,(a.u+x)/64F,(a.v+a.d+t*a.h)/64F);
    }
    private static void add(List<Quad> out,Vertex a,Vertex b,Vertex c,Vertex d){
        V face=b.position.sub(a.position).cross(c.position.sub(a.position));
        if(face.dot(a.normal)<0)out.add(new Quad(d,c,b,a));else out.add(new Quad(a,b,c,d));
    }
    private static List<Quad> loft(Shape s,int n,int rows,boolean overlay){
        List<Quad> out=new ArrayList<>();
        // Split at face boundaries to avoid interpolation across discontinuous atlas islands.
        for(int face=0;face<4;face++){
            float start=(face-.5F)/4F;
            for(int i=0;i<n/4;i++)for(int j=0;j<rows;j++){
                float u=start+i/(float)n,U=start+(i+1)/(float)n,t=j/(float)rows,T=(j+1)/(float)rows;
                float left=u+1e-6F,right=U-1e-6F;
                var a=vertex(s,left,t,overlay);var b=vertex(s,right,t,overlay);
                var c=vertex(s,right,T,overlay);var d=vertex(s,left,T,overlay);
                // Coordinates at exact seams coincide; only UVs remain on their own face.
                a=new Vertex(position(s,u,t,overlay),a.normal,a.u,a.v);
                b=new Vertex(position(s,U,t,overlay),b.normal,b.u,b.v);
                c=new Vertex(position(s,U,T,overlay),c.normal,c.u,c.v);
                d=new Vertex(position(s,u,T,overlay),d.normal,d.u,d.v);
                add(out,a,b,c,d);
            }
        }
        Atlas a=overlay?s.overlay:s.base;
        for(int end=0;end<2;end++){
            Ring ring=s.rings[end==0?0:s.rings.length-1];
            V normal=new V(0,end==0?-1:1,0);
            float x0=a.u+a.d+(end==0?0:a.w),y0=a.v;
            Vertex center=new Vertex(new V(ring.x,ring.y,ring.z),normal,(x0+a.w*.5F)/64F,(y0+a.d*.5F)/64F);
            for(int i=0;i<n;i++){
                float u=i/(float)n,U=(i+1)/(float)n;
                V p=position(s,u,end,overlay),q=position(s,U,end,overlay);
                Vertex p1=new Vertex(p,normal,(x0+a.w*(.5F+.5F*(float)Math.sin(u*Math.PI*2)))/64F,(y0+a.d*(.5F-.5F*(float)Math.cos(u*Math.PI*2)))/64F);
                Vertex p2=new Vertex(q,normal,(x0+a.w*(.5F+.5F*(float)Math.sin(U*Math.PI*2)))/64F,(y0+a.d*(.5F-.5F*(float)Math.cos(U*Math.PI*2)))/64F);
                add(out,center,p1,p2,p2);
            }
        }
        return out;
    }
    private static Shape shape(int b,Atlas a,Atlas o,int kind,Ring... rings){return new Shape(b,a,o,rings,kind);}
    private static void append(List<Quad> dest,Shape s,int n,int rows,boolean overlay){dest.addAll(loft(s,n,rows,overlay));}
    static List<Part> build(boolean slim,int lod){return build(slim,lod,0,false);}
    static List<Part> build(boolean slim,int lod,int physique,boolean hair){
        int n=switch(lod){case 0->96;case 1->48;case 2->24;default->12;};
        int rows=switch(lod){case 0->48;case 1->24;case 2->12;default->6;};
        int arm=slim?3:4;
        Atlas h=a(0,0,8,8,8),ho=a(32,0,8,8,8),body=a(16,16,8,12,4),bo=a(16,32,8,12,4);
        Shape[] shapes={
            shape(0,h,ho,0,r(-9.0F,0,.12F,.03F,.04F),r(-8.7F,0,.18F,1.24F,1.28F),r(-7.8F,0,.2F,2.05F,1.82F),r(-6.4F,0,.22F,2.24F,1.98F),r(-5.3F,0,.10F,2.15F,1.92F),r(-4.2F,0,-.05F,1.94F,1.73F),r(-3.1F,0,-.14F,1.47F,1.53F),r(-2.65F,0,-.03F,.79F,1.02F),r(-2.55F,0,.08F,.22F,.35F)),
            shape(1,body,bo,1,r(-.45F,0,0,1.05F,1.12F),r(.35F,0,.08F,2.65F,1.48F),r(1.6F,0,.06F,3.55F,1.96F),r(3.6F,0,0,3.45F,2.03F),r(5.8F,0,.05F,2.92F,1.77F),r(8.2F,0,.11F,2.48F,1.65F),r(10.2F,0,.15F,2.93F,1.83F),r(12F,0,.1F,2.65F,1.67F)),
            arm(2,arm,slim),arm(3,arm,slim),leg(4),leg(5)
        };
        List<Part> result=new ArrayList<>();
        for(Shape s:shapes){
            if(physique!=0){
                Ring[] adjusted=new Ring[s.rings.length];
                for(int i=0;i<adjusted.length;i++){
                    Ring q=s.rings[i];float factor=1;
                    if(s.bone==1){factor=physique==1?.90F:1.08F;if(physique==1&&q.y>9)factor=1.05F;}
                    else if(s.bone>=2)factor=physique==1?.90F:1.06F;
                    adjusted[i]=r(q.y,q.x,q.z,q.rx*factor,q.rz*factor);
                }
                s=shape(s.bone,s.base,s.overlay,s.kind,adjusted);
            }
            List<Quad> base=loft(s,n,rows,false);
            if(s.bone==0){
                int small=Math.max(8,(n/16)*4);
                append(base,shape(0,h,ho,2,r(-3.15F,0,.15F,.80F,.86F),r(-1.25F,0,.12F,.92F,.9F),r(.45F,0,.1F,1.08F,1.0F)),small,rows/2,false);
                for(int side:new int[]{-1,1}){
                    append(base,shape(0,h,ho,2,r(-6.1F,side*1.99F,.27F,.12F,.16F),r(-5.8F,side*2.13F,.26F,.34F,.43F),r(-4.9F,side*2.14F,.30F,.29F,.37F),r(-4.65F,side*2.02F,.28F,.12F,.13F)),small,rows/2,false);
                }
            }
            if(s.bone==2||s.bone==3){
                int side=s.bone==2?-1:1,small=Math.max(8,(n/16)*4);
                float cx=side*.2F;
                // Individual fingers, slightly curled into a relaxed weapon-ready grip.
                for(int i=0;i<4;i++){
                    float x=cx+(i-1.5F)*.35F,y=9.6F+(i==0||i==3?-.16F:0);
                    append(base,shape(s.bone,s.base,s.overlay,2,r(y,x,-.12F,.18F,.24F),r(y+.6F,x,-.18F,.17F,.21F),r(y+1.0F,x,-.40F,.14F,.17F),r(y+1.12F,x,-.48F,.05F,.08F)),small,rows/3,false);
                }
                append(base,shape(s.bone,s.base,s.overlay,2,r(8.25F,cx-side*.55F,-.37F,.26F,.25F),r(8.85F,cx-side*.82F,-.55F,.23F,.22F),r(9.45F,cx-side*.65F,-.71F,.14F,.15F),r(9.60F,cx-side*.58F,-.72F,.04F,.05F)),small,rows/3,false);
            }
            result.add(new Part(s.bone,false,List.copyOf(base)));
            // Only a skin-conforming shell; do not duplicate fingers/ears.
            List<Quad> outer=loft(s,n,rows,true);
            if(hair&&s.bone==0){
                // Hair cap follows the scalp; uses the original hat atlas.
                append(outer,shape(0,h,ho,2,r(-9.18F,0,.2F,.04F,.05F),r(-8.8F,0,.25F,1.34F,1.36F),r(-7.8F,0,.28F,2.15F,1.96F),r(-7.15F,0,.35F,2.29F,2.02F)),n,rows/3,true);
            }
            result.add(new Part(s.bone,true,List.copyOf(outer)));
        }
        return List.copyOf(result);
    }
    private static Shape arm(int bone,int width,boolean slim){
        int side=bone==2?-1:1;float x=side*(width/2F-1),f=slim?.87F:1;
        Atlas a=bone==2?a(40,16,width,12,4):a(32,48,width,12,4);
        Atlas o=bone==2?a(40,32,width,12,4):a(48,48,width,12,4);
        return shape(bone,a,o,2,r(-2F,x,0,.40F,.53F),r(-1F,x,0,1.22F*f,1.36F),r(.8F,x,0,1.43F*f,1.42F),r(2.7F,x,-.08F,1.26F*f,1.31F),r(4.3F,x,.05F,.96F*f,1.06F),r(5.5F,x,.04F,1.03F*f,1.10F),r(7.0F,x,-.03F,.76F*f,.81F),r(8.0F,x,-.04F,.57F*f,.62F),r(9.2F,side*.2F,-.1F,.76F,.51F),r(9.8F,side*.2F,-.12F,.69F,.40F));
    }
    private static Shape leg(int bone){
        Atlas a=bone==4?a(0,16,4,12,4):a(16,48,4,12,4);
        Atlas o=bone==4?a(0,32,4,12,4):a(0,48,4,12,4);
        return shape(bone,a,o,2,r(0,0,0,1.52F,1.73F),r(1.3F,0,0,1.58F,1.72F),r(3.6F,0,-.12F,1.32F,1.48F),r(5.5F,0,-.20F,.99F,1.04F),r(6.5F,0,.08F,1.12F,1.21F),r(8.2F,0,.22F,1.12F,1.19F),r(10F,0,.08F,.69F,.76F),r(10.8F,0,-.34F,.75F,1.12F),r(11.5F,0,-.80F,.97F,1.73F),r(12F,0,-.80F,.92F,1.63F));
    }
}
