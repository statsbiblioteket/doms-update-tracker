package dk.statsbiblioteket.doms.updatetracker.improved.worklog;

import dk.statsbiblioteket.doms.updatetracker.improved.fedora.FedoraFailedException;
import dk.statsbiblioteket.doms.updatetracker.improved.database.UpdateTrackerPersistentStore;
import dk.statsbiblioteket.doms.updatetracker.improved.database.UpdateTrackerStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

/**
 * This timertasks regularly polls the fedora worklog and calls the update tracker with the unit of work
 */
public class WorkLogPollTask extends TimerTask {

    private static Logger log = LoggerFactory.getLogger(WorkLogPollTask.class);

    private final WorkLogPoller workLogPoller;
    private final UpdateTrackerPersistentStore updateTrackerPersistentStore;
    private int limit;

    /**
     * @param workLogPoller
     * @param updateTrackerPersistentStore
     * @param limit                        the amount of work units to retrieve in each invocation
     */
    public WorkLogPollTask(WorkLogPoller workLogPoller, UpdateTrackerPersistentStore updateTrackerPersistentStore,
                           int limit) {
        this.workLogPoller = workLogPoller;
        this.updateTrackerPersistentStore = updateTrackerPersistentStore;
        this.limit = limit;
    }

    @Override
    public void run() {

        try {
            Date lastRegisteredChange = getStartDate();

            List<WorkLogUnit> events = getEvents(lastRegisteredChange);

            for (WorkLogUnit event : events) {
                handleEvent(event);
            }
            if (!events.isEmpty()) {
                log.info("No further events to work on");
            }
        } catch (Exception e){//Fault barrier
            //If this method bombs out, the timer is stopped, and will not start until the webservice is reloaded
            log.error("Failed to poll for worklog tasks",e);
            //Log this and keep going. Only Errors get through now
        }
    }

    private Date getStartDate() {
        Date lastRegisteredChange = null;
        try {
            //TODO DO NOT USE LASTMODIFIED, USE THE INCREMENTING KEY
            lastRegisteredChange = updateTrackerPersistentStore.lastChanged();
        } catch (UpdateTrackerStorageException e){
            log.error("Failed to find lastChanged from the update tracker",e);
        }
        if (lastRegisteredChange == null){
            lastRegisteredChange = new Date(0);
        }
        return lastRegisteredChange;
    }

    private List<WorkLogUnit> getEvents(Date lastRegisteredChange) {
        List<WorkLogUnit> events = new ArrayList<WorkLogUnit>();
        try {
            log.debug("Starting query for events since '{}'", lastRegisteredChange);
            events = workLogPoller.getFedoraEvents(lastRegisteredChange, limit);
            log.info("Looking for events since '{}'. Found '{}", lastRegisteredChange, events.size());
        } catch (IOException e) {
            log.error("Failed to get Fedora events.", e);
        }
        return events;
    }

    private void handleEvent(WorkLogUnit event) {
        try {
            final String pid = event.getPid();
            final Date date = event.getDate();
            final String param = event.getParam();
            final String method = event.getMethod();
            log.debug("Registering the event '{}'", event);

            if (method.equals("ingest")) {
                updateTrackerPersistentStore.objectCreated(pid, date);
            } else if (method.equals("modifyObject")) {
                updateTrackerPersistentStore.objectStateChanged(pid, date, param);
            } else if (method.equals("purgeObject")) {
                updateTrackerPersistentStore.objectDeleted(pid, date);
            } else if (method.equals("addDatastream") ||
                       method.equals("modifyDatastreamByReference") ||
                       method.equals("modifyDatastreamByValue") ||
                       method.equals("purgeDatastream") ||
                       method.equals("setDatastreamState") ||
                       method.equals("setDatastreamVersionable")) {
                updateTrackerPersistentStore.datastreamChanged(pid, date, param);
            } else if (method.equals("addRelationship") || method.equals("purgeRelationship")) {
                updateTrackerPersistentStore.objectRelationsChanged(pid, date);
            } else if (method.equals("getObjectXML") || method.equals("export") ||
                       method.equals("getDatastream") ||
                       method.equals("getDatastreams") ||
                       method.equals("getDatastreamHistory") ||
                       method.equals("putTempStream") ||
                       method.equals("getTempStream") ||
                       method.equals("compareDatastreamChecksum") ||
                       method.equals("getNextPID") || method.equals("getRelationships") ||
                       method.equals("validate")) {// Nothing to do
                log.debug("Got nonchanging event '{}' from worklog", event);
            } else {
                log.warn("Got unknown event '{}' from worklog", event);
            }

        }catch(UpdateTrackerStorageException e){
            log.error("Failed to store events in update tracker. Failed on '" + event + "'", e);
        }catch(FedoraFailedException e){
            log.error("Failed to communicate with fedora. Failed on '" + event + "'", e);
        }
    }
}
