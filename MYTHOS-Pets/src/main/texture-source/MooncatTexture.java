import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class MooncatTexture {
    static final int SIZE=4096,TILE=1024;
    static final int[][] COLORS={{38,39,64},{83,89,124},{177,161,111},{78,59,99},{96,209,255},{157,170,211}};
    static double hash(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0x7fffffff)/2147483648.0;}
    static double noise(double x,double y,int period){
        int i=(int)Math.floor(x),j=(int)Math.floor(y);double a=x-i,b=y-j;
        a=a*a*(3-2*a);b=b*b*(3-2*b);
        int k=Math.floorMod(i,period),l=Math.floorMod(i+1,period);
        return (hash(k,j)*(1-a)+hash(l,j)*a)*(1-b)+(hash(k,j+1)*(1-a)+hash(l,j+1)*a)*b;
    }
    static int clamp(double a){return Math.max(0,Math.min(255,(int)a));}
    public static void main(String[] args)throws Exception{
        BufferedImage img=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int m=(y/TILE*4+x/TILE)%6;
            double u=(x%TILE/(double)TILE-0.012)/0.976,v=(y%TILE/(double)TILE-0.012)/0.976;
            u-=Math.floor(u);
            double shade;
            if(m==0||m==1||m==5){
                double flow=noise(u*12,v*9,12)-0.5;
                double hairs=noise((u+flow*0.018)*220,v*37,220);
                double bundles=noise((u+flow*0.025)*70,v*16,70);
                double clouds=noise(u*12,v*11,12);
                shade=(hairs-0.5)*22+(bundles-0.5)*24+(clouds-0.5)*27;
                if(m==0){
                    double stripe=Math.sin(v*43+Math.sin(u*Math.PI*6)*2.8+flow*2);
                    shade-=Math.max(0,stripe-0.45)*19;
                    // Subtle celestial silver flecks woven into fur, not particle spam.
                    if(hash((int)(u*970),(int)(v*970))>0.998)shade+=65;
                }
            }else if(m==4){
                shade=25+noise(u*40,v*32,40)*30+Math.sin(u*Math.PI*12)*9;
            }else shade=(noise(u*80,v*22,80)-0.5)*20;
            int[] c=COLORS[m];img.setRGB(x,y,0xff000000|(clamp(c[0]+shade)<<16)|(clamp(c[1]+shade)<<8)|clamp(c[2]+shade));
        }
        File out=new File(args[0]);out.getParentFile().mkdirs();ImageIO.write(img,"PNG",out);
    }
}
