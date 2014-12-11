package PSaPP.pred;
/*
Copyright (c) 2010, PMaC Laboratories, Inc.
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

import PSaPP.util.*;
import PSaPP.dbase.*;

import java.util.*;
import java.io.*;
import java.text.NumberFormat;
import java.awt.Color;

import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.entity.*;
import org.jfree.chart.labels.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.data.category.*;
import org.jfree.data.general.*;

/**
 * This class processes all .psinsout files in a specified 
 * directory and sends an email message describing the 
 * simulation results contained in the file.
 * @author bsheely
 */
public class Reporter {

    static final String ETASK_TIME_COMMENT = "Load imbalance due to an MPI event is identified when the"
            + " time spent in that event for a given task is significantly different than the"
            + " average for all tasks. Based on this criteria the following MPI events have been"
            + " identified as causing a load imbalance in at least one specific task:";
    static final String HIT_RATE_COMMENT = "The hit rate data for each basic-block was captured using"
            + " runtime cache simulation of the application's memory address stream. Data is assumed"
            + " to be in L1 cache if it has hit rates >= 99.5%, in L2 cache if it has hit rates >= 99.5%,"
            + " and in L3 cache if it has hit rates >= 98.0%";
    static final String imgSrcPath = "";  // not currently set
    static final String subject = "PSiNS Data";
    static final java.awt.Paint bgColor = new java.awt.Color(34, 34, 34);
    static final int NUM_FUNC = 6;
    String[] recipients;
    String[] cc;
    String outputDir;
    String imagesDir;
    String body;
    String simulatedSystem = "";
    String application = "";
    String dataSet = "";
    int machineProfile;
    int cpuCount = 0;
    boolean saveOutput = false;
    ArrayList outputFiles;
    File images;
    Database database;
    PsinsData psinsData;
    BinsData binsData = null;
    FuncData funcData = null;
    TaskData taskData = null;
    HashMap profileData = null;

    /**
     * Constructor
     * @param dir Path to directory which contains the .psinsout files
     * @param testCase Represents a test_case_data row in the database
     * @param db Database from which to draw information about the test_case_data
     */
    public Reporter(String dir, TestCase testCase, Database db) {
        application = testCase.getApplication();
        dataSet = testCase.getDataset();
        cpuCount = testCase.getCpu();
        database = db;
        recipients = getEmailsFromTestCase(testCase);
        cc = ConfigSettings.getSettings(ConfigKey.Settings.EMAIL_CC);
        init(dir);
    }

    /**
     * Set whether or not output files are saved
     * @param save If true, output files are never deleted
     */
    public void setSaveOutput(boolean save) {
        saveOutput = save;
    }
    
    /**
     * Process all .psinsout files
     * @return boolean True if all files successfully processed
     */
    public boolean run() throws Exception {
        if (database == null) {
            Logger.error("Database is null");
            return false;
        }
        try {
            File folder = new File(outputDir);
            File[] files = folder.listFiles();
            if (files == null) {
                Logger.error("Directory " + outputDir + " does not exist");
                return false;
            }
            boolean fileFound = false;
            if (files.length == 0) {
                Logger.warn("No files found in directory " + outputDir);
            }
            for (int i = 0; i < files.length; ++i) {
                if (files[i].isFile()) {
                    String filename = files[i].getName();
                    if (filename.endsWith(".psinsout")) {
                        fileFound = true;
                        if (!processPsinsFile(filename, true)) {
                            return false;
                        }
                        processStats();
                        outputFiles = new ArrayList();
                        if (!createTextFile(filename.substring(0, filename.indexOf(".psinsout")))) {
                            return false;
                        }
                        if (!createHTMLFile(filename.substring(0, filename.indexOf(".psinsout")))) {
                            return false;
                        }
                        if (recipients != null) {
                            setEmailBody();
                            String[] attachments = new String[outputFiles.size()];
                            outputFiles.toArray(attachments);
                            if (!Util.sendEmail(recipients, cc, subject, body, attachments)) {
                                return false;
                            }
                        }
                    }
                }
            }
            if (!fileFound) {
                Logger.warn("No .psinsout file found in directory " + outputDir);
            }
        } catch (Exception e) {
            throw e;
        }
        if (!saveOutput) {
            deleteOutputFiles();
        }
        return true;
    }

    /**
     * Process only the .psinsout files that contain the specified profiles and
     * only generate a report for the profile at index zero
     * @param profiles Machine profiles part of the same prediction run
     * @return boolean True if all files successfully processed
     */
    public boolean run(int[] profiles) throws Exception {
        if (profiles.length == 0) {
            Logger.error("At least 1 machine profile is required for reporting");
        }
        if (profiles.length == 1) {
            return processProfiles(profiles);
        }
        for (int i = 0; i < profiles.length; ++i) {
            int[] copy = Arrays.copyOf(profiles, profiles.length);
            int head = copy[0];
            copy[0] = copy[i];
            copy[i] = head;
            if (!processProfiles(copy)) {
                return false;
            }
        }
        if (!saveOutput) {
            deleteOutputFiles();
        }
        return true;
    }

    private void deleteOutputFiles() {
        for (int i = 0; i < outputFiles.size(); ++i) {
            (new File((String) outputFiles.get(i))).delete();
        }
        Util.deleteDir(images);
    }

    private boolean processProfiles(int[] profiles) throws Exception {
        if (database == null) {
            Logger.error("Database is null");
            return false;
        }
        try {
            File folder = new File(outputDir);
            File[] files = folder.listFiles();
            if (files == null) {
                Logger.error("Directory " + outputDir + " does not exist");
                return false;
            }
            if (files.length == 0) {
                Logger.error("No files found in directory " + outputDir);
                return false;
            }
            profileData = new LinkedHashMap();
            String[] psinsFiles = new String[profiles.length];
            for (int i = 0; i < profiles.length; ++i) {
                for (int j = 0; j < files.length; ++j) {
                    if (files[j].isFile()) {
                        String filename = files[j].getName();
                        if (filename.endsWith(".psinsout") && profiles[i] == getMachineProfile(filename)) {
                            psinsFiles[i] = filename;
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < psinsFiles.length; ++i) {
                if (psinsFiles[i] == null) {
                    if (i == 0) {
                        return false;
                    } else {
                        Logger.warn("Unable to process profile " + String.valueOf(profiles[i]));
                        continue;
                    }
                }
                if (!processPsinsFile(psinsFiles[i], false)) {
                    Logger.warn("Unable to process profile " + String.valueOf(profiles[i]));
                    continue;
                }
                profileData.put(new Integer(getMachineProfile(psinsFiles[i])), psinsData);
            }
            if (!processPsinsFile(psinsFiles[0], true)) {
                return false;
            }
            processStats();
            outputFiles = new ArrayList();
            String file = psinsFiles[0];
            if (!createTextFile(file.substring(0, file.indexOf(".psinsout")))) {
                return false;
            }
            if (!createHTMLFile(file.substring(0, file.indexOf(".psinsout")))) {
                return false;
            }
            if (recipients != null) {
                setEmailBody();
                String[] attachments = new String[outputFiles.size()];
                outputFiles.toArray(attachments);
                if (!Util.sendEmail(recipients, cc, subject, body, attachments)) {
                    return false;
                }
            }
        } catch (Exception e) {
            throw e;
        }
        return true;
    }

    private int getMachineProfile(String filename) throws Exception {
        try {
            String file = outputDir + filename;
            FileReader fileReader = new FileReader(new File(file));
            LineNumberReader reader = new LineNumberReader(fileReader);
            String line;
            while ((line = reader.readLine()) != null) {
                if (reader.getLineNumber() == 4) {
                    StringTokenizer tokenizer = new StringTokenizer(line);
                    String token = "";
                    while (tokenizer.hasMoreTokens()) {
                        token = tokenizer.nextToken();
                    }
                    if (Character.isDigit(token.charAt(0))) {
                        return Integer.parseInt(token);
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }
        Logger.warn("Machine profile does not exist in " + filename);
        return 0;
    }

    private Reporter(String dir, String email, boolean save) {
        recipients = (email != null) ? email.split(",") : null;
        cc = null;
        saveOutput = save;
        database = new Postgres();
        if (!database.initialize()) {
            Logger.warn("Cannot initialize the database");
        }
        init(dir);
    }

    private String[] getEmailsFromTestCase(TestCase testCase) {
        if (testCase == null) {
            Logger.warn("No test case available to reporter; no users found");
            return null;
        }
        if (database != null) {
            TreeMap tuples = database.getTestCaseUsers(testCase);
            if (tuples != null) {
                Set unqEmails = new HashSet();
                Iterator it = tuples.keySet().iterator();
                while (it.hasNext()) {
                    String key = (String) it.next();
                    String val = (String) tuples.get(key);
                    unqEmails.add(val);
                }
                return (String[]) unqEmails.toArray(new String[unqEmails.size()]);
            } else {
                return null;
            }
        } else {
            Logger.warn("no database available to reporter; no users found");
        }
        return null;
    }

    private void init(String dir) {
        if (!dir.endsWith("/")) {
            dir += "/";
        }
        outputDir = dir;
        imagesDir = outputDir + "images/";
        images = new File(imagesDir);
        images.mkdir();
    }

    private boolean run(String file) throws Exception {
        if (database == null) {
            Logger.error("Database is null");
            return false;
        }
        try {
            if (file != null) {
                if (!processPsinsFile(file, true)) {
                    return false;
                }
                processStats();
                outputFiles = new ArrayList();
                if (!createTextFile(file.substring(0, file.indexOf(".psinsout")))) {
                    return false;
                }
                if (!createHTMLFile(file.substring(0, file.indexOf(".psinsout")))) {
                    return false;
                }
                if (recipients != null) {
                    setEmailBody();
                    String[] attachments = new String[outputFiles.size()];
                    outputFiles.toArray(attachments);
                    if (!Util.sendEmail(recipients, cc, subject, body, attachments)) {
                        return false;
                    }
                }
            } else {
                return run();
            }
        } catch (Exception e) {
            throw e;
        }
        if (!saveOutput) {
            deleteOutputFiles();
        }
        return true;
    }

    private boolean processPsinsFile(String filename, boolean reporting) throws Exception {
        try {
            PsinsParser parser = new PsinsParser();
            String file = outputDir + filename;
            if (reporting) {
                Logger.inform("Processing report for " + filename);
            }
            FileReader fileReader = new FileReader(new File(file));
            LineNumberReader reader = new LineNumberReader(fileReader);
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(line);
                String token = "";
                while (tokenizer.hasMoreTokens()) {
                    token = tokenizer.nextToken();
                }
                if (reporting && reader.getLineNumber() == 1 && application.isEmpty()) {
                    application = token;
                } else if (reporting && reader.getLineNumber() == 2 && dataSet.isEmpty()) {
                    dataSet = token;
                } else if (reader.getLineNumber() == 3) {
                    if (Character.isDigit(token.charAt(0))) {
                        count = Integer.parseInt(token);
                    }
                } else if (reader.getLineNumber() == 4) {
                    if (Character.isDigit(token.charAt(0))) {
                        machineProfile = Integer.parseInt(token);
                    }
                } 
            }
            if (reporting) {
                if (cpuCount == 0) {
                    cpuCount = count;
                }
                simulatedSystem = database.getBaseResourceName(machineProfile);
            }
            psinsData = new PsinsData(count);
            if (!parser.parse(file, psinsData)) {
                return false;
            }
        } catch (Exception e) {
            throw e;
        }
        return true;
    }

    private boolean processStats() throws Exception {
        String fileStart = "sysid" + String.valueOf(database.getCacheSysId(machineProfile)) + "_";
        String fileEnd = "_" + Format.BR(database.getBaseResource(machineProfile)) + "_" + Format.MP(database.getMemoryPIdx(machineProfile));
        try {
            File folder = new File(outputDir + "stats/");
            File[] files = folder.listFiles();
            if (files == null) {
                Logger.inform("Directory " + outputDir + "stats/ does not exist");
                return true;
            }
            if (files.length == 0) {
                Logger.warn("No files found in directory " + outputDir + "/stats");
                return true;
            }
            boolean binsFileFound = false;
            boolean funcFileFound = false;
            boolean taskFileFound = false;
            for (int i = 0; i < files.length; ++i) {
                if (files[i].isFile()) {
                    String filename = files[i].getName();
                    if (!binsFileFound && filename.startsWith(fileStart) && filename.endsWith(fileEnd + ".bins")) {
                        binsFileFound = true;
                        BinsParser parser = new BinsParser();
                        binsData = new BinsData();
                        if (!parser.parse(outputDir + "stats/" + filename, binsData)) {
                            return false;
                        }
                    } else if (!funcFileFound && filename.startsWith(fileStart) && filename.endsWith(fileEnd + ".func")) {
                        funcFileFound = true;
                        FuncParser parser = new FuncParser();
                        funcData = new FuncData();
                        if (!parser.parse(outputDir + "stats/" + filename, funcData)) {
                            return false;
                        }
                    } else if (!taskFileFound && filename.startsWith(fileStart) && filename.endsWith(fileEnd + ".task")) {
                        taskFileFound = true;
                        TaskParser parser = new TaskParser();
                        taskData = new TaskData();
                        if (!parser.parse(outputDir + "stats/" + filename, taskData)) {
                            return false;
                        }
                    }
                }
            }
            if (!binsFileFound) {
                Logger.warn("Corresponding .bins file not found in directory " + outputDir + "/stats");
            }
            if (!funcFileFound) {
                Logger.warn("Corresponding .func file not found in directory " + outputDir + "/stats");
            }
            if (!taskFileFound) {
                Logger.warn("Corresponding .task file not found in directory " + outputDir + "/stats");
            }
        } catch (Exception e) {
            throw e;
        }
        return true;
    }

    private boolean createTextFile(String filename) throws Exception {
        try {
            outputFiles.add(outputDir + filename + ".txt");
            File file = new File(outputDir, filename + ".txt");
            file.createNewFile();
            FileWriter fstream = new FileWriter(file);
            PrintWriter out = new PrintWriter(fstream);
            Iterator iterator;
            if (application != null) {
                out.println("Application: " + application);
            }
            if (dataSet != null) {
                out.println("Data Set: " + dataSet);
            }
            if (cpuCount != 0) {
                out.println("CPU Count: " + String.valueOf(cpuCount));
            }
            if (simulatedSystem != null) {
                out.println("Simulated System: " + simulatedSystem);
            }
            if (profileData != null) {                                                             //machine profile table
                Set set = profileData.entrySet();
                Iterator itr = set.iterator();
                out.println("\nMachine\tPredicted Runtime\t% Communication");
                out.println("-------\t-----------------\t---------------");
                while (itr.hasNext()) {
                    Map.Entry entry = (Map.Entry) itr.next();
                    PsinsData data = (PsinsData) entry.getValue();
                    double percentComm = data.totalCommunicationTime
                            / (data.totalCommunicationTime + data.totalComputationTime) * 100;
                    out.println(database.getMachineLabel(((Integer) entry.getKey()).intValue()) + "\t"
                            + String.valueOf(Format.format2d(data.totalPredictionTime)) + "\t"
                            + String.valueOf((int) percentComm) + "%");
                }
            }
            out.println("\nProcessing and Communication Time As Percentage of Total");             //Etime data
            out.println("--------------------------------------------------------");
            iterator = psinsData.etimes.iterator();
            while (iterator.hasNext()) {
                Event event = (Event) iterator.next();
                if (event.value > 0) {
                    //String output = String.format("%s \t  %5.2f", event.eventType, event.value);
                    String output = event.eventType + "\t" + String.valueOf(event.value);
                    out.println(output);
                }
            }
            if (funcData != null) {                                                                //function times
                out.println("\nFunctions Calls With Most Processing Time");
                out.println("-----------------------------------------");
                iterator = funcData.funcTimes.iterator();
                int count = 1;
                while (iterator.hasNext() && count <= 5) {
                    FuncTime func = (FuncTime) iterator.next();
                    out.println(func.name + "\t" + Format.format2d(func.time));
                    ++count;
                }
            }
            if (binsData != null) {                                                                //hit rates
                out.println("\nHit Rates");
                out.println("---------");
                double total = binsData.timeL1 + binsData.timeL2 + binsData.timeL3 + binsData.timeMM;
                out.println("L1 - " + String.valueOf((int) (binsData.timeL1 / total * 100)) + "%");
                if (binsData.timeL2 > 0) {
                    out.println("L2 - " + String.valueOf((int) (binsData.timeL2 / total * 100)) + "%");
                }
                if (binsData.timeL3 > 0) {
                    out.println("L3 - " + String.valueOf((int) (binsData.timeL3 / total * 100)) + "%");
                }
                out.println("Main memory - " + String.valueOf((int) (binsData.timeMM / total * 100)) + "%");
            }
            if (taskData != null) {                                                                //task time stats
                out.println("\nTask Time Stats");
                out.println("---------------");
                out.println("Min - " + Format.format2d(taskData.min));
                out.println("Max - " + Format.format2d(taskData.max));
                out.println("Mean - " + Format.format2d(taskData.avg));
                out.println("Standard Deviation - " + Format.format2d(taskData.standardDeviation));
            }
            out.close();
        } catch (Exception e) {
            Logger.error("Exception while creating text file " + filename + " " + e);
            throw e;
        }
        return true;
    }

    private boolean createHTMLFile(String filename) throws Exception {
        //ETaskTime data will not exist if the --brief_results flag was passed to PSiNS
        boolean ETaskTimeData = psinsData.taskEventTimes[0] != null ? true : false;
        try {    	                                                                           // create HTML file
            outputFiles.add(outputDir + filename + ".html");
            File file = new File(outputDir, filename + ".html");
            file.createNewFile();
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            PrintWriter writer = new PrintWriter(out);
            writer.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("<title>PMaC Tools Automated Modeling Analysis></title>");
            writer.println("<link rel=\"stylesheet\" href=\"pmac-tools-report.css\">");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("<div id=\"container\">");
            writer.println("<div id=\"intro\">");
            writer.println("<div id=\"logoheader\">");
            writer.println("<a href=\"http://www.pmaclabs.com/\"> <img src=\"pmac_logo_whitebg.gif\" width=\"530\" height=\"82\"  alt=\"PMaC Laboratories, Inc.\"></a>");
            writer.println("<a href=\"http://pettt-ace.com/\"> <img src=\"ace_logo.png\" width=\"63\" height=\"80\"  alt=\"Pettt-Ace\"></a>");
            writer.println("</div>");
            writer.println("<div id=\"testcase\">");                                               //Test Case Summary
            writer.println("<h1>Test Case Summary - " + simulatedSystem + "</h1>");
            writer.println("<table>");
            writer.println("<tr>");
            writer.println("<td><b>Application:</b>" + application + "</td>");
            writer.println("<td><b>Data Set:</b>" + dataSet + "</td>");
            writer.println("<td><b>CPU Count:</b>" + String.valueOf(cpuCount) + "</td>");
            writer.println("</tr>");
            writer.println("</table>");
            writer.println("</div>"); //testcase
            writer.println("</div>"); //intro
            if (profileData != null) {                                                              //Estimated Application Runtime Per System
                writer.println("<div id=\"supportingdata\">");
                writer.println("<a name=\"per_system_time\"></a>");
                writer.println("<h1>Estimated Application Runtime Per System</h1>");
                writer.println("<table>");
                writer.println("<tr>");
                writer.println("<th>Machine</th>");
                writer.println("<th>Runtime</th>");
                writer.println("<th>Communication</th>");
                writer.println("</tr>");
                Set set = profileData.entrySet();
                Iterator itr = set.iterator();
                while (itr.hasNext()) {
                    Map.Entry entry = (Map.Entry) itr.next();
                    PsinsData data = (PsinsData) entry.getValue();
                    double percentComm = data.totalCommunicationTime
                            / (data.totalCommunicationTime + data.totalComputationTime) * 100;
                    writer.println("<tr>");
                    writer.println("<td>" + database.getBaseResourceName(((Integer) entry.getKey()).intValue()) + "</td>");
                    writer.println("<td>" + String.valueOf(Format.format2d(data.totalPredictionTime)) + "</td>");
                    writer.println("<td>" + String.valueOf((int) percentComm) + "%</td>");
                    writer.println("</tr>");
                }
                writer.println("</table>");
                writer.println("</div>");
            }
            writer.println("<div id=\"supportingdata\">");                                         //Total Processing and Message Passing Time
            writer.println("<a name=\"message_passing_overview\"></a>");
            writer.println("<h1>Total Processing and Message Passing Time</h1>");
            writer.println("<table>");
            writer.println("<tr>");
            writer.println("<td><img src=\"" + imgSrcPath + filename + "_etime_piechart.png\" width=\"514px\"></td>");
            writer.println("</tr>");
            writer.println("<tr>");
            writer.println("<td>");
            writer.println("<table>");
            writer.println("<tr>");
            writer.println("<th>MPI Event</th>");
            writer.println("<th>Total Bytes</th>");
            writer.println("<th>Number of Calls</th>");
            writer.println("<th>Avg. Bytes</th>");
            writer.println("</tr>");
            Iterator iter = psinsData.etimes.iterator();
            while (iter.hasNext()) {
                Event event = (Event) iter.next();
                if (event.value >= 1.0) {
                    CommSize commSize = (CommSize) psinsData.commSizes.get(event.eventType);
                    if (commSize != null) {
                        writer.println("<tr>");
                        writer.println("<td>" + event.eventType + "</td>");
                        writer.println("<td>" + String.valueOf(commSize.totalBytes) + "</td>");
                        writer.println("<td>" + String.valueOf(commSize.count) + "</td>");
                        writer.println("<td>" + Format.format2d(commSize.avgBytes) + "</td>");
                        writer.println("</tr>");
                    }
                }
            }
            writer.println("</table>");
            writer.println("</td>");
            writer.println("</tr>");
            writer.println("</table>");
            writer.println("</div>");
            if (ETaskTimeData) {                                                                   //Per-Task Processing and Message Passing Time
                writer.println("<div id=\"supportingdata\">");
                writer.println("<a name=\"message_passing_per_cpu\"></a>");
                writer.println("<h1>Per-Task Processing and Message Passing Time</h1>");
                writer.println("<img src=\"" + imgSrcPath + filename + "_etasktime_barchart.png\" width=\"514px\">");
                if (!psinsData.eTaskTimeComments.isEmpty()) {
                    writer.println("<h3><a title=\"" + ETASK_TIME_COMMENT + "\">Imbalanced</a> MPI Functions:</h3>");
                    String events = "";
                    iter = psinsData.eTaskTimeComments.iterator();
                    int i = 1;
                    while (iter.hasNext()) {
                        events += i != psinsData.eTaskTimeComments.size() ? (String) iter.next() + ", " : (String) iter.next();
                        ++i;
                    }
                    writer.println(events);
                }
                writer.println("</div>");
            }
            if (funcData != null) {                                                                //Functions With Highest Processing Time
                writer.println("<div id=\"supportingdata\">");
                writer.println("<a name=\"function_time_overview\"></a>");
                writer.println("<h1>Functions With Highest Processing Time</h1>");
                writer.println("<img src=\"" + imgSrcPath + filename + "_functime_piechart.png\" width=\"514px\">");
                if (!funcData.funcTimeComments.isEmpty()) {
                    writer.println("<table>");
                    writer.println("<tr>");
                    writer.println("<th>Function Name</th>");
                    writer.println("<th>Run Time</th>");
                    if (funcData.cachelevels >= 1) {
                        writer.println("<th>Avg. L1 Hit rate</th>");
                    }
                    if (funcData.cachelevels >= 2) {
                        writer.println("<th>Avg. L2 Hit rate</th>");
                    }
                    if (funcData.cachelevels >= 3) {
                        writer.println("<th>Avg. L3 Hit rate</th>");
                    }
                    writer.println("</tr>");
                    iter = funcData.funcTimes.iterator();
                    int count = 1;
                    while (iter.hasNext()) {
                        FuncTime func = (FuncTime) iter.next();
                        if (count <= NUM_FUNC && !func.name.contentEquals("<others>")) {
                            writer.println("<tr>");
                            writer.println("<td>" + func.name + "</td>");
                            writer.println("<td>" + Format.format2d(func.time / cpuCount) + "</td>");
                            Iterator itr = func.hitRates.iterator();
                            while (itr.hasNext()) {
                                Double hitrate = (Double) itr.next();
                                writer.println("<td>" + Format.format2d(hitrate.doubleValue()) + "%" + "</td>");
                            }
                            writer.println("</tr>");
                            ++count;
                        }
                    }
                    writer.println("</table>");
                }
                writer.println("</div>");
            }
            if (binsData != null) {                                                                //Analysis of Data Movement
                writer.println("<div id=\"supportingdata\">");
                writer.println("<a name=\"cache_behavior_overview\"></a>");
                writer.println("<h1>Analysis of Data Movement</h1>");
                writer.println("Our analysis shows that your application is spending its time <a title=\"" + HIT_RATE_COMMENT + "\">waiting for memory</a> in the following places:");
                writer.println("<img src=\"" + imgSrcPath + filename + "_hitrates_piechart.png\" width=\"514px\">");
                if (!binsData.comments.isEmpty()) {
                    writer.println("<table>");
                    writer.println("<tr>");
                    writer.println("<th>Cache Level</th>");
                    writer.println("<th>Time Spent</th>");
                    writer.println("<th>% Time</th>");
                    writer.println("</tr>");
                    iter = binsData.comments.iterator();
                    while (iter.hasNext()) {
                        MemoryStats stats = (MemoryStats) iter.next();
                        String level = "Main Mem.";
                        if (stats.cacheLevel == 1) {
                            level = "L1";
                        } else if (stats.cacheLevel == 2) {
                            level = "L2";
                        } else if (stats.cacheLevel == 3) {
                            level = "L3";
                        }
                        writer.println("<tr>");
                        writer.println("<td>" + level + "</td>");
                        writer.println("<td>" + Format.format2d(stats.time / cpuCount) + "</td>");
                        writer.println("<td>" + Format.format2d(stats.percent_total_time) + "%" + "</td>");
                        writer.println("</tr>");
                    }
                    writer.println("</table>");
                }
                int percentMM = (int) (binsData.timeMM / binsData.totalTime * 100);
                if (percentMM > 50) {
                    writer.println("More than " + String.valueOf(percentMM) + "% of time is spent in main memory.<br>");
                    writer.println("Some cache optimizations might help improve performance.<br>");
                }
                writer.println("</div>");
            }
            writer.println("<div id=\"linkList\">");
            writer.println("<div id=\"listmenu\">");                                               //App Characterization
            writer.println("<h1><span>App Characterization</span></h1>");
            writer.println("<ul id=\"listmenu\">");
            if (profileData != null) {
                writer.println("<li><a href=\"#per_system_time\" title=\"Application time, broken down by computation vs. communication for all unclassified HPCMP systems\">Cross-Arch Runtimes</a>&nbsp;</li>");
            }
            writer.println("<li><a href=\"#message_passing_overview\" title=\"Overall application time, broken down by computation vs. communication\">MPI Overview</a>&nbsp;</li>");
            if (ETaskTimeData) {
                writer.println("<li><a href=\"#message_passing_per_cpu\" title=\"Application time per CPU, broken down by computation vs. communication\">MPI Per-Task</a>&nbsp;</li>");
            }
            if (funcData != null) {
                writer.println("<li><a href=\"#function_time_overview\" title=\"Time spent per function\">Function Timing</a>&nbsp;</li>");
            }
            if (binsData != null) {
                writer.println("<li><a href=\"#cache_behavior_overview\" title=\"Application cache behavior\">Data Motion</a>&nbsp;</li>");
            }
            writer.println("</ul>");
            writer.println("</div>");
            if (profileData != null) {                                                             //Cross-Arch Analysis
                writer.println("<div id=\"listmenu\">");
                writer.println("<h1><span>Cross-Arch Analysis</span></h3>");
                writer.println("<ul id=\"listmenu\">");
                Set set = profileData.entrySet();
                Iterator itr = set.iterator();
                while (itr.hasNext()) {
                    Map.Entry entry = (Map.Entry) itr.next();
                    writer.println("<li><a href=\"\">" + database.getBaseResourceName(((Integer) entry.getKey()).intValue()) + "</a>&nbsp;</li>");
                }
                writer.println("</ul>");
                writer.println("</div>");
            }
            writer.println("<div id=\"listmenu\">");                                               //Other Resources
            writer.println("<h1><span>Other Resources</span></h3>");
            writer.println("<ul id=\"listmenu\">");
            writer.println("<li><a href=\"http://apps.ccac.hpc.mil/PMaC/faq.html/\">FAQ</a>&nbsp;</li>");
            writer.println("<li><a href=\"http://apps.ccac.hpc.mil/PMaC/faq.html/\">pmac-tools Home</a>&nbsp;</li>");
            writer.println("<li><a href=\"http://en.wikibooks.org/wiki/Message-Passing_Interface/MPI_function_reference\">MPI Function Interface</a>&nbsp;</li>");
            writer.println("</ul>");
            writer.println("</div>");
            writer.println("</div>"); //linklist
            writer.println("</div>"); //container
            writer.println("</body>");
            writer.println("</html>");
            writer.close();
        } catch (Exception e) {
            Logger.error("Exception while creating HTML file " + filename + " " + e);
            throw e;
        }
        try {                                                                                      //create ETime pie chart
            DefaultPieDataset pieData = new DefaultPieDataset();
            Iterator iter = psinsData.etimes.iterator();
            double other = 100.00;
            ArrayList events = new ArrayList();
            while (iter.hasNext()) {
                Event event = (Event) iter.next();
                if (event.value >= 1.0) {
                    pieData.setValue(event.eventType, new Double(event.value));
                    other -= event.value;
                    events.add(event.eventType);
                }
            }
            if (other >= 1.0) {
                pieData.setValue("Other", new Double(other));
                events.add("Other");
            }
            PiePlot plot = new PiePlot(pieData);
            for (int i = 0; i < events.size(); ++i) {
                plot.setSectionPaint((String) events.get(i), getEventColor((String) events.get(i)));
            }
            plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} {2}", NumberFormat.getNumberInstance(),
                    NumberFormat.getPercentInstance()));
            plot.setBackgroundPaint(bgColor);
            plot.setOutlineVisible(false);
            plot.setShadowPaint(bgColor);
            JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
            chart.setBackgroundPaint(bgColor);
            ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());
            File pieChart = new File(imagesDir + filename + "_etime_piechart.png");
            outputFiles.add(imagesDir + filename + "_etime_piechart.png");
            ChartUtilities.saveChartAsPNG(pieChart, chart, 600, 400, info);
        } catch (Exception e) {
            Logger.error("Exception while creating ETime pie chart " + e);
            throw e;
        }
        if (ETaskTimeData) {
            try {                                                                                  //create ETaskTime bar chart
                DefaultCategoryDataset dataset = new DefaultCategoryDataset();
                for (int i = 0; i < psinsData.cpuCount; ++i) {
                    List events = psinsData.taskEventTimes[i];
                    Iterator iter = events.iterator();
                    while (iter.hasNext()) {
                        Event event = (Event) iter.next();
                        if (event.value / psinsData.totalPredictionTime >= 0.01) {
                            dataset.addValue(event.value, event.eventType, String.valueOf(i));
                        }
                    }
                }
                JFreeChart chart = ChartFactory.createStackedBarChart(null, "CPU", "Seconds",
                        dataset, PlotOrientation.VERTICAL, true, false, false);
                chart.setBackgroundPaint(bgColor);
                chart.getLegend().setBorder(0, 0, 0, 0);
                chart.getLegend().setBackgroundPaint(bgColor);
                chart.getLegend().setItemPaint(java.awt.Color.white);
                CategoryPlot plot = (CategoryPlot) chart.getPlot();
                LegendItemCollection legendItems = plot.getLegendItems();
                CategoryItemRenderer renderer = plot.getRenderer();
                for (int i = 0; i < legendItems.getItemCount(); ++i) {
                    LegendItem legendItem = legendItems.get(i);
                    renderer.setSeriesPaint(legendItem.getSeriesIndex(), getEventColor(legendItem.getLabel()));
                }
                plot.setBackgroundPaint(java.awt.Color.white);
                plot.setRangeGridlinePaint(java.awt.Color.black);
                plot.getDomainAxis().setLabelPaint(java.awt.Color.white);
                plot.getRangeAxis().setLabelPaint(java.awt.Color.white);
                plot.getRangeAxis().setTickLabelPaint(java.awt.Color.white);
                plot.getDomainAxis().setAxisLineVisible(false);
                CategoryAxis domainAxis = plot.getDomainAxis();
                domainAxis.setTickLabelsVisible(false);
                domainAxis.setAxisLinePaint(java.awt.Color.black);
                ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());
                File file = new File(imagesDir + filename + "_etasktime_barchart.png");
                outputFiles.add(imagesDir + filename + "_etasktime_barchart.png");
                ChartUtilities.saveChartAsPNG(file, chart, 1200, 800, info);
            } catch (Exception e) {
                Logger.error("Exception while creating ETaskTime bar chart " + e);
                throw e;
            }
        }
        if (funcData != null) {
            try {                                                                                  //create func time pie chart
                DefaultPieDataset pieData = new DefaultPieDataset();
                Iterator iter = funcData.funcTimes.iterator();
                double other = 0.0;
                int count = 1;
                while (iter.hasNext()) {
                    FuncTime funcTime = (FuncTime) iter.next();
                    if (count <= NUM_FUNC && !funcTime.name.contentEquals("<others>")) {
                        pieData.setValue(funcTime.name, new Double(funcTime.time));
                        ++count;
                    } else {
                        other += funcTime.time;
                    }
                }
                if (other >= 0.0) {
                    pieData.setValue("Other", new Double(other));
                }
                PiePlot plot = new PiePlot(pieData);
                plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} {2}", NumberFormat.getNumberInstance(),
                        NumberFormat.getPercentInstance()));
                plot.setBackgroundPaint(bgColor);
                plot.setOutlineVisible(false);
                plot.setShadowPaint(bgColor);
                JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
                chart.setBackgroundPaint(bgColor);
                ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());
                File pieChart = new File(imagesDir + filename + "_functime_piechart.png");
                outputFiles.add(imagesDir + filename + "_functime_piechart.png");
                ChartUtilities.saveChartAsPNG(pieChart, chart, 600, 400, info);
            } catch (Exception e) {
                Logger.error("Exception while creating functime pie chart " + e);
                throw e;
            }
        }
        if (binsData != null) {
            try {                                                                                      //create bins data pie chart
                DefaultPieDataset pieData = new DefaultPieDataset();
                pieData.setValue("L1 cache", new Double(binsData.timeL1));
                if (binsData.timeL2 > 0) {
                    pieData.setValue("L2 cache", new Double(binsData.timeL2));
                }
                if (binsData.timeL3 > 0) {
                    pieData.setValue("L3 cache", new Double(binsData.timeL3));
                }
                pieData.setValue("Main Memory", new Double(binsData.timeMM));
                PiePlot plot = new PiePlot(pieData);
                plot.setBackgroundPaint(bgColor);
                plot.setOutlineVisible(false);
                plot.setShadowPaint(bgColor);
                plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} {2}", NumberFormat.getNumberInstance(),
                        NumberFormat.getPercentInstance()));
                JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
                chart.setBackgroundPaint(bgColor);
                ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());
                File pieChart = new File(imagesDir + filename + "_hitrates_piechart.png");
                outputFiles.add(imagesDir + filename + "_hitrates_piechart.png");
                ChartUtilities.saveChartAsPNG(pieChart, chart, 600, 400, info);
            } catch (Exception e) {
                Logger.error("Exception while creating hit rate pie chart " + e);
                throw e;
            }
        }
        return true;
    }

    private void setEmailBody() {
        body = "";
        if (application != null) {
            body += ("Application: " + application + "\n");
        }
        if (dataSet != null) {
            body += ("Data Set: " + dataSet + "\n");
        }
        if (cpuCount != 0) {
            body += ("CPU Count: " + String.valueOf(cpuCount) + "\n");
        }
        if (simulatedSystem != null) {
            body += ("Simulated System: " + simulatedSystem + "\n");
        }
    }

    private java.awt.Color getEventColor(String event) {
        if (event.equals("CPUTime")) {
            return new Color(255, 0, 0);     //red
        } else if (event.equals("MPI_Allgather")) {
            return new Color(255, 127, 80);  //coral
        } else if (event.equals("MPI_Allgatherv")) {
            return new Color(160, 82, 45);   //sienna
        } else if (event.equals("MPI_Allreduce")) {
            return new Color(0, 255, 0);     //lime
        } else if (event.equals("MPI_Alltoall")) {
            return new Color(255, 165, 0);   //orange
        } else if (event.equals("MPI_Alltoallv")) {
            return new Color(165, 42, 42);   //brown
        } else if (event.equals("MPI_Barrier")) {
            return new Color(255, 0, 255);   //fuchsia
        } else if (event.equals("MPI_Bcast")) {
            return new Color(255, 255, 0);   //yellow
        } else if (event.equals("MPI_Bsend")) {
            return new Color(0, 128, 0);     //green
        } else if (event.equals("MPI_Bsend_init")) {
            return new Color(135, 206, 235); //sky blue
        } else if (event.equals("MPI_Comm_create")) {
            return new Color(238, 130, 238); //violet
        } else if (event.equals("MPI_Comm_dup")) {
            return new Color(75, 0, 130);    //indigo
        } else if (event.equals("MPI_Comm_free")) {
            return new Color(64, 224, 208);  //turquoise
        } else if (event.equals("MPI_Comm_split")) {
            return new Color(46, 139, 87);   //sea green
        } else if (event.equals("MPI_Finalize")) {
            return new Color(128, 128, 0);   //olive
        } else if (event.equals("MPI_Gather")) {
            return new Color(220, 20, 60);   //crimson
        } else if (event.equals("MPI_Gatherv")) {
            return new Color(210, 180, 140); //tan
        } else if (event.equals("MPI_Init")) {
            return new Color(0, 0, 0);       //black
        } else if (event.equals("MPI_Ibsend")) {
            return new Color(255, 105, 180); //hot pink
        } else if (event.equals("MPI_Irecv")) {
            return new Color(192, 192, 192); //silver
        } else if (event.equals("MPI_Irsend")) {
            return new Color(221, 160, 221); //plum
        } else if (event.equals("MPI_Isend")) {
            return new Color(127, 255, 0);   //chartreuse
        } else if (event.equals("MPI_Issend")) {
            return new Color(0, 255, 255);   //aqua
        } else if (event.equals("MPI_Pcontrol")) {
            return new Color(176, 224, 230); //powder blue
        } else if (event.equals("MPI_Recv")) {
            return new Color(0, 128, 128);   //teal
        } else if (event.equals("MPI_Recv_init")) {
            return new Color(153, 102, 204); //amethyst
        } else if (event.equals("MPI_Reduce")) {
            return new Color(0, 0, 128);     //navy
        } else if (event.equals("MPI_Reduce_scatter")) {
            return new Color(230, 230, 250); //lavender
        } else if (event.equals("MPI_Request_free")) {
            return new Color(106, 90, 205);  //slate blue
        } else if (event.equals("MPI_Rsend")) {
            return new Color(240, 230, 140); //khaki
        } else if (event.equals("MPI_Rsend_init")) {
            return new Color(0, 100, 0);     //dark green
        } else if (event.equals("MPI_Scan")) {
            return new Color(250, 128, 114); //salmon
        } else if (event.equals("MPI_Scatter")) {
            return new Color(255, 192, 203); //pink
        } else if (event.equals("MPI_Scatterv")) {
            return new Color(210, 105, 30);  //chocolate
        } else if (event.equals("MPI_Ssend")) {
            return new Color(128, 0, 128);   //purple
        } else if (event.equals("MPI_Ssend_init")) {
            return new Color(255, 215, 0);   //gold
        } else if (event.equals("MPI_Send")) {
            return new Color(128, 128, 128); //gray
        } else if (event.equals("MPI_Send_init")) {
            return new Color(189, 183, 107); //dark khaki
        } else if (event.equals("MPI_Sendrecv")) {
            return new Color(112, 128, 144); //slate gray
        } else if (event.equals("MPI_Start")) {
            return new Color(127, 255, 212); //aquamarine
        } else if (event.equals("MPI_Startall")) {
            return new Color(144, 238, 144); //light green
        } else if (event.equals("MPI_Wait")) {
            return new Color(245, 222, 179); //wheat
        } else if (event.equals("MPI_Waitall")) {
            return new Color(128, 0, 0);     //maroon
        } else if (event.equals("MPI_Waitany")) {
            return new Color(0, 0, 255);     //blue
        } else if (event.equals("MPI_Waitsome")) {
            return new Color(218, 165, 32);  //goldenrod
        } else if (event.equals("Other")) {
            return new Color(255, 255, 255); //white
        } else {
            Logger.warn("No Color has been defined for " + event);
            return new Color(255, 255, 255);
        }
    }

    public static void main(String args[]) {
        try {
            ConfigSettings.readConfigFile();
            CommandLineParser commandLineParser = new CommandLineParser(args);
            Reporter reporter = new Reporter(commandLineParser.dir, commandLineParser.email, commandLineParser.saveOutput);
            boolean success;
            success = commandLineParser.profiles != null ? reporter.run(commandLineParser.profiles) : reporter.run(commandLineParser.file);
            if (success) {
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            }
        } catch (Exception e) {
            Logger.inform(e + "\n*** FAIL *** FAIL *** FAIL Exception*****************\n");
        }
    }
}

class CommandLineParser implements CommandLineInterface {

    OptionParser optionParser;
    public String dir;
    public String file = "";
    public String email = "";
    public int[] profiles = null;
    public boolean saveOutput;
    static final String[] ALL_OPTIONS = {
        "help:?",
        "dir:s",
        "file:s",
        "email:s",
        "profiles:s",
        "save_output:?"
    };
    static final String helpString =
            "[Basic Params]:\n"
            + "    --help                              : print a brief help message\n"
            + "[Script Params]:\n"
            + "    --dir            /path/to/psinsout  : path to directory containing .psinsout files [REQ]\n"
            + "    --file           <filename>         : process a specific file\n"
            + "                                          default is to process all\n"
            + "    --email          <email addresses>  : comma delimited email recipients for report\n"
            + "    --profiles       <profiles>         : comma delimited machine profiles"
            + "    --save_output                       : output files are never deleted";

    public CommandLineParser(String argv[]) {
        optionParser = new OptionParser(ALL_OPTIONS, this);
        if (argv.length < 1) {
            optionParser.printUsage("");
        }
        optionParser.parse(argv);
        if (optionParser.isHelp()) {
            optionParser.printUsage("");
        }
        if (!optionParser.verify()) {
            Logger.error("Error in command line options");
        }
        dir = (String) optionParser.getValue("dir");
        file = (String) optionParser.getValue("file");
        email = (String) optionParser.getValue("email");
        saveOutput = optionParser.getValue("save_output") != null ? true : false;
        if (optionParser.getValue("profiles") != null) {
            String[] tmp = ((String) optionParser.getValue("profiles")).split(",");
            profiles = new int[tmp.length];
            for (int i = 0; i < tmp.length; ++i) {
                profiles[i] = Integer.parseInt(tmp[i]);
            }
        }
    }

    public boolean verifyValues(HashMap values) {
        dir = (String) values.get("dir");
        if (dir == null) {
            Logger.error("--dir is a required argument");
            return false;
        }
        return true;
    }

    public TestCase getTestCase(HashMap values) {
        return null;
    }

    public boolean isHelp(HashMap values) {
        return (values.get("help") != null);
    }

    public boolean isVersion(HashMap values) {
        return false;
    }

    public void printUsage(String str) {
        System.out.println("\n" + str + "\n");
        System.out.println(helpString);
        String all = "usage :\n";
        for (int i = 0; i < ALL_OPTIONS.length; ++i) {
            all += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        all += ("\n" + str);
    }
}

