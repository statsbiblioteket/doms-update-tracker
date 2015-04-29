package dk.statsbiblioteket.doms.updatetracker.improved.fedora;

import dk.statsbiblioteket.doms.central.connectors.BackendInvalidCredsException;
import dk.statsbiblioteket.doms.central.connectors.BackendInvalidResourceException;
import dk.statsbiblioteket.doms.central.connectors.BackendMethodFailedException;
import dk.statsbiblioteket.doms.central.connectors.Connector;
import dk.statsbiblioteket.doms.central.connectors.fedora.Fedora;
import dk.statsbiblioteket.doms.central.connectors.fedora.structures.FedoraRelation;
import dk.statsbiblioteket.doms.central.connectors.fedora.structures.ObjectProfile;
import dk.statsbiblioteket.doms.central.connectors.fedora.structures.ObjectType;
import dk.statsbiblioteket.doms.central.connectors.fedora.tripleStore.TripleStoreRest;
import dk.statsbiblioteket.doms.central.connectors.fedora.views.Views;
import dk.statsbiblioteket.doms.central.connectors.fedora.views.ViewsImpl;
import dk.statsbiblioteket.doms.updatetracker.improved.database.ViewBundle;
import dk.statsbiblioteket.doms.updatetracker.improved.database.datastructures.Record;
import dk.statsbiblioteket.util.Pair;
import dk.statsbiblioteket.util.caching.TimeSensitiveCache;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;

import javax.xml.bind.JAXBException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * This class embeds the specific functions we need for Fedora in the update tracker
 */
public class FedoraForUpdateTracker {

    public static final String ENTRY_RELATION
            = "http://doms.statsbiblioteket.dk/relations/default/0/1/#isEntryForViewAngle";
    protected static final String COLLECTION_RELATION
            = "http://doms.statsbiblioteket.dk/relations/default/0/1/#isPartOfCollection";
    protected static final String HASMODEL_RELATION
            = "info:fedora/fedora-system:def/model#hasModel";

    private final Views views;
    private final TripleStoreRest tripleStoreRest;
    private final Fedora fedoraRest;
    private final EntryAngleCache entryAngleCache;


    private static final int ONE_MINUTE_IN_MILLISECONDS = 60 * 1000;

    /**
     * This is the profile of object profiles. Elements have a lifetime of only one minute, which should prevent the cache
     * from growing to large.
     * As both the pid and the date is part of the key, this is just a cache for multiple invocations during the
     * same event. The next event will have a new date, and will thus not hit the old profile, and thus I do not need
     * to invalidate entries in this cache.
     */
    private static final TimeSensitiveCache<Pair<String,Date>, ObjectProfile> profileCache = new TimeSensitiveCache<>(ONE_MINUTE_IN_MILLISECONDS, false);



    public FedoraForUpdateTracker(EntryAngleCache entryAngleCache, Fedora fedoraRest, TripleStoreRest tripleStoreRest,
                                  ViewsImpl views) {

        this.entryAngleCache = entryAngleCache;
        this.fedoraRest = fedoraRest;
        this.tripleStoreRest = tripleStoreRest;
        this.views = views;
    }

    public Collection<String> getEntryAngles(String pid, Date date) throws FedoraFailedException {
        try {
            Set<String> entryAngles = new HashSet<>();
            ObjectProfile profile = getObjectProfile(pid, date);
            synchronized (entryAngleCache) {
                if (profile.getType() == ObjectType.CONTENT_MODEL){
                    entryAngleCache.setEntryViewAngles(pid, getEntryAnglesForContentModel(pid, date));
                }
                for (String contentmodelPid : profile.getContentModels()) {
                    entryAngles.addAll(getEntryAnglesForContentModel(contentmodelPid, date));
                }
            }
            return entryAngles;
        } catch (BackendInvalidCredsException | BackendMethodFailedException | BackendInvalidResourceException | JAXBException e) {
            throw new FedoraFailedException("Failed to get view info from Fedora for pid " + pid, e);
        }
    }

    private Set<String> getEntryAnglesForContentModel(String contentmodel, Date date) throws
                                                           BackendMethodFailedException,
                                                           BackendInvalidCredsException,
                                                           BackendInvalidResourceException,
                                                           JAXBException {
        Set<String> entryAngles = entryAngleCache.getCachedEntryAngles(contentmodel);
        if (entryAngles == null) {
            try {
                List<FedoraRelation> entryRelations = fedoraRest.getNamedRelations(contentmodel, ENTRY_RELATION, date.getTime());

                entryAngles = getObject(entryRelations);
                entryAngles = scrubViewAngles(entryAngles);
            } catch (BackendInvalidResourceException e) {
                entryAngles = new HashSet<>();
            }
            entryAngleCache.setEntryViewAngles(contentmodel, entryAngles);
        }
        return entryAngles;
   }

    private Set<String> scrubViewAngles(Set<String> entryAngles) {
        Set<String> scrubbedEntryAngles = new HashSet<>();
        CollectionUtils.collect(entryAngles, new Transformer<String, String>() {
            @Override
            public String transform(String string) {
                return string.replaceAll("^\"", "").replaceAll("\"$", "");
            }
        }, scrubbedEntryAngles);
        return scrubbedEntryAngles;
    }

    private Set<String> getObject(List<FedoraRelation> entryRelations) {
        Set<String> entryAngles;
        entryAngles = new HashSet<>();
        CollectionUtils.collect(entryRelations, new Transformer<FedoraRelation, String>() {
            @Override
            public String transform(FedoraRelation relation) {
                return Connector.toPid(relation.getObject());
            }
        }, entryAngles);
        return entryAngles;
    }

    private Set<String> getSubject(List<FedoraRelation> entryRelations) {
        Set<String> entryAngles;
        entryAngles = new HashSet<>();
        CollectionUtils.collect(entryRelations, new Transformer<FedoraRelation, String>() {
            @Override
            public String transform(FedoraRelation relation) {
                return Connector.toPid(relation.getSubject());
            }
        }, entryAngles);
        return entryAngles;
    }


    public ViewBundle calcViewBundle(String entryPid, String viewAngle, Date date) throws FedoraFailedException {
        try {
            List<String> pids = views.getViewObjectsListForObject(entryPid, viewAngle, date.getTime());
            return new ViewBundle(entryPid, viewAngle, pids);
        } catch (BackendInvalidCredsException | BackendMethodFailedException | BackendInvalidResourceException e) {
            throw new FedoraFailedException("Failed calculating view bundle", e);
        }
    }

    public Set<String> getCollections(String pid, Date date) throws FedoraFailedException {
        List<FedoraRelation> collectionRelations;
        try {
            collectionRelations = fedoraRest.getNamedRelations(pid, COLLECTION_RELATION,
                                                                                       date.getTime());
        } catch (BackendInvalidCredsException | BackendMethodFailedException e) {
            throw new FedoraFailedException("Failed to get collection info from Fedora for pid " + pid, e);
        } catch (BackendInvalidResourceException e) {
            return new HashSet<>();
        }
        return getObject(collectionRelations);
    }

    public Set<String> getObjectsOfThisContentModel(String contentModelPid) throws FedoraFailedException {

        List<FedoraRelation> hasModelRelations;
        try {
            hasModelRelations = tripleStoreRest.getInverseRelations(contentModelPid, HASMODEL_RELATION);
        } catch (BackendInvalidCredsException | BackendMethodFailedException | BackendInvalidResourceException e) {
            throw new FedoraFailedException("Failed to get content model info from Fedora for pid " + contentModelPid, e);
        }
        return getSubject(hasModelRelations);
    }

    /**
     * Check if this object is a content model at the given date
     * @param pid the pid of the object
     * @param date check the object as it was as this timestamp
     * @return true if the object is a content model at the given date
     * @throws FedoraFailedException
     */
    public boolean isCurrentlyContentModel(String pid, Date date) throws FedoraFailedException {
        if (entryAngleCache.isCachedContentModel(pid)){
            return true;
        }
        try {
            ObjectProfile profile = getObjectProfile(pid, date);
            return profile.getType() == ObjectType.CONTENT_MODEL;
        } catch (BackendInvalidCredsException | BackendInvalidResourceException | BackendMethodFailedException e) {
            throw new FedoraFailedException("Failed to get profile of object '"+pid+"' at date '"+date.toString()+"'",e);
        }
    }

    private ObjectProfile getObjectProfile(String pid, Date date) throws
                                                                  BackendInvalidResourceException,
                                                                  BackendMethodFailedException,
                                                                  BackendInvalidCredsException {
        final Pair<String, Date> key = new Pair<>(pid, date);
        ObjectProfile cachedProfile = profileCache.get(key);
        if (cachedProfile == null){
            cachedProfile = fedoraRest.getLimitedObjectProfile(pid, date.getTime());
            profileCache.put(key,cachedProfile);
        }
        return cachedProfile;
    }

    public void invalidateContentModel(String pid) {
        entryAngleCache.invalidateContentModel(pid);
    }

    public Record.State getState(String pid, Date date) throws FedoraFailedException {
        try {
            ObjectProfile profile = getObjectProfile(pid, date);
            return Record.State.fromName(profile.getState());
        } catch (BackendInvalidCredsException | BackendInvalidResourceException | BackendMethodFailedException e) {
            throw new FedoraFailedException("Failed to get profile of object '" + pid + "' at date '" +
                                            date.toString() + "'", e);
        }
    }
}

