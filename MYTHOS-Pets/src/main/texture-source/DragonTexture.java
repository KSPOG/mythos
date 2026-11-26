import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Build-time texture generator; not shipped as executable mod code. */
public final class DragonTexture {
    private static final int SIZE=2048, TILE=512;
    private static final int[][] COLORS={{48,44,47},{89,49,36},{128,111,91},{82,34,28},
        {255,109,19},{179,56,18},{45,29,32},{111,38,31}};
    private static double hash(int x,int y) {
        int n=x*374761393+y*668265263;
        n=(n^(n>>>13))*1274126177;
        return (n^(n>>>16) & 0x7fffffff) / 2147483648.0;
    }
    private static int clamp(double n){return Math.max(0,Math.min(255,(int)n));}
    public static void main(String[] args)throws Exception{
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int material=(y/TILE%2)*4+x/TILE;
            int u=x%TILE,v=y%TILE;
            double noise=hash(x,y)*11;
            double shade;
            if(material==0||material==1||material==6){
                int row=v/22;
                double fx=((u+(row%2)*13)%26)/26.0,fy=(v%22)/22.0;
                double edge=Math.min(Math.min(fx,1-fx),Math.min(fy,1-fy));
                double ridge=Math.sin(Math.PI*fx)*Math.sin(Math.PI*fy);
                shade=edge<0.075?-20:ridge*30-8;
                if(material==1)shade+=Math.sin(v*0.16)*9;
            }else if(material==2){
                shade=Math.sin(u*0.16+Math.sin(v*0.021)*2)*12+Math.sin(v*0.075)*8;
            }else if(material==7||material==3){
                shade=Math.sin(u*0.037+Math.sin(v*0.023)*3)*12+Math.sin(v*0.12)*5;
            }else shade=Math.sin(v*0.12)*7;
            int[] base=COLORS[material];
            int rgb=0xff000000|(clamp(base[0]+shade+noise)<<16)|(clamp(base[1]+shade+noise)<<8)|clamp(base[2]+shade+noise);
            image.setRGB(x,y,rgb);
        }
        File output=new File(args[0]);output.getParentFile().mkdirs();
        ImageIO.write(image,"PNG",output);
    }
}
