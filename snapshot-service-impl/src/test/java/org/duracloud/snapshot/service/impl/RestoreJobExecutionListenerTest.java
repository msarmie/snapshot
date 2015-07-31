/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.duracloud.client.task.SnapshotTaskClient;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

/**
 * @author Bill Branan
 *         Date: 2/18/14
 */
public class RestoreJobExecutionListenerTest extends SnapshotTestBase {

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private ExecutionListenerConfig executionConfig;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private RestoreRepo restoreRepo;

    @Mock
    private SnapshotTaskClientHelper snapshotTaskClientHelper;

    @Mock
    private SnapshotTaskClient snapshotTaskClient;

    @Mock
    private BridgeConfiguration bridgeConfig;

    @Mock
    private Snapshot snapshot;

    @Mock 
    private Restoration restoration;
    
    @TestSubject
    private  RestoreJobExecutionListener executionListener = new  RestoreJobExecutionListener();

    private String restorationId = "restorationId";
    private String snapshotName = "snapshot-name";
    private String contentDir = "content-dir";
    private int daysToExpire = 42;
    private JobParameters jobParams;
    
    @Before
    public void setup() {
        super.setup();
        
        Map<String, JobParameter> jobParamMap = new HashMap<>();
        jobParamMap.put(SnapshotServiceConstants.SPRING_BATCH_UNIQUE_ID,
                        new JobParameter(restorationId));
        jobParams = new JobParameters(jobParamMap);
    }

    @Test
    public void testAfterJobSuccess() throws Exception {
        setupCommon();

        String duracloudEmail = "duracloud-email";
        String userEmail = "user-email";
        String spaceId = "space-id";
        String dcUsername = "dc-username";
        String dcPassword = "dc-password";
        DuracloudEndPointConfig endPointConfig = new DuracloudEndPointConfig();
        endPointConfig.setSpaceId(spaceId);

        expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);

        Capture<String> messageCapture = new Capture<>();
        notificationManager.sendNotification(
            EasyMock.eq(NotificationType.EMAIL),
            EasyMock.<String>anyObject(),
            EasyMock.capture(messageCapture),
            EasyMock.eq(duracloudEmail),
            EasyMock.eq(userEmail));
        expectLastCall();

        restoration.setStatus(org.duracloud.snapshot.dto.RestoreStatus.RESTORATION_COMPLETE);
        expectLastCall();

        restoration.setEndDate(EasyMock.isA(Date.class));
        expectLastCall();
        expect(restoration.getDestination()).andReturn(endPointConfig);
        expect(executionConfig.getDuracloudEmailAddresses()).andReturn(new String[]{duracloudEmail});
        expect(restoration.getUserEmail()).andReturn(userEmail);

        restoration.setExpirationDate(EasyMock.isA(Date.class));
        expectLastCall();

        EasyMock.expect(snapshotTaskClientHelper
                            .create(EasyMock.isA(DuracloudEndPointConfig.class),
                                    EasyMock.<String>anyObject(),
                                    EasyMock.<String>anyObject()))
                .andReturn(snapshotTaskClient);
        EasyMock.expect(snapshotTaskClient.completeRestore(spaceId, daysToExpire))
                .andReturn(null);

        EasyMock.expect(bridgeConfig.getDuracloudUsername())
                .andReturn(dcUsername);
        EasyMock.expect(bridgeConfig.getDuracloudPassword())
                .andReturn(dcPassword);

        replayAll();

        executionListener.afterJob(jobExecution);
        String message = messageCapture.getValue();
        assertTrue(message.contains(snapshotName));
        assertTrue(message.contains(String.valueOf(restorationId)));
        assertTrue(message.contains("EXPIRE"));
        assertTrue(message.contains(String.valueOf(daysToExpire)));
    }

    private void setupCommon() {
        executionListener.init(executionConfig, daysToExpire);
        expect(jobExecution.getJobParameters())
                .andReturn(jobParams);
        expect(executionConfig.getContentRoot()).andReturn(new File(contentDir));
        expect(restoration.getRestorationId()).andReturn(restorationId).atLeastOnce();
        restoration.setStatusText(isA(String.class));
        expectLastCall();
        
        expect(restoreRepo.findByRestorationId(restorationId)).andReturn(restoration);
        expect(restoration.getSnapshot()).andReturn(snapshot);
        expect(snapshot.getName()).andReturn(snapshotName);
        expect(restoreRepo.save(restoration)).andReturn(restoration);
    }

    @Test
    public void testAfterJobFailure() {
         setupCommon();

        String duracloudEmail = "duracloud-email";

        expect(jobExecution.getStatus())
                .andReturn(BatchStatus.FAILED);

        Capture<String> messageCapture = new Capture<>();
        notificationManager.sendNotification(
            EasyMock.eq(NotificationType.EMAIL),
            EasyMock.<String>anyObject(),
            EasyMock.capture(messageCapture),
            EasyMock.eq(duracloudEmail));
        expectLastCall();

        expect(executionConfig.getDuracloudEmailAddresses())
                .andReturn(new String[]{duracloudEmail});

        restoration.setStatus(RestoreStatus.ERROR);
        expectLastCall();
        replayAll();

        executionListener.afterJob(jobExecution);
        String message = messageCapture.getValue();
        assertTrue(message.contains(snapshotName));
        assertTrue(message.contains(contentDir));
        assertTrue(message.contains("failed"));
    }

    @Test
    public void testGetExpirationDate() {
        Date currentDate = new Date();
        Date expirationDate =
            executionListener.getExpirationDate(currentDate, daysToExpire);
        assertNotNull(expirationDate);
        long millisecondsInADay = 86400000l;
        assertEquals(currentDate.getTime() + (daysToExpire * millisecondsInADay),
                     expirationDate.getTime());

        replayAll();
    }

}
