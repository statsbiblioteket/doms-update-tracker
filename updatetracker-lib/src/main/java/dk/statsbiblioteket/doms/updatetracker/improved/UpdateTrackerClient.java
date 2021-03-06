package dk.statsbiblioteket.doms.updatetracker.improved;

import dk.statsbiblioteket.doms.updatetracker.improved.database.UpdateTrackerStorageException;
import dk.statsbiblioteket.doms.updatetracker.improved.database.datastructures.Record;
import dk.statsbiblioteket.doms.updatetracker.webservice.InvalidCredentialsException;
import dk.statsbiblioteket.doms.updatetracker.webservice.MethodFailedException;
import dk.statsbiblioteket.doms.updatetracker.webservice.RecordDescription;
import dk.statsbiblioteket.doms.updatetracker.webservice.UpdateTrackerWebservice;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Update tracker webservice. Provides upper layers of DOMS with info on changes
 * to objects in Fedora. Used by DOMS Server aka. Central
 */
public class UpdateTrackerClient implements UpdateTrackerWebservice {

    private final UpdateTrackingSystem updateTrackingSystem;

    public UpdateTrackerClient(UpdateTrackingSystem updateTrackingSystem) {
        this.updateTrackingSystem = updateTrackingSystem;
    }


    /**
     * Lists the entry objects of views (records) in Fedora, in the given
     * collection, that have changed since the given time.
     *
     * @param collectionPid The PID of the collection in which we are looking
     *                      for changes.
     * @param viewAngle     ...TODO doc
     * @param beginTime     The time since which we are looking for changes.
     * @param state         ...TODO doc
     *
     * @return returns java.util.List<dk.statsbiblioteket.doms.updatetracker
     * .webservice.RecordDescription>
     * @throws dk.statsbiblioteket.doms.updatetracker.webservice.MethodFailedException
     * @throws dk.statsbiblioteket.doms.updatetracker.webservice.InvalidCredentialsException
     */
    public List<RecordDescription> listObjectsChangedSince(String collectionPid, String viewAngle, long beginTime,
                                                       String state, Integer offset, Integer limit) throws
                                                                                                    InvalidCredentialsException,
                                                                                                    MethodFailedException {
        List<Record> entries;
        try {
            entries = updateTrackingSystem.getStore().lookup(new java.util.Date(beginTime),
                                                             viewAngle,
                                                             offset,
                                                             limit,
                                                             state,
                                                             collectionPid);
        } catch (UpdateTrackerStorageException e) {
            throw new MethodFailedException("Failed to query the persistent storage", "", e);
        }
        return convert(entries, state);
    }

    /**
     * Return the last time a view/record conforming to the content model of the
     * given content model entry, and in the given collection, has been changed.
     *
     * @param collectionPid The PID of the collection in which we are looking
     *                      for the last change.
     * @param viewAngle     The name of the viewangle in which we are looking
     *                      for the last change.
     *
     * @return The date/time of the last change.
     * @throws dk.statsbiblioteket.doms.updatetracker.webservice.InvalidCredentialsException
     * @throws dk.statsbiblioteket.doms.updatetracker.webservice.MethodFailedException
     */
    public long getLatestModificationTime(java.lang.String collectionPid, java.lang.String viewAngle,
                                          java.lang.String state) throws
                                                                  InvalidCredentialsException,
                                                                  MethodFailedException {

        try {
            return updateTrackingSystem.getStore().lastChanged(viewAngle, collectionPid).getTime();
        } catch (UpdateTrackerStorageException e) {
            throw new MethodFailedException("Failed to query the persistent storage", "", e);
        }
    }


    private List<RecordDescription> convert(List<Record> entries, String state) {
        List<RecordDescription> list2 = new ArrayList<>(entries.size());
        for (Record record : entries) {
            list2.add(convert(record, state));
        }
        return list2;
    }

    private RecordDescription convert(Record thing, String state) {
        RecordDescription thang = new RecordDescription();
        thang.setPid(thing.getEntryPid());
        thang.setCollectionPid(thing.getCollection());
        thang.setLastChangedTime(real(thing.getLastModified()).getTime());
        final long active = real(thing.getActive()).getTime();
        final long deleted = real(thing.getDeleted()).getTime();
        final long inactive = real(thing.getInactive()).getTime();
        try {
            switch (Record.State.valueOf(state)) {
                case ACTIVE:
                    if (active > deleted) {
                        thang.setState("A");
                        thang.setRecordTime(active);
                    } else {
                        thang.setState("D");
                        thang.setRecordTime(deleted);
                    }
                    break;
                case INACTIVE:
                    if (inactive > deleted) {
                        thang.setState("I");
                        thang.setRecordTime(inactive);
                    } else {
                        thang.setState("D");
                        thang.setRecordTime(deleted);
                    }
                    break;
                case DELETED:
                    thang.setState("D");
                    thang.setRecordTime(deleted);
                    break;
            }
        } catch (IllegalArgumentException e) { //If you specified something else
            if (active >= inactive && active > deleted) {
                thang.setState("A");
                thang.setRecordTime(active);
            } else if (inactive > active && inactive > deleted) {
                thang.setState("I");
                thang.setRecordTime(inactive);
            } else if (deleted >= active && deleted >= inactive) {
                thang.setState("D");
                thang.setRecordTime(deleted);
            }
        }
        return thang;
    }

    private Date real(Date date) {
        if (date == null){
            return new Date(0);
        }
        return date;
    }
}
