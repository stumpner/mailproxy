package net.stumpner.util.mailproxy.example;

import net.stumpner.util.mailproxy.MailProxy;

import javax.mail.MessagingException;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.SimpleLayout;

import java.util.Random;

/**
 * Created by IntelliJ IDEA.
 * User: franz.stumpner
 * Date: 21.09.2007
 * Time: 13:38:34
 * To change this template use File | Settings | File Templates.
 */
public class MailProxyTest extends Thread {

    public void run() {

        Logger rootLogger = Logger.getRootLogger();
        rootLogger.addAppender(new ConsoleAppender(new SimpleLayout()));
        rootLogger.setLevel(Level.DEBUG);
        Logger logger = Logger.getLogger(MailProxyTest.class);

        logger.info("MailProxyTest: started [OK]");

        try {
            MailProxy mailProxy = MailProxy.getInstance();
            mailProxy.setPostmaster("","maidfgdfl.stumpner.net");
            for (int a=0;a<10;a++) {
                Random rand = new Random();
                int waittime = rand.nextInt()/500000;
                if (waittime<0) waittime = waittime*-1;
                System.out.println("Wait: "+waittime);
                Thread.sleep(waittime);
                if (rand.nextBoolean()) {
                    mailProxy.saveSend("server06.stumpner.co.at","franz@stumpner.net","franz@stumpner.net","test"+a,"testbody");
                } else {
                    mailProxy.saveSend("mail.stumpner343.net","franz@stumpner.net","franz@stumpner.net","test"+a,"testbody");
                }
            }
            //mailProxy.sendQueue();
        } catch (MessagingException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        logger.info("MailProxyTest: started [ENDED]");


    }

}
