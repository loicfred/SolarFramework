package org.solarframework.auth.web.spring;

import org.solarframework.auth.obj.Account_Notification;
import org.solarframework.auth.obj.Account_User;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AuthUtils {

    public static void addEssential(Model model, Principal loggedUser, Account_User U) {
        model.addAttribute("principal", loggedUser);
        if (loggedUser != null) {
            model.addAttribute("user", U);
            List<Account_Notification> notifs = Account_Notification.ofUser(U.getID(), 7);
            model.addAttribute("notifications", notifs);
            model.addAttribute("isRead", notifs.stream().allMatch(Account_Notification::isOpened));
        } else {
            model.addAttribute("notifications", new ArrayList<>());
            model.addAttribute("isRead", true);
        }
    }

    public static void addEssential(ModelAndView model, Principal loggedUser, Account_User U) {
        model.addObject("principal", loggedUser);
        if (loggedUser != null) {
            model.addObject("user", U);
            List<Account_Notification> notifs = Account_Notification.ofUser(U.getID(), 7);
            model.addObject("notifications", notifs);
            model.addObject("isRead", notifs.stream().allMatch(Account_Notification::isOpened));
        } else {
            model.addObject("notifications", new ArrayList<>());
            model.addObject("isRead", true);
        }
    }
    public static void addEssential(Map<String, Object> model, Principal loggedUser, Account_User U) {
        model.put("principal", loggedUser);
        if (loggedUser != null) {
            model.put("user", U);
            List<Account_Notification> notifs = Account_Notification.ofUser(U.getID(), 7);
            model.put("notifications", notifs);
            model.put("isRead", notifs.stream().allMatch(Account_Notification::isOpened));
        } else {
            model.put("notifications", new ArrayList<>());
            model.put("isRead", true);
        }
    }

}
