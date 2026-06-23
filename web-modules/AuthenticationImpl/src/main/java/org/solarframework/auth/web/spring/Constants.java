package org.solarframework.auth.web.spring;

import org.solarframework.auth.obj.Account_Notification;
import org.solarframework.auth.obj.Account_User;
import org.springframework.ui.Model;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

public class Constants {

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
}
