package PSaPP.util;
/*
Copyright (c) 2010, The Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of the Regents of the University of California nor the names of its contributors may be
used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.util.*;
import java.text.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;
import PSaPP.pred.*;
import PSaPP.dbase.*;

public class Util {

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; ++i) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    public static boolean sendEmail(String[] to, String[] cc, String subject, String body, String[] attachments) throws Exception {
        String host = "";
        String username = "";
        String password = "";
        String sender = "";
        String useTLS = "";
        if (host.isEmpty() || username.isEmpty() || password.isEmpty() || sender.isEmpty() || useTLS.isEmpty()) {
            try {
                host = ConfigSettings.getSetting(ConfigKey.Setting.SMTP_SERVER);
                username = ConfigSettings.getSetting(ConfigKey.Setting.SMTP_LOGIN);
                password = ConfigSettings.getSetting(ConfigKey.Setting.SMTP_PASSWORD);
                sender = ConfigSettings.getSetting(ConfigKey.Setting.EMAIL_SENDER);
                useTLS = ConfigSettings.getSetting(ConfigKey.Setting.SMTP_USE_TLS);
                useTLS = useTLS.toLowerCase();
            } catch (Exception e) {
                throw e;
            }
        }
        if (body == null && attachments == null || to == null) {
            Logger.error("Invalid null argument passed to Util.sendEmail");
            return false;
        }
        String toRecipients = "", ccRecipients = "";
        for (int i = 0; i < to.length; ++i) {
            toRecipients = (i == 0) ? to[i] : toRecipients + ',' + to[i];
        }
        if (cc != null) {
            for (int i = 0; i < cc.length; ++i) {
                ccRecipients = (i == 0) ? cc[i] : ccRecipients + ',' + cc[i];
            }
        }

        Properties properties = new Properties();
        properties.put("mail.transport.protocol", "smtp");
        if (useTLS.equals("1") || useTLS.equals("yes") || useTLS.equals("true")) {
            properties.put("mail.smtp.starttls.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "false");
        }
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.auth", "true");
        Authenticator authenticator = new PMaCAuthenticator(username, password);
        Session session = Session.getDefaultInstance(properties, authenticator);
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender));
            message.setRecipients(Message.RecipientType.TO, new InternetAddress().parse(toRecipients));
            message.setRecipients(Message.RecipientType.CC, new InternetAddress().parse(ccRecipients));
            message.setSubject(subject);
            if (attachments != null) {
                Multipart multipart = new MimeMultipart();
                if (body != null) {
                    BodyPart messageBodyPart = new MimeBodyPart();
                    messageBodyPart.setText(body);
                    multipart.addBodyPart(messageBodyPart);
                }
                for (int i = 0; i < attachments.length; ++i) {
                    String attachment = attachments[i];
                    BodyPart messageBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(attachment);
                    messageBodyPart.setDataHandler(new DataHandler(source));
                    String filename = attachment.indexOf('/') != -1 ? attachment.substring(attachment.lastIndexOf('/') + 1) : attachment;
                    messageBodyPart.setFileName(filename);
                    multipart.addBodyPart(messageBodyPart);
                }
                message.setContent(multipart);
            } else {
                message.setText(body);
            }
            Transport.send(message);
        } catch (Exception e) {
            Logger.error("Exception while creating/sending email message " + subject + " " + e);
            e.printStackTrace();
            throw e;
        }
        return true;
    }
}

class PMaCAuthenticator extends javax.mail.Authenticator {

    private String username;
    private String password;

    public PMaCAuthenticator(String name, String pwd) {
        username = name;
        password = pwd;
    }

    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, password);
    }
}
