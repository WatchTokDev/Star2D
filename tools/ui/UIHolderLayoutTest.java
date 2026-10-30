import com.star4droid.template.ui.UIHolderLayout;
public class LT {
  static int bad=0;
  static void eq(String n,float[] r,float... e){ for(int i=0;i<4;i++) if(Math.abs(r[i]-e[i])>0.01f){bad++;System.out.println("FAIL "+n+" "+java.util.Arrays.toString(r)+" != "+java.util.Arrays.toString(e));return;} System.out.println("ok "+n); }
  public static void main(String[] a){
    // screen 1280x720
    eq("stretch both", UIHolderLayout.compute("topleft",true,true,0,0,0,0,300,300,1280,720),0,0,1280,720);
    eq("stretch margins", UIHolderLayout.compute("center",true,true,0,0,20,10,300,300,1280,720),20,10,1240,700);
    eq("bottom bar", UIHolderLayout.compute("bottom",true,false,0,0,0,16,300,140,1280,720),0,16,1280,140);
    eq("center 80/60", UIHolderLayout.compute("center",false,false,80,60,0,0,300,300,1280,720),128,144,1024,432);
    eq("topright", UIHolderLayout.compute("topright",false,false,0,0,10,20,200,100,1280,720),1070,600,200,100);
    eq("bottomleft", UIHolderLayout.compute("bottomleft",false,false,0,0,10,20,200,100,1280,720),10,20,200,100);
    eq("left center", UIHolderLayout.compute("left",false,false,0,0,0,0,200,100,1280,720),0,310,200,100);
    eq("portrait pct", UIHolderLayout.compute("top",false,false,100,25,0,0,1,1,720,1280),0,960,720,320);
    System.out.println(bad==0?"ALL OK":"FAILED "+bad); System.exit(bad==0?0:1);
  }
}
