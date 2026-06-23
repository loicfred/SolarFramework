package org.solarframework.auth.web.spring.v1;

import jakarta.servlet.http.HttpServletRequest;
import org.solarframework.auth.obj.Account_EmailVerification;
import org.solarframework.auth.obj.Account_Notification;
import org.solarframework.auth.obj.Account_User;
import org.solarframework.auth.obj.enums.EmailVerificationType;
import org.solarframework.auth.obj.enums.Gender;
import org.solarframework.auth.obj.enums.UserStatus;
import org.solarframework.auth.web.spring.AuthEmailService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Controller
@RequestMapping("/auth/v1")
public class AuthController {
    private final PasswordEncoder passwordEncoder;
    private final AuthEmailService authEmailService;

    public AuthController(PasswordEncoder passwordEncoder, AuthEmailService authEmailService) {
        this.passwordEncoder = passwordEncoder;
        this.authEmailService = authEmailService;
    }

    @GetMapping("/login")
    public String login(Principal loggedUser) {
        if (loggedUser != null) return "redirect:/";
        return "auth/v1/login";
    }



    @GetMapping("/signup")
    public String signupForm(Model model, Principal loggedUser) {
        if (loggedUser != null) return "redirect:/";
        model.addAttribute("user", new UserModel());
        return "auth/v1/signup";
    }
    @PostMapping("/signup")
    public String register(HttpServletRequest request, UserModel model) {
        if (!validatePassword(model.Password)) return "redirect:/accounts/signup?badpassword";
        if (Account_User.getByEmail(model.Email) != null) return "redirect:/accounts/signup?bademail";
        //if (user.DateOfBirth < 18) return "redirect:/accounts/signup?badage";
        clearFailedLogins(model.Email);

        Account_User u = new Account_User();
        u.setFirstName(model.FirstName);
        u.setLastName(model.LastName);
        u.setGender(model.Gender);
        //u.setAddress(model.Address);
        u.setDateOfBirth(model.DateOfBirth);
        u.setPasswordHash(passwordEncoder.encode(model.Password));
        u.Write();
        String token = UUID.randomUUID().toString();
        new Account_EmailVerification(u, token, EmailVerificationType.REGISTRATION);
        authEmailService.sendVerificationEmail("v1", request, model.Email, token);
        return "redirect:/auth/v1/login?verify";
    }

    @GetMapping("/email/verify")
    public String verifyAccount(@RequestParam String token, Model model) {
        Account_EmailVerification vToken = Account_EmailVerification.getByToken(token);
        if (vToken == null) {
            model.addAttribute("error", "Verification code has expired.");
            return "auth/v1/verification";
        } else {
            vToken.getUser().setStatus(UserStatus.ACTIVE);
            vToken.getUser().setEmailVerified(true);
            vToken.getUser().UpdateOnly("Verified", "Enabled");
            vToken.Delete();
            new Account_Notification(vToken.getUser().getID(), "Your account has been verified!", "Your account has been verified successfully! You can now log in.");
            model.addAttribute("success", "Your account has been verified! You can now log in.");
            model.addAttribute("loginparam", "verified");
            return "auth/v1/verification";
        }
    }

    @GetMapping("/delete/verify")
    public String deleteAccount(@RequestParam(required = false) String token, Model model) {
        Account_EmailVerification vToken = Account_EmailVerification.getByToken(token);
        if (vToken == null) {
            model.addAttribute("error", "Verification code has expired.");
            return "auth/v1/verification";
        } else {
            vToken.getUser().Delete();
            vToken.Delete();
            SecurityContextHolder.clearContext();
            model.addAttribute("success", "Your account has been deleted successfully! You can now sign in once again.");
            model.addAttribute("loginparam", "deleted");
            return "auth/v1/verification";
        }
    }


    @GetMapping("/resetpassword")
    public String resetPassword(Model model) {
        model.addAttribute("user", new UserModel());
        return "auth/v1/resetpassword";
    }
    @PostMapping("/resetpassword")
    public String resetPassword(HttpServletRequest request, RedirectAttributes model, @ModelAttribute UserModel user, @RequestParam String type) {
        if (type.equals("email")) {
            Account_User u = Account_User.getByEmail(user.Email);
            if (u == null) {
                model.addFlashAttribute("error", "No account with this email exists.");
                return "redirect:/auth/v1/resetpassword";
            }
            String token = UUID.randomUUID().toString();
            new Account_EmailVerification(u, token, EmailVerificationType.PASSWORD_RESET);
            authEmailService.sendResetPasswordEmail("v1", request, u.getEmail(), token);

            model.addFlashAttribute("success", "An email has been sent to your email address with further instructions.");
        } else if (type.equals("phone")) {
            Account_User u = Account_User.getByPhone(user.Phone);
            if (u == null) {
                model.addFlashAttribute("error", "No account with this phone number exists.");
                return "redirect:/auth/v1/resetpassword";
            }

            model.addFlashAttribute("success", "An SMS has been sent to your phone number with further instructions.");
        }
        return "redirect:/auth/v1/resetpassword";
    }


    @GetMapping("/newpassword")
    public String newPassword(RedirectAttributes model, @RequestParam String token) {
        Account_EmailVerification vToken = Account_EmailVerification.getByToken(token);
        if (vToken == null) {
            model.addFlashAttribute("error", "The reset code has expired.");
            return "redirect:/auth/v1/resetpassword";
        } else {
            return "auth/v1/newpassword";
        }
    }
    @PostMapping("/newpassword")
    public String newPassword(RedirectAttributes model, @RequestParam String token, @RequestParam String password1, @RequestParam String password2) {
        Account_EmailVerification vToken = Account_EmailVerification.getByToken(token);
        if (vToken == null) {
            model.addAttribute("error", "The reset code has expired.");
            return "redirect:/auth/v1/resetpassword";
        } else {
            if (!password1.equals(password2)) {
                model.addAttribute("error", "The new password doesn't match.");
                return "redirect:/auth/v1/newpassword?token=" + token;
            }
            vToken.getUser().setPasswordHash(passwordEncoder.encode(password1));
            if (!validatePassword(password1)) {
                model.addAttribute("error", "Password should be of minimum 8 of length and contain at least 1 digit, symbol, uppercase & lowercase character.");
                return "redirect:/auth/v1/newpassword?token=" + token;
            }
            vToken.getUser().UpdateOnly("Password");
            new Account_Notification(vToken.getUser().getID(), "Your password has been reset!", "Your password has been reset successfully.");
            vToken.Delete();
            return "redirect:/auth/v1/login?newpassword";
        }
    }


    public static boolean validatePassword(String password) {
        if (password.length() < 8) return false;
        boolean hasSymbol = false;
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasNumber = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasNumber = true;
            else if (!Character.isLetterOrDigit(c)) hasSymbol = true;
        }
        return hasSymbol && hasUpper && hasLower && hasNumber;
    }
    public static void clearFailedLogins(String email) {
        Account_User U = SolarDBManager.getWhere(Account_User.class, "Email = ? OR (Enabled = ? AND Verified = ?)", email, false, false).orElse(null);
        if (U != null) U.Delete();
    }

    public static class UserModel {
        public Long ID;
        public String FirstName;
        public String LastName;
        public String Address;
        public Gender Gender;
        public String Email;
        public String Password;
        public String Phone;
        public LocalDate DateOfBirth;

        public Long getID() {
            return ID;
        }
        public void setID(Long ID) {
            this.ID = ID;
        }

        public String getFirstName() {
            return FirstName;
        }
        public void setFirstName(String firstName) {
            FirstName = firstName;
        }

        public String getLastName() {
            return LastName;
        }
        public void setLastName(String lastName) {
            LastName = lastName;
        }

        public String getAddress() {
            return Address;
        }
        public void setAddress(String address) {
            Address = address;
        }

        public Gender getGender() {
            return Gender;
        }
        public void setGender(Gender gender) {
            Gender = gender;
        }

        public String getEmail() {
            return Email;
        }
        public void setEmail(String email) {
            Email = email;
        }

        public String getPassword() {
            return Password;
        }
        public void setPassword(String password) {
            Password = password;
        }

        public String getPhone() {
            return Phone;
        }
        public void setPhone(String phone) {
            Phone = phone;
        }

        public LocalDate getDateOfBirth() {
            return DateOfBirth;
        }
        public void setDateOfBirth(LocalDate dateOfBirth) {
            DateOfBirth = dateOfBirth;
        }
    }
}
