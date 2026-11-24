import com.npcarchitects.Blueprint;
import java.util.HashSet;
public class BlueprintTest {
 public static void main(String[] args) {
  for(int w=5;w<=15;w++) for(int d=5;d<=15;d++) {
   var cells=Blueprint.house(w,d); var positions=new HashSet<String>(); int last=-1;
   for(var c:cells) { if(c.x()<0||c.x()>=w||c.z()<0||c.z()>=d||c.y()<last||!positions.add(c.x()+":"+c.y()+":"+c.z())) throw new AssertionError(); last=c.y(); }
   if(cells.size()!=2*w*d+3*(2*w+2*d-4)-2) throw new AssertionError();
  }
  System.out.println("121 blueprint dimension combinations passed");
 }
}
