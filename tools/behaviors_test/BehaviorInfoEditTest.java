import com.star4droid.star2d.behaviors.*;
public class BE { public static void main(String[] a){
  String s=BehaviorTemplate.source("Health","BOTH");
  String o=BehaviorInfoEdit.set(s,"name",BehaviorInfoEdit.quote("My \"Health\""));
  o=BehaviorInfoEdit.set(o,"icon",BehaviorInfoEdit.quote("heart"));
  o=BehaviorInfoEdit.set(o,"desc",BehaviorInfoEdit.quote("Hit points (with, comma)"));
  o=BehaviorInfoEdit.set(o,"targets","BehaviorInfo.Target.UI");
  o=BehaviorInfoEdit.set(o,"color",BehaviorInfoEdit.quote("red"));
  System.out.println(o.substring(o.indexOf("@BehaviorInfo"),o.indexOf("public class")));
  BehaviorMeta m=SourceScanner.behavior(o,"/x/Health.java");
  System.out.println(m.name+"|"+m.icon+"|"+m.desc+"|"+m.targets+"|"+m.category);
}}
