import com.npcarchitects.Blueprint;
import java.util.HashSet;
public class BlueprintTest {
 public static void main(String[] args) {
  int cases=0;
  for(int w=5;w<=15;w++) for(int d=5;d<=15;d++) for(int h=4;h<=12;h++) for(int r=0;r<360;r+=90) {
   var cells=Blueprint.house(w,d,h,r); var positions=new HashSet<String>(); int last=-1;
   int rw=r%180==0?w:d, rd=r%180==0?d:w;
   for(var c:cells) { if(c.x()<0||c.x()>=rw||c.z()<0||c.z()>=rd||c.y()<last||c.y()>=h||!positions.add(c.x()+":"+c.y()+":"+c.z())) throw new AssertionError(); last=c.y(); }
   if(cells.size()!=2*w*d+(h-2)*(2*w+2*d-4)-2) throw new AssertionError();
   int x=w/2,z=0; int dx=switch(r){case 90->d-1-z;case 180->w-1-x;case 270->z;default->x;}; int dz=switch(r){case 90->x;case 180->d-1-z;case 270->w-1-x;default->z;};
   if(positions.contains(dx+":1:"+dz)||positions.contains(dx+":2:"+dz)) throw new AssertionError("Door obstructed");
   cases++;
  }
  System.out.println(cases+" blueprint dimension/height/rotation combinations passed");
 }
}
