package ar.edu.uade.medlink.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class RecoveryMailer {
    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final String from;
    private final boolean configured;

    public RecoveryMailer(@Value("${medlink.recovery.mail.host:}") String host,
        @Value("${medlink.recovery.mail.port:587}") int port,
        @Value("${medlink.recovery.mail.username:}") String username,
        @Value("${medlink.recovery.mail.password:}") String password,
        @Value("${medlink.recovery.mail.starttls:true}") boolean starttls,
        @Value("${medlink.recovery.mail.from:}") String from) {
        configured = !host.isBlank() && !from.isBlank();
        this.from = from;
        sender.setHost(host); sender.setPort(port);
        sender.setUsername(username); sender.setPassword(password);
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(starttls));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(starttls));
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
    }

    public boolean configured() { return configured; }

    public void send(String email, String code) {
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("MediFind — Recuperar contraseña");
        message.setText("Tu código para recuperar la contraseña de MediFind es: " + code
            + "\n\nIngresalo en Recuperar contraseña, junto a tu correo y una nueva contraseña."
            + "\nVence en 15 minutos y se puede usar una sola vez."
            + "\nSi no lo solicitaste, ignorá este correo. No compartas el código.");
        sender.send(message);
    }
}
