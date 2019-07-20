package com.tuan.exercise.grader.mail;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;

public class MailHandler {

    private final String username;
    private final String password;

    private static final String HOST = "imap.gmail.com";
    private static final String STORE_TYPE = "imaps";
    private static final String FOLDER_INBOX = "INBOX";
    private static final String ZIP_EXT = ".zip";

    private static final int NET_BUF = 1024;
    private static final int FILE_BUF = 1024;

    public MailHandler(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public Store connect() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", STORE_TYPE);
        props.put("mail.imap.host", HOST);
        props.put("mail.imap.port", "993");
        props.put("mail.imap.starttls.enable", "true");

        // get session and connect to store
        Session mailSession = Session.getDefaultInstance(props);
        Store store = mailSession.getStore();
        store.connect(HOST, this.username, this.password);

        return store;
    }

    public void downloadInboxZips(Store store, String subjectRegex, String destDirName)
            throws IOException, MessagingException {
        File destDir = new File(destDirName);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        Folder inbox = store.getFolder(FOLDER_INBOX);
        inbox.open(Folder.READ_ONLY);

        Message[] msgArr = inbox.getMessages();
        for (Message msg : msgArr) {
            String subject = msg.getSubject();
            Address[] froms = msg.getFrom();

            if (!Pattern.matches(subjectRegex, subject) || froms == null) {
                continue;
            }

            String from = ((InternetAddress) froms[0]).getAddress();
            this.downloadAttachments(msg, from, ZIP_EXT, destDirName);
        }

        inbox.close(false);
    }

    private void downloadAttachments(Message msg, String from, String ext, String destDirName)
            throws IOException, MessagingException {

        Multipart mp = (Multipart) msg.getContent();
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart bodyPart = mp.getBodyPart(i);

            // dealing with attachments only
            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) && bodyPart.getFileName() != null) {
                String fileFullname = bodyPart.getFileName();
                String fileExt = fileFullname.substring(fileFullname.lastIndexOf('.'));
                if (!ext.equals(fileExt))
                    continue;

                String savedFileName = String.format("%s_%s", from, fileFullname);
                File download = new File(String.format("%s%s%s", destDirName, File.separator, savedFileName));
                InputStream netIn = bodyPart.getInputStream();
                try (OutputStream fileOut = new FileOutputStream(download)) {
                    byte[] buf = new byte[NET_BUF];
                    int bytesRead;
                    while ((bytesRead = netIn.read(buf)) > 0) {
                        fileOut.write(buf, 0, bytesRead);
                    }
                }
            }
        }
    }

    public void extractAll(String srcBaseDir, String destBaseDir) {
        File srcDir = new File(srcBaseDir);
        if (!srcDir.exists())
            return;

        File destDir = new File(destBaseDir);
        if (!destDir.exists())
            destDir.mkdirs();

        // iterate through all files in the source directory
        File[] compressedFiles = srcDir.listFiles();
        for (File compFile : compressedFiles) {
            extractSingle(compFile, destBaseDir);
        }
    }

    private void extractSingle(File compFile, String destBaseDir) {
        // test if file is compressed type
        try (DataInputStream zipTestIn = new DataInputStream(new FileInputStream(compFile))) {
            if (zipTestIn.readInt() != 0x504b0304)
                return;
        } catch (IOException e) {
            e.printStackTrace();
        }

        String compFileFullname = compFile.getName();
        String destDirPath = new StringBuilder()
                .append(destBaseDir)
                .append(File.separator)
                .append(compFileFullname.substring(0, compFileFullname.lastIndexOf('.')))
                .toString();

        File destDir = new File(destDirPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        try (InputStream zipIn = new FileInputStream(compFile)) {
            ZipInputStream zis = new ZipInputStream(zipIn);
            ZipEntry zipEntry;
            byte[] buf = new byte[FILE_BUF];
            while ((zipEntry = zis.getNextEntry()) != null) {
                File extracted = new File(new StringBuilder()
                        .append(destDirPath)
                        .append(File.separator)
                        .append(zipEntry.getName()).toString());
                if (zipEntry.isDirectory()) {
                    if (!extracted.exists())
                        extracted.mkdirs();
                } else {
                    try (FileOutputStream fos = new FileOutputStream(extracted)) {
                        int length;
                        while ((length = zis.read(buf)) > 0) {
                            fos.write(buf, 0, length);
                        }
                    }
                }
                zis.closeEntry();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
