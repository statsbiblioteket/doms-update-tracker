package dk.statsbiblioteket.doms.updatetracker.webservice;

import dk.statsbiblioteket.doms.updatetracker.UpdateTrackerWebserviceLib;

import javax.annotation.Resource;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.WebServiceContext;
import java.util.List;
import java.lang.String;

/**
 * Update tracker webservice. Provides upper layers of DOMS with info on changes
 * to objects in Fedora. Used by DOMS Server aka. Central to provide Summa with
 * said info.
 */
@WebService(endpointInterface
        = "dk.statsbiblioteket.doms.updatetracker.webservice"
        + ".UpdateTrackerWebservice")
public class UpdateTrackerWebserviceImpl implements UpdateTrackerWebservice {

    @Resource
    WebServiceContext context;
    UpdateTrackerWebservice updateTrackerWebservice;

    public UpdateTrackerWebserviceImpl() throws MethodFailedException {
        this.updateTrackerWebservice = new UpdateTrackerWebserviceLib();
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
     * @return returns java.util.List<dk.statsbiblioteket.doms.updatetracker
     *         .webservice.PidDatePidPid>
     * @throws MethodFailedException
     * @throws InvalidCredentialsException
     */
    public List<PidDatePidPid> listObjectsChangedSince(
            @WebParam(name = "collectionPid", targetNamespace = "")
            String collectionPid,
            @WebParam(name = "viewAngle", targetNamespace = "")
            String viewAngle,
            @WebParam(name = "beginTime", targetNamespace = "")
            long beginTime,
            @WebParam(name = "state", targetNamespace = "")
            String state,
            @WebParam(name = "offset", targetNamespace = "") Integer offset,
            @WebParam(name = "limit", targetNamespace = "") Integer limit)


            throws InvalidCredentialsException, MethodFailedException

    {
        return updateTrackerWebservice.listObjectsChangedSince(collectionPid, viewAngle, beginTime, state, offset,
                                                               limit);
    }

    /**
     * Return the last time a view/record conforming to the content model of the
     * given content model entry, and in the given collection, has been changed.
     *
     * @param collectionPid The PID of the collection in which we are looking
     *                      for the last change.
     * @param viewAngle     ...TODO doc
     * @return The date/time of the last change.
     * @throws InvalidCredentialsException
     * @throws MethodFailedException
     */
    public long getLatestModificationTime(
            @WebParam(name = "collectionPid", targetNamespace = "")
            java.lang.String collectionPid,
            @WebParam(name = "viewAngle", targetNamespace = "")
            java.lang.String viewAngle,
            @WebParam(name = "state", targetNamespace = "")
            java.lang.String state)
            throws InvalidCredentialsException, MethodFailedException
    {
        return updateTrackerWebservice.getLatestModificationTime(collectionPid, viewAngle, state);
    }


}
