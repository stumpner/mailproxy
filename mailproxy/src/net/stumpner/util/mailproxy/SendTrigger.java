package net.stumpner.util.mailproxy;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by IntelliJ IDEA.
 * User: franz.stumpner
 * Date: 21.09.2007
 * Time: 16:41:20
 */
public class SendTrigger {

    private Timer timer = null;
    private Boolean triggerRun = false; //gibt an ob der Trigger angestossen wurde

    /**
     * Startet den Trigger und ruft {@link MailProxy#sendQueue()} auf, wenn der Trigger auslöst (nach Zeit).
     * @param delay löst nach delay Millisekunden den Trigger aus
     */
    synchronized public void run(long delay) {

        //Nur starten, wenn kein anderer Timer läuft...
        if (!triggerRun) {
            triggerRun = true;
            handleTrigger(true,delay);
        } else {
            //bereits gestartet...
        }
    }

    synchronized private void handleTrigger(boolean start,long delay) {
        if (start) {
            if (timer!=null) {
                timer.cancel();
            }
            timer = new Timer();
            timer.schedule(createTimerTask(),delay);
        } else {
            if (timer!=null)
                timer.cancel();
            timer = null;
        }
    }


    /**
     * Erstellt ein TimerTask Objekt welches {@link net.stumpner.util.mailproxy.MailProxy#sendQueue()} anstosst
     * @return TimerTask gibt einen neu erstellen TimerTask zurück
     */
    private TimerTask createTimerTask() {

        return new TimerTask() {

            public void run() {
                triggerRun = false; //Trigger entwerten, da er ja jetzt dann läuft
                MailProxy.getInstance().sendQueue();
                //Wenn der Trigger entwertet wurde, den Timer stoppen/canceln
                if (!triggerRun) handleTrigger(false,0);
            }
        };

    }

}
