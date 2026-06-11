import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.lang.reflect.Modifier;

public class DumpAE {
    public static void main(String[] args) throws Exception {
        File file = new File("d:/seria/plugins/BlockRegen/lib/AdvancedEnchantments-8.7.4.jar");
        URL url = file.toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{url});
        Class<?> clazz = classLoader.loadClass("net.advancedplugins.ae.api.AEAPI");
        
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                System.out.println(method.toString());
            }
        }
    }
}
