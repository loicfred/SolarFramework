package org.solarframework.auth.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthEmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.application.name}")
    private String applicationName;

    public AuthEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String message) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
        System.out.println("Email sent to " + to + " with subject: " + subject + "\n" + message);
    }


    public void sendVerificationEmail(String version, HttpServletRequest req, String to, String token) {
        String subject = "Verify your " + applicationName + " account";
        String verificationUrl = req.getRequestURL().toString().replaceFirst(req.getRequestURI(), "") + "/auth/" + version + "/email/verify?token=" + token;

        String message = "Welcome! Please click the link below to verify your " + applicationName + " account:\n" + verificationUrl;

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
        System.out.println("Email sent to " + to + " with subject: " + subject + "\n" + message);
    }
    public void sendResetPasswordEmail(String version, HttpServletRequest req, String to, String token) {
        String subject = "Reset your " + applicationName + " account password";
        String verificationUrl = req.getRequestURL().toString().replaceFirst(req.getRequestURI(), "") + "/auth/" + version + "/newpassword?token=" + token;

        String message = "Hello dear. To reset your password, please click the link below:\n" + verificationUrl + "\n";
        message += "\nIf you did not request a password reset, please ignore this email.";

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
        System.out.println("Email sent to " + to + " with subject: " + subject + "\n" + message);
    }

    public void sendDeleteConfirmationEmail(String version, HttpServletRequest req, String to, String token) {
        String subject = "Confirm deletion of " + applicationName + " account";
        String verificationUrl = req.getRequestURL().toString().replaceFirst(req.getRequestURI(), "") + "/auth/" + version + "/delete/verify?token=" + token;

        String message = "Welcome! Please click the link below to confirm the deletion of your " + applicationName + " account:\n" + verificationUrl;

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
        System.out.println("Email sent to " + to + " with subject: " + subject + "\n" + message);
    }

}