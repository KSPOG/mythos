import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Build-time texture generator; not shipped as executable mod code. */
public final class DragonTexture {
    private static final int SIZE=4096, TILE=1024;
    private static final int[][] COLORS={{48,44,47},{89,49,36},{128,111,91},{82,34,28},
        {255,109,19},{179,56,18},{45,29,32},{111,38,31}};
    private static double hash(int x,int y) {
        int n=x*374761393+y*668265263;
        n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16)) & 0x7fffffff) / 2147483648.0;
    }
    // Periodic circumferential value noise: the two sides of the UV seam match.
    private static double noise(double x,double y,int period){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        double fx=x-ix,fy=y-iy;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        int a=Math.floorMod(ix,period),b=Math.floorMod(ix+1,period);
        double low=hash(a,iy)*(1-fx)+hash(b,iy)*fx;
        double high=hash(a,iy+1)*(1-fx)+hash(b,iy+1)*fx;
        return low*(1-fy)+high*fy;
    }
    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    private static int hornColor(int x,int y){
        // Match the renderer's UV inset, not the atlas tile edge.
        double u=(x/(double)TILE-0.008)/0.984;
        u-=Math.floor(u);
        double t=Math.max(0,Math.min(1,(y/(double)TILE-0.008)/0.984));
        double warp=noise(u*9,t*5,9)-0.5;
        double grain=noise((u+warp*0.018)*83,t*6,83);
        double fine=noise((u+warp*0.009)*211,t*23,211);
        double broad=noise(u*13,t*9,13);
        double weather=noise(u*27,t*35,27);
        // Irregular growth bands are subdued at the naturally worn tip.
        double bandPhase=t*19+noise(u*7,t*5,7)*0.52+noise(u*13,t*12,13)*0.18;
        double band=bandPhase-Math.floor(bandPhase);
        double ridge=(Math.exp(-Math.pow((band-0.25)/0.13,2))*8
            -Math.exp(-Math.pow((band-0.43)/0.055,2))*12)*(1-smooth((t-0.60)/0.40));
        // Sparse elongated fissures with a narrow pale edge, not uniform stripes.
        double fissure=noise((u+warp*0.024)*49,t*8,49);
        double mask=smooth((noise(u*19,t*13,19)-0.48)/0.29)*(1-smooth((t-0.78)/0.22));
        double crack=-28*smooth((0.20-fissure)/0.085)*mask
            +7*Math.exp(-Math.pow((fissure-0.225)/0.023,2))*mask;
        double wear=smooth((t-0.56)/0.44)*(0.55+0.45*broad);
        double shade=(grain-0.5)*26+(fine-0.5)*13+(broad-0.5)*20
            +(weather-0.5)*10+ridge+crack;
        double blend=smooth(t);
        double red=57+100*blend+19*wear;
        double green=48+88*blend+19*wear;
        double blue=43+70*blend+18*wear;
        return 0xff000000|(clamp(red+shade)<<16)|(clamp(green+shade*0.94)<<8)|clamp(blue+shade*0.85);
    }
    private static int clamp(double n){return Math.max(0,Math.min(255,(int)n));}
    public static void main(String[] args)throws Exception{
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int material=(y/TILE%2)*4+x/TILE;
            int u=x%TILE,v=y%TILE;
            if(material==2){
                image.setRGB(x,y,hornColor(u,v));
                continue;
            }
            double noise=hash(x,y)*7+Math.sin(u*0.012)*Math.sin(v*0.018)*4;
            double shade;
            if(material==0||material==1||material==6){
                double warpedY=v+3*Math.sin(u*0.038)+2*Math.sin(u*0.093);
                int row=(int)Math.floor(warpedY/22);
                double warpedX=u+(row&1)*13+3*Math.sin(v*0.064);
                double fx=warpedX/26-Math.floor(warpedX/26),fy=warpedY/22-Math.floor(warpedY/22);
                double edge=Math.min(Math.min(fx,1-fx),Math.min(fy,1-fy));
                double ridge=Math.sin(Math.PI*fx)*Math.sin(Math.PI*fy);
                double mottling=hash((int)Math.floor(warpedX/26),row)*17-8;
                shade=(edge<0.075?-18:ridge*23-6)+mottling;
                if(material==1)shade+=Math.sin(v*0.16)*9;
            }else if(material==7||material==3){
                shade=Math.sin(u*0.018+Math.sin(v*0.012)*3)*12+Math.sin(v*0.06)*5;
                if(material==7){double vein=Math.abs(Math.sin(u*0.033+v*0.009+Math.sin(v*0.017)*0.65));shade-=Math.max(0,0.09-vein)*160;}
            }else shade=Math.sin(v*0.12)*7;
            int[] base=COLORS[material];
            int rgb=0xff000000|(clamp(base[0]+shade+noise)<<16)|(clamp(base[1]+shade+noise)<<8)|clamp(base[2]+shade+noise);
            image.setRGB(x,y,rgb);
        }
        File output=new File(args[0]);output.getParentFile().mkdirs();
        ImageIO.write(image,"PNG",output);
    }
}
