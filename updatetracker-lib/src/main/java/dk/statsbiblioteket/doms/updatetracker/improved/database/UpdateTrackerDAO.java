package dk.statsbiblioteket.doms.updatetracker.improved.database;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.eq;
import static org.hibernate.criterion.Restrictions.in;
import static org.hibernate.criterion.Restrictions.not;

public class UpdateTrackerDAO {
    @SuppressWarnings("unchecked")
    private static <T> List<T> listRecords(Criteria criteria) {
        return criteria.list();
    }


    @SafeVarargs
    static <T> Set<T> asSet(T... vars){
        return new HashSet<>(Arrays.asList(vars));
    }

    static boolean recordNotExists(Session session, Record newRecord) {
        return session.get(Record.class, newRecord) == null;
    }

    static List<Record> getAllRecordsWithThisEntryPid(String pid, Session session) {
        return listRecords(session.createCriteria(Record.class)
                                  .add(eq("entryPid", pid)));
    }

    static List<Record> getRecordsNotInTheseCollectionsAndViewAngles(String entryPid, Session session,
                                                                     Collection<String> entryForViewAngles,
                                                                     Collection<String> collections) {
        final Criteria criteria = session.createCriteria(Record.class).add(eq("entryPid", entryPid));

        if (!collections.isEmpty() && !entryForViewAngles.isEmpty()){
            criteria.add(not(and(in("collection", collections), in("viewAngle", entryForViewAngles))));
        } else if (!collections.isEmpty()){
            criteria.add(not(and(in("collection", collections))));
        } else if (!entryForViewAngles.isEmpty()){
            criteria.add(not(and(in("viewAngle", entryForViewAngles))));
        }
        return listRecords(criteria);
    }

    static DomsObject loadOrCreate(Session session, String pid) {
        Object persistent = session.get(DomsObject.class, pid);
        if (persistent == null){
            session.save(new DomsObject(pid));
        }
        return (DomsObject) session.load(DomsObject.class, pid);
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> listRecords(Query query) {
        return query.list();
    }
}