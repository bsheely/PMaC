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

    public static String cleanWhiteSpace(String s, boolean all) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (all) {
            s = s.replaceAll("\\s+", "");
        }
        return s;
    }

    public static String cleanWhiteSpace(String s) {
        return cleanWhiteSpace(s, true);
    }

    public static String cleanComment(String s) {
        if (s == null) {
            return null;
        }
        s = s.replaceAll("#.*$", "");
        return s;
    }

    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (i == 0 && (c == '-')) {
                continue;
            }
            if (c == '.') {
                continue;
            }
            if ((c >= '0') && (c <= '9')) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static String convertKbToBytes(String s) {
        int bytes = Integer.parseInt(s.substring(0, s.indexOf("K")));
        bytes *= 1024;
        return String.valueOf(bytes);
    }

    public static String convertBytesToKb(String s) {
        int kb = Integer.parseInt(s) / 1024;
        return String.valueOf(kb) + "KB";
    }

    public static String makeKey(String src) {
        return ("_" + src + "_");
    }

    public static String genKeyString(Object[] strs, int sidx, int cnt) {
        String ret = "_";
        assert (sidx >= 0);
        if (strs != null) {
            for (int i = 0; i < cnt; i++) {
                if ((sidx + i) >= strs.length) {
                    break;
                }
                ret += (strs[sidx + i] + "_");
            }
        }
        return ret;
    }

    public static Integer toInteger(String value) {
        Integer ret = null;
        try {
            ret = new Integer(value);
        } catch (Exception e) {
            Logger.warn(value + " is not an integer");
        }
        return ret;
    }

    public static Long toLong(String value) {
        Long ret = null;
        try {
            ret = new Long(value);
        } catch (Exception e) {
            Logger.warn(value + " is not a long");
        }
        return ret;
    }

    public static Float toFloat(String value) {
        Float ret = null;
        try {
            ret = new Float(value);
        } catch (Exception e) {
            Logger.warn(value + " is not a float");
        }
        return ret;
    }

    public static Double toDouble(String value) {
        Double ret = null;
        try {
            ret = new Double(value);
        } catch (Exception e) {
            Logger.warn(value + " is not a double");
        }
        return ret;
    }

    public static Object[] stringsToRecord(String str, String signature) {
        String[] fields = str.split("\\s+");
        return stringsToRecord(fields, Database.InvalidDBID, signature);
    }

    public static Object[] stringsToRecord(String[] fields, String signature) {
        return stringsToRecord(fields, Database.InvalidDBID, signature);
    }

    public static Object[] stringsToRecord(String[] fields, int dbid, String signature) {
        if ((fields == null) || (signature == null)) {
            return null;
        }
        assert ((dbid == Database.InvalidDBID) || (dbid >= 0));

        if (fields.length != signature.length()) {
            Logger.warn("Can not make record for " + signature
                    + ". Tokens has incorrect number of fields " + fields.length);
            return null;
        }
        assert (fields.length == signature.length());

        Object[] tokens = null;
        if (dbid != Database.InvalidDBID) {
            tokens = new Object[fields.length + 1];
        } else {
            tokens = new Object[fields.length];
        }
        for (int i = 0; i < fields.length; i++) {
            Object obj = null;
            char sig = signature.charAt(i);
            switch (sig) {
                case 's':
                    obj = fields[i];
                    break;
                case 'i':
                    obj = toInteger(fields[i]);
                    break;
                case 'f':
                    obj = toFloat(fields[i]);
                    break;
                case 'l':
                    obj = toLong(fields[i]);
                    break;
                case 'd':
                    obj = toDouble(fields[i]);
                    break;
                default:
                    obj = null;
            }
            if (obj == null) {
                return null;
            }
            tokens[i] = obj;
        }
        if (dbid != Database.InvalidDBID) {
            tokens[fields.length] = new Integer(dbid);
        }
        return tokens;
    }

    public static boolean recordToFile(Object[] tokens, String signature, DataOutputStream outStream) {
        if ((tokens == null) || (signature == null) || (outStream == null)) {
            return false;
        }
        if (tokens.length != (signature.length() + 1)) {
            Logger.warn("Signature " + signature + " size is different than record size "
                    + tokens.length);
            return false;
        }
        try {
            for (int i = 0; i < (tokens.length - 1); i++) {
                char sig = signature.charAt(i);
                switch (sig) {
                    case 's': {
                        String val = (String) tokens[i];
                        byte[] bytes = val.getBytes();
                        outStream.writeInt(bytes.length);
                        outStream.write(bytes, 0, bytes.length);
                        break;
                    }
                    case 'i': {
                        Integer val = (Integer) tokens[i];
                        outStream.writeInt(val.intValue());
                        break;
                    }
                    case 'l': {
                        Long val = (Long) tokens[i];
                        outStream.writeLong(val.longValue());
                        break;
                    }
                    case 'f': {
                        Float val = (Float) tokens[i];
                        outStream.writeFloat(val.floatValue());
                        break;
                    }
                    case 'd': {
                        Double val = (Double) tokens[i];
                        outStream.writeDouble(val.doubleValue());
                        break;
                    }
                    default:
                        return false;
                }
            }
            Integer dbid = (Integer) tokens[tokens.length - 1];
            outStream.writeInt(dbid.intValue());

        } catch (Exception e) {
            Logger.error("Can not write record back to file\n" + e);
            return false;
        }
        return true;
    }

    public static Object[] fileToRecord(String signature, DataInputStream inpStream) {
        return fileToRecord(signature, inpStream, true);
    }

    public static Object[] fileToRecord(String signature, DataInputStream inpStream, boolean readDbid) {
        if ((signature == null) || (inpStream == null)) {
            return null;
        }
        Object[] tokens = null;
        if (readDbid) {
            tokens = new Object[signature.length() + 1];
        } else {
            tokens = new Object[signature.length()];
        }
        try {
            for (int i = 0; i < signature.length(); i++) {
                char sig = signature.charAt(i);
                switch (sig) {
                    case 's': {
                        int size = inpStream.readInt();
                        byte[] bytes = new byte[size];
                        int cnt = inpStream.read(bytes, 0, size);
                        assert (size == cnt);
                        tokens[i] = new String(bytes);
                        break;
                    }
                    case 'i': {
                        tokens[i] = new Integer(inpStream.readInt());
                        break;
                    }
                    case 'l': {
                        tokens[i] = new Long(inpStream.readLong());
                        break;
                    }
                    case 'f': {
                        tokens[i] = new Float(inpStream.readFloat());
                        break;
                    }
                    case 'd': {
                        tokens[i] = new Double(inpStream.readDouble());
                        break;
                    }
                    default:
                        return null;
                }
            }
            if (readDbid) {
                tokens[signature.length()] = new Integer(inpStream.readInt());
            }
        } catch (Exception e) {
            Logger.warn("Can not read the records form file\n" + e);
            return null;
        }
        return tokens;
    }

    public static Object[] duplicate(Object[] arr) {
        if (arr == null) {
            return null;
        }
        return duplicate(arr, 0, arr.length);
    }

    public static Object[] duplicate(Object[] arr, int startIdx) {
        if (arr == null) {
            return null;
        }
        return duplicate(arr, startIdx, arr.length - startIdx);
    }

    public static Object[] duplicate(Object[] arr, int startIdx, int len) {
        if ((arr == null) || (startIdx >= arr.length)) {
            return null;
        }
        int checkCnt = arr.length - startIdx;
        if (checkCnt < len) {
            len = checkCnt;
        }
        Object[] retValue = new Object[len];
        for (int i = 0; i < len; i++) {
            retValue[i] = arr[i + startIdx];
        }
        return retValue;
    }

    public static String getMemoryProfileSignature(String type, Integer levelCount) {
        if (levelCount == null) {
            return null;
        }
        if ((levelCount.intValue() < 1) || (levelCount.intValue() > 3)) {
            Logger.warn(levelCount + " level count is not valid");
            return null;
        }
        int cnt = 0;
        if (type.equals("BWstretchedExp") || type.equals("ti09")) {
            cnt = levelCount.intValue() * 3;
        } else if (type.equals("BWstretchedHit")) {
            cnt = (levelCount.intValue() * 3) + (levelCount.intValue() - 1);
        } else if (type.equals("BWstretchedEMult")) {
            cnt = (levelCount.intValue() * 2) + 1;
        } else if (type.equals("BWstretchedPen")) {
            cnt = (levelCount.intValue() * 3) + 1 + (levelCount.intValue() - 1);
        } else if (type.equals("BWcyclesDrop")) {
            cnt = (levelCount.intValue() + 1);
        } else if (type.equals("BWexppenDrop")) {
            cnt = (levelCount.intValue() + 1) * 2 + 4;
        }
        if (cnt == 0) {
            return null;
        }
        String retValue = "";
        for (int i = 0; i < cnt; i++) {
            retValue += "d";
        }
        retValue += "s";
        return retValue;
    }

    public static String[] makeArray(String value) {
        if (value == null) {
            return null;
        }
        String[] retValue = new String[1];
        retValue[0] = value;
        return retValue;
    }

    public static Object[] makeArray(Object value) {
        if (value == null) {
            return null;
        }
        Object[] retValue = new Object[1];
        retValue[0] = value;
        return retValue;
    }

    public static Object[] concatArray(Object[] arr1, Object newValue) {
        Object[] arr2 = new Object[1];
        arr2[0] = newValue;
        return concatArray(arr1, arr2);
    }

    public static Object[] concatArray(Object[] arr1, Object[] arr2) {
        int cnt = 0;
        if (arr1 != null) {
            cnt += arr1.length;
        }
        if (arr2 != null) {
            cnt += arr2.length;
        }
        if (cnt == 0) {
            return null;
        }

        int idx = 0;
        Object[] retValue = new Object[cnt];
        if (arr1 != null) {
            for (int i = 0; i < arr1.length; i++) {
                retValue[idx++] = arr1[i];
            }
        }
        if (arr2 != null) {
            for (int i = 0; i < arr2.length; i++) {
                retValue[idx++] = arr2[i];
            }
        }
        assert (cnt == retValue.length);
        return retValue;
    }

    public static Object[] fileTimeTuple(String strVal) {
        String[] tokens = strVal.split(",");
        if (tokens.length == 2) {
            Object tmpStr = LinuxCommand.pathExists(Util.cleanWhiteSpace(tokens[0]));
            Object tmpFlt = Util.toDouble(Util.cleanWhiteSpace(tokens[1]));
            if ((tmpStr != null) && (tmpFlt != null)) {
                Object[] retValue = new Object[2];
                retValue[0] = tmpStr;
                retValue[1] = tmpFlt;
                return retValue;
            }
        }
        return null;
    }

    public static Integer[] machineList(String strVal) {
        List list = new ArrayList();
        if (strVal != null) {
            strVal = Util.cleanWhiteSpace(strVal);
            String[] values = strVal.split(",");
            for (int i = 0; i < values.length; i++) {
                String current = values[i];
                Integer beginValue = null;
                Integer endValue = null;
                try {
                    if (current.matches("\\d+")) {
                        beginValue = new Integer(current);
                        endValue = beginValue;
                    } else if (current.matches("\\d+\\-\\d+")) {
                        String[] begend = current.split("-");
                        assert (begend.length == 2);
                        beginValue = new Integer(begend[0]);
                        endValue = new Integer(begend[1]);
                    }
                } catch (Exception e) {
                    Logger.warn(values[i] + " is not in right format for machinelist\n" + e);
                    beginValue = null;
                    endValue = null;
                }

                if (beginValue == null) {
                    Logger.warn("Begin value is invalid at " + current);
                    return null;
                }

                assert (beginValue != null) && (endValue != null);

                if ((beginValue.intValue() < 0) || (endValue.intValue() < 0)) {
                    Logger.warn("Begin/end value is invalid since negative at " + current);
                    return null;
                }
                if (beginValue.intValue() > endValue.intValue()) {
                    Logger.warn("Value is invalid since begin is greater than end at " + current);
                    return null;
                }

                for (int j = beginValue.intValue(); j <= endValue.intValue(); j++) {
                    list.add(new Integer(j));
                }
            }
        }

        if (list.isEmpty()) {
            return null;
        }

        Integer[] retValue = new Integer[list.size()];
        Iterator it = list.iterator();
        int i = 0;
        while (it.hasNext()) {
            retValue[i++] = (Integer) it.next();
        }

        return retValue;
    }

    public static Object[] phaseListValue(String strVal) {
        HashSet phases = new HashSet();
        String[] tokens = strVal.split(",");
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].matches("p\\d+")) {
                return null;
            }
            int len = tokens[i].length();
            int idx = tokens[i].indexOf('p');
            do {
                idx++;
            } while (tokens[i].charAt(idx) == '0');

            String phaseId = new String(tokens[i].toCharArray(), idx, len - idx);

            phases.add(phaseId);
        }
        if (phases.size() > 0) {
            Integer[] retValue = new Integer[phases.size()];
            Iterator it = phases.iterator();
            int i = 0;
            while (it.hasNext()) {
                retValue[i++] = new Integer((String) it.next());
            }
            return retValue;
        }
        return null;
    }

    public static Object[] phaseListTimeValue(String strVal) {
        HashMap phases = new HashMap();
        String[] tokens = strVal.split(",");
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].matches("p\\d+=[\\d.]+")) {
                return null;
            }
            int len = tokens[i].length();
            int idx1 = tokens[i].indexOf('p');
            do {
                idx1++;
            } while (tokens[i].charAt(idx1) == '0');
            int idx2 = tokens[i].indexOf('=');

            String phaseId = new String(tokens[i].toCharArray(), idx1, idx2 - idx1);
            Double phaseTime = new Double(new String(tokens[i].toCharArray(), idx2 + 1, len - idx2 - 1));

            phases.put(phaseId, phaseTime);
        }
        if (phases.size() > 0) {
            Object[] retValue = new Object[phases.size() * 2];
            Iterator it = phases.keySet().iterator();
            int i = 0;
            while (it.hasNext()) {
                String phaseId = (String) it.next();
                retValue[i++] = new Integer(phaseId);
                retValue[i++] = phases.get(phaseId);
            }
            return retValue;
        }
        return null;
    }

    public static boolean pathExists(String path) {
        return (LinuxCommand.pathExists(path) != null);
    }

    public static boolean isFile(String path) {
        return LinuxCommand.isFile(path);
    }

    public static boolean isDirectory(String path) {
        return LinuxCommand.isDirectory(path);
    }

    public static boolean mkdir(String path) {
        return LinuxCommand.mkdir(path);
    }

    public static boolean mkdirs(String[] paths) {
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                boolean status = LinuxCommand.mkdir(paths[i]);
                if (!status) {
                    Logger.warn("Can not make " + paths[i]);
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isValidRound(Integer val) {
        boolean retValue = false;
        if (val != null) {
            return (val.intValue() > 0);
        }
        return retValue;
    }

    public static boolean isValidTaskCount(Integer val) {
        boolean retValue = false;
        if (val != null) {
            return (val.intValue() > 0);
        }
        return retValue;
    }

    public static boolean isValidBWMethod(String mth) {
        boolean retValue = false;
        if (mth != null) {
            for (int i = 0; i < Convolver.validBwMethods.length; i++) {
                if (Convolver.validBwMethods[i].equals(mth)) {
                    retValue = true;
                    break;
                }
            }
        }
        return retValue;
    }

    public static boolean isValidNetworkSim(String sim) {
        boolean retValue = false;
        if (sim != null) {
            for (int i = 0; i < NetworkSim.validNetworkSims.length; i++) {
                if (NetworkSim.validNetworkSims[i].equals(sim)) {
                    retValue = true;
                    break;
                }
            }
        }
        return retValue;
    }

    public static boolean isValidNetworkMod(String sim, String mod) {
        boolean retValue = false;
        if ((sim != null) && (mod != null)) {
            for (int i = 0; i < NetworkSim.validNetworkMods.length; i++) {
                if (NetworkSim.validNetworkMods[i].equals(mod)) {
                    retValue = true;
                    break;
                }
            }
        }
        return retValue;
    }

    public static boolean isValidRatioMethod(String mth) {
        boolean retValue = false;
        if (mth != null) {
            for (int i = 0; i < Convolver.validRatioMethods.length; i++) {
                if (Convolver.validRatioMethods[i].equals(mth)) {
                    retValue = true;
                    break;
                }
            }
        }
        return retValue;
    }

    public static boolean isValidLevelCount(int lvl) {
        return ((lvl >= 1) && (lvl <= 3));
    }

    public static double multMillion(double x) {
        return (x * 1.0e6);
    }

    public static double percentage(double x, double y) {
        if (x < 0.0) {
            x = -1.0 * x;
        }
        if (y < 0.0) {
            y = -1.0 * y;
        }
        double sum = x + y;
        if (sum == 0.0) {
            return -100.0;
        }
        return 100.0 * (x / sum);
    }

    public static String dateToString(Date date) {
        return ("" + DateFormat.getDateInstance(DateFormat.SHORT).format(date));

    }

    public static String SimpleDateString(Date date) {

        SimpleDateFormat sdf = new SimpleDateFormat("MMddyyyy");
        String dateString = sdf.format(date);

        return dateString;
    }

    public static String listToString(Object[] arr) {
        String retValue = "";
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null) {
                    retValue += arr[i];
                }
                retValue += "||";
            }
        }
        return retValue;
    }

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
