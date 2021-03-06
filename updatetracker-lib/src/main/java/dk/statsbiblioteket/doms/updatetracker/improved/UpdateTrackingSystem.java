package dk.statsbiblioteket.doms.updatetracker.improved;

import dk.statsbiblioteket.doms.central.connectors.fedora.Fedora;
import dk.statsbiblioteket.doms.central.connectors.fedora.FedoraRest;
import dk.statsbiblioteket.doms.central.connectors.fedora.tripleStore.TripleStoreRest;
import dk.statsbiblioteket.doms.central.connectors.fedora.views.ViewsImpl;
import dk.statsbiblioteket.doms.updatetracker.improved.database.UpdateTrackerBackend;
import dk.statsbiblioteket.doms.updatetracker.improved.database.UpdateTrackerPersistentStore;
import dk.statsbiblioteket.doms.updatetracker.improved.database.UpdateTrackerPersistentStoreImpl;
import dk.statsbiblioteket.doms.updatetracker.improved.database.dao.DBFactory;
import dk.statsbiblioteket.doms.updatetracker.improved.fedora.EntryAngleCache;
import dk.statsbiblioteket.doms.updatetracker.improved.fedora.FedoraForUpdateTracker;
import dk.statsbiblioteket.doms.updatetracker.improved.worklog.WorkLogPollDAO;
import dk.statsbiblioteket.doms.updatetracker.improved.worklog.WorkLogPollTask;
import dk.statsbiblioteket.sbutil.webservices.authentication.Credentials;

import java.io.Closeable;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * This is the system that starts the persistent store and the jms listener and ties them together
 */
public class UpdateTrackingSystem implements Closeable {


    private  UpdateTrackerPersistentStore store;
    private WorkLogPollDAO workLogPollDAO;
    private  Timer timer;


    public UpdateTrackingSystem(UpdateTrackingConfig updateTrackingConfig) {


        try {
            Credentials creds = new Credentials(updateTrackingConfig.getFedoraWebUsername(),
                                                updateTrackingConfig.getFedoraWebPassword());
            EntryAngleCache cmCache = new EntryAngleCache();
            Fedora fedoraRest = new FedoraRest(creds, updateTrackingConfig.getFedoraWebUrl());
            TripleStoreRest tripleStoreRest = new TripleStoreRest(creds,
                                                                  updateTrackingConfig.getFedoraWebUrl(), fedoraRest);
            ViewsImpl views = new ViewsImpl(tripleStoreRest, fedoraRest);


            //This thread pool is the number of records we can recalculate simultaneously when an common object (part of all their bundles) change
            final ExecutorService viewBundleRecalcThreadPool = initialiseThreadPool(updateTrackingConfig.getViewBundleMaxThreads());
            //Start up the fedora connection
            FedoraForUpdateTracker fedora = new FedoraForUpdateTracker(cmCache, fedoraRest, views);

            //Start up the database
            final UpdateTrackerBackend updateTrackerBackend = new UpdateTrackerBackend(fedora, updateTrackingConfig.getViewBundleCacheTime(), viewBundleRecalcThreadPool);

            final DBFactory dbfac = new DBFactory(updateTrackingConfig
                                                          .getUpdatetrackerHibernateConfig(),
                                                  updateTrackingConfig
                                                          .getUpdatetrackerHibernateMappings());
            store = new UpdateTrackerPersistentStoreImpl(fedora,
                                                         updateTrackerBackend,
                                                         dbfac);


            //initialise the connection to the work log
            workLogPollDAO = new WorkLogPollDAO(updateTrackingConfig.getFedoraDatabaseDriver(), updateTrackingConfig.getFedoraDatabaseURL(),
                                                updateTrackingConfig.getFedoraDatabaseUsername(),
                                                updateTrackingConfig.getFedoraDatabasePassword());

            startWorkLogTimerTask(updateTrackingConfig);
        } catch (Exception e){
            close();
            throw new RuntimeException(e);
        }
    }

    private ExecutorService initialiseThreadPool(Integer viewBundleThreadCount) {

        final ThreadFactory threadFactory = new ThreadFactory() {
            private final ThreadFactory fac = Executors.defaultThreadFactory();
            @Override //Hack to make the threads daemon threads so they do not block shutdown
            public Thread newThread(Runnable r) {
                Thread thread = fac.newThread(r);
                thread.setDaemon(true);
                return thread;
            }
        };
        //If thread count not correctly specified, make a cached thread pool (creates up to infinity threads as required, and kills them after 60 seconds of idle)
        if (viewBundleThreadCount == null || viewBundleThreadCount <= 0) {
            return Executors.newCachedThreadPool(threadFactory);
        } else {
            return Executors.newFixedThreadPool(viewBundleThreadCount, threadFactory);
        }
    }


    private void startWorkLogTimerTask(UpdateTrackingConfig updateTrackingConfig) {
        final boolean isDaemon = false;
        timer = new Timer("UpdateTracker-worklog-poller",isDaemon);
        //The timer thread is NOT a daemon, so it should prevent shutdown until the timer task is completed.
        //Tie it all together
        final int delay = updateTrackingConfig.getFedoraUpdatetrackerDelay();
        final int period = updateTrackingConfig.getFedoraUpdatetrackerPeriod();
        final int limit = updateTrackingConfig.getFedoraUpdatetrackerLimit();
        timer.schedule(new WorkLogPollTask(workLogPollDAO, store, limit, delay), delay, period);
    }

    @Override
    public void close()  {
        if (timer != null) {
            timer.cancel();
        }
        if (workLogPollDAO != null) {
            workLogPollDAO.close();
        }
        if (store != null) {
            store.close();
        }
    }

    public UpdateTrackerPersistentStore getStore() {
        return store;
    }
}
