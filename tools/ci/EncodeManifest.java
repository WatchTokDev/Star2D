import axml.xml.AxmlUtil;

/** CI helper: text AndroidManifest.xml -> binary AXML. Usage: java -cp app/libs/lib_7.jar tools/ci/EncodeManifest.java in.xml out.xml */
public class EncodeManifest {
    public static void main(String[] a) throws Exception {
        AxmlUtil.encodeFile(a[0], a[1]);
    }
}
