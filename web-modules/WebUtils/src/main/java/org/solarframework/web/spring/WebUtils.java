package org.solarframework.web.spring;

import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Map;

public class WebUtils {

    public static boolean canAccessPage(Model model, Principal loggedUser, String path) {
        try {
            Class<?> Account_User = Class.forName("org.solarframework.auth.obj.Account_User");
            Object result = Account_User.getDeclaredMethod("getByAuthentication", Principal.class).invoke(null, loggedUser);
            if (!(boolean)result.getClass().getMethod("hasAccessToPath", String.class).invoke(result, path)) return false;
            Class.forName("org.solarframework.auth.web.spring.AuthUtils").getDeclaredMethod("addEssential", Model.class, Principal.class, Account_User).invoke(null, model, loggedUser, result);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
    public static boolean canAccessPage(ModelAndView model, Principal loggedUser, String path) {
        try {
            Class<?> Account_User = Class.forName("org.solarframework.auth.obj.Account_User");
            Object result = Account_User.getDeclaredMethod("getByAuthentication", Principal.class).invoke(null, loggedUser);
            if (!(boolean)result.getClass().getMethod("hasAccessToPath", String.class).invoke(result, path)) return false;
            Class.forName("org.solarframework.auth.web.spring.AuthUtils").getDeclaredMethod("addEssential", ModelAndView.class, Principal.class, Account_User).invoke(null, model, loggedUser, result);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
    public static boolean canAccessPage(Map<String, Object> model, Principal loggedUser, String path) {
        try {
            Class<?> Account_User = Class.forName("org.solarframework.auth.obj.Account_User");
            Object result = Account_User.getDeclaredMethod("getByAuthentication", Principal.class).invoke(null, loggedUser);
            if (!(boolean)result.getClass().getMethod("hasAccessToPath", String.class).invoke(result, path)) return false;
            Class.forName("org.solarframework.auth.web.spring.AuthUtils").getDeclaredMethod("addEssential", Map.class, Principal.class, Account_User).invoke(null, model, loggedUser, result);
            return true;
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return false;
        }
    }
}
