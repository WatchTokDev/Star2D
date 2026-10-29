package com.star4droid.star2d.remote;
import com.star4droid.star2d.ui.blocks.*;
import java.util.*;
public class Dump {
    public static void main(String[] a) throws Exception {
        BlockCatalog.load(new android.content.Context(){});
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet()) {
            System.out.println("## " + e.getKey());
            for (BlockCatalog.Def d : e.getValue()) {
                StringBuilder sb = new StringBuilder(d.title + (d.isIf ? " [container]" : "") + "  fields=");
                BlockModel.Node n = d.create();
                for (BlockModel.Field f : n.fields) sb.append(f.label()).append("(").append(f.name).append(")=").append(f.value).append("; ");
                System.out.println(sb);
            }
        }
        System.out.println("## REPORTERS");
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) System.out.println(r.group + " | " + r.display + " | " + r.template);
    }
}
