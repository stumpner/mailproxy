package net.stumpner.util.mailproxy;

import org.apache.log4j.Logger;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import java.util.*;

/**
 * MailProxy ist eine Klasse zum Senden von Mails über eine Queue bzw. Warteschlange
 * <p>
 * Um die Libary zu nutzen, werden folgende Pakete benötigt
 * <ul>
 *   <li>Sun Java Mail API 1.4 (http://java.sun.com/products/javamail/)</li>
 *   <li>Sun JAF 1.1 (JavaBeans Activation Framework) (http://java.sun.com/products/javabeans/jaf/index.jsp)</li>
 *   <li>Apache Log4J (http://logging.apache.org/log4j/index.html)</li>
 * </ul>
 * </p>
 *
 * History:
 * - 2012-10-22: send von statischer Transport-Methode umgebaut auf Instanz todo: qeue-send ist noch nicht umgebaut
 *
 * User: franz.stumpner
 * Date: 20.09.2007
 * Time: 18:13:52
 */
public class MailProxy {

    public static MailProxy getInstance() {
        return instance;
    }

    static private MailProxy instance = new MailProxy();
    private MailProxy() {
    }

    LinkedList mailQueue = new LinkedList();
    HashMap errorMails = new HashMap(); //Auflistung mit den Mails: <Mail:retries(int)>

    int maxRetries = 3;         //Wie oft ein Zustellversucht unternommen werden soll
    long sendDelayTime = 10000;     //Verzögerung in Millisekunden wann mit dem Mailversand begonnen werden soll
    long retryDelayTime = 30000;    //Verzögerung in Millisekunden wann mit dem nächsten Zustellversuch begonnen werden soll
    boolean autoSend = true;   //Ob die Mails automatisch versendet werden (true) oder durch aufruf von sendQueue
    String postmaster = "";     //Emailadresse an denen Fehlermeldungen gesendet werden
    String mailserver = "";     //Mailserver zum versenden der Postmaster-Mails

    final SendTrigger sendTrigger = new SendTrigger();

    /**
     * Schickt ein Mail über den Mailserver, speichert es aber vorher in einer internen Queue ab
     * Vorteil: Senden geht sehr schnell (es muss nicht auf den Mailserver gewartet werden)
     * und bei temorären Mail-Server fehlern wird das mail später geschickt
     * @param mailServer Mailserver
     * @param sender Absender
     * @param receiver Empfänger
     * @param subject Betreff
     * @param body Mailinhalt
     * @throws MessagingException Wenn die Email nicht gesendet werden konnte
     */
    public void saveSend(String mailServer, String sender, String receiver, String subject, String body) throws MessagingException {

        Logger logger = Logger.getLogger(MailProxy.class);
        logger.debug("MailProxy: MAIL->IN saveSend(...);");

        Properties properties = System.getProperties();
        properties.put("mail.smtp.host", mailServer);
        Session mysession = Session.getDefaultInstance(properties, null);
        MimeMessage message = new MimeMessage(mysession);
        try {
            message.setFrom(new InternetAddress(sender));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(receiver));
            message.setSubject(subject);
            message.setContent(body,"text/plain");
            mailQueue.add(message);
        } catch (MessagingException e) {
            logger.error("Error sending Mail ["+mailServer+","+receiver+"]");
            throw e;
        }

        sendTrigger.run(sendDelayTime);
    }

    /**
     * Verschickt alle Mails die sich in der Queue befinden.
     */
    public synchronized void sendQueue() {

        ArrayList sentMessages = new ArrayList();
        ArrayList removedMessages = new ArrayList();
        Logger logger = Logger.getLogger(MailProxy.class);
        logger.debug("MailProxy: MAIL->QSEND sendQueue(...); ["+mailQueue.size()+"]");

        LinkedList mailQueueCopy = (LinkedList)mailQueue.clone();

        if (mailQueueCopy.size()>0) {
            Iterator it = mailQueueCopy.iterator();
            while (it.hasNext()) {
                MimeMessage mimeMessage = (MimeMessage)it.next();
                String subject = "";
                String recipient = "";
                try {
                    subject = mimeMessage.getSubject();
                    recipient = Arrays.toString(
                            mimeMessage.getRecipients(MimeMessage.RecipientType.TO)
                    );
                    logger.debug("MailProxy: MAIL->QSEND sendQueue(...); ["+(mailQueueCopy.indexOf(mimeMessage)+1)+" / "+mailQueueCopy.size()+"]");
                    Transport.send(mimeMessage);
                    sentMessages.add(mimeMessage);
                } catch (MessagingException e) {
                    logger.error("MailProxy: MAIL->ERROR sendQueue(...); ["+(mailQueueCopy.indexOf(mimeMessage)+1)+" / "+mailQueueCopy.size()+"] "+e.getMessage());
                    e.printStackTrace();
                    //prüfen wie oft das errormail bereits versucht wurde zu versenden
                    int retries = 1;
                    if (errorMails.containsKey(mimeMessage)) {
                        retries = ((Integer)errorMails.get(mimeMessage)).intValue();
                        retries++;
                    }
                    errorMails.put(mimeMessage,new Integer(retries));
                    if (retries>=maxRetries) {
                        errorMails.remove(mimeMessage);
                        removedMessages.add(mimeMessage);
                        //Fehlermeldung versenden
                        logger.fatal("MailProxy: MAIL->ERROR sendQueue(...); maxRetries["+maxRetries+"] erreicht."); // "+mimeMessage+" "+subject+" "+recipient);
                        if (mailserver.length()>0 && postmaster.length()>0) {
                            send(mailserver,postmaster,postmaster,"MailProxy - Error Report","Fehler beim Versender der Email:\n\nVersuche: "+retries+"\nSubject: "+subject+"\nRecipients: "+recipient+"\n"+e.getMessage());
                        } else {
                            logger.warn("MailProxy: No Postmaster-Adress is set for Errorreports!");
                        }
                    }
                }
            }

            //versendete Mails löschen:
            synchronized(mailQueue) {
                mailQueue.removeAll(sentMessages);
                errorMails.remove(sentMessages);
                mailQueue.removeAll(removedMessages);
            }
            logger.debug("MailProxy: MAIL->DONE sendQueue(...); [vers="+sentMessages.size()+",err="+errorMails.size()+",queu="+mailQueue.size()+"]");
        }

        if (mailQueue.size()>0) {
            sendTrigger.run(retryDelayTime);
        } else {
            //sendeTrigger nichtmehr anstossen
        }
    }

    /**
     * Schickt ein mail sofort über den Mailserver
     * @param mailServer Mailserver über den das Mail versendet werden soll
     * @param sender Absender der Email
     * @param receiver Empfänger der Email
     * @param subject Betreff der Email
     * @param body Text/mailbody der Email
     */
    public void send(String mailServer, String sender, String receiver, String subject, String body) {

        Properties properties = System.getProperties();
        properties.put("mail.smtp.host", mailServer);
        Session mysession = Session.getDefaultInstance(properties, null);
        MimeMessage message = new MimeMessage(mysession);
        Transport transport = null;
        try {
            InternetAddress receiverAddr = new InternetAddress(receiver);

            message.setFrom(new InternetAddress(sender));
            message.addRecipient(Message.RecipientType.TO, receiverAddr);
            message.setSubject(subject);
            message.setContent(body,"text/plain");

            //Alte static methode = Transport.send(message);
            transport = mysession.getTransport("smtp");
            transport.connect();
            transport.sendMessage(message, new InternetAddress[] { receiverAddr });

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }


    /**
     * Wie oft ein Zustellversuch unternommen werden soll bevor eine Fehlermeldung an die
     * Postmaster-Adresse gesendet wird
     * @param maxRetries Maximale Anzahl der Versuche ein fehlerhaftes Email zu versenden
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Wenn Autosend aktiviert ist, werden die Mails aus der Queue automatisch versendet.
     * Die Methode {@link MailProxy#sendQueue()}  muss nicht aufgerufen werden.
     * @param autoSend Ob die Emails automatisch (nach Zeit) versendet werden sollen
     */
    public void setAutoSend(boolean autoSend) {
        this.autoSend = autoSend;
    }


    /**
     * Hier kann die Emailadresse des Postmasters hinterlegt werden, an die Fehlermeldungen
     * und Reports gesendet werden.
     * @param postmaster Emailadresse des Administrators, der Fehlermeldungen empfängt
     * @param mailserver Mailserver über den die Fehlermeldungen versendet werden sollen
     */
    public void setPostmaster(String postmaster, String mailserver) {
        this.postmaster = postmaster;
        this.mailserver = mailserver;
    }


    /**
     * Nach wieviel Millisekunden, nach dem ein Mail eingegangen ist, soll der Mailversand gestartet werden?
     * @param sendDelayTime Anzahl der Millisekunden
     */
    public void setSendDelayTime(long sendDelayTime) {
        this.sendDelayTime = sendDelayTime;
    }

    /**
     * Wenn ein Email nicht versendet werden konnte, nach wieviel Millisekunden dann ein erneuter Versuch
     * gestartet werden soll, das Mail zu versenden
     * @param retryDelayTime
     */
    public void setRetryDelayTime(long retryDelayTime) {
        this.retryDelayTime = retryDelayTime;
    }
}

